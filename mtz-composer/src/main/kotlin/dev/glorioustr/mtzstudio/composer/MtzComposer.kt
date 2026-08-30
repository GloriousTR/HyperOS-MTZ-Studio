package dev.glorioustr.mtzstudio.composer

import dev.glorioustr.mtzstudio.core.Hashing
import dev.glorioustr.mtzstudio.core.MtzMetadata
import dev.glorioustr.mtzstudio.core.MtzParser
import dev.glorioustr.mtzstudio.core.ThemeVisualPolicy
import dev.glorioustr.mtzstudio.core.ComponentCategory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MtzComposer(private val parser: MtzParser = MtzParser()) {
    fun compose(request: CompositionRequest, output: Path): CompositionResult {
        validate(request)
        val parent = output.toAbsolutePath().parent ?: throw CompositionException("Output must have a parent directory")
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val selectedEntries = resolveEntries(request)
            writePackage(temporary, request, selectedEntries)
            parser.parse(temporary) // Fail before publishing a structurally invalid package.
            moveAtomically(temporary, output)
            val verified = parser.parse(output)
            return CompositionResult(
                output = output,
                outputSha256 = verified.sha256,
                verifiedArchive = verified,
                provenance = buildList {
                    request.baseSource?.let { base ->
                        base.archive.components.forEach { component ->
                            add(
                                ProvenanceEntry(
                                    sourceThemeId = base.themeId,
                                    sourceDisplayName = base.displayName,
                                    sourceSha256 = base.archive.sha256,
                                    category = component.category,
                                    rootPath = component.rootPath,
                                ),
                            )
                        }
                    }
                    request.selections.forEach { selection ->
                        add(
                            ProvenanceEntry(
                                sourceThemeId = selection.source.themeId,
                                sourceDisplayName = selection.source.displayName,
                                sourceSha256 = selection.source.archive.sha256,
                                category = selection.category,
                                rootPath = selection.rootPath,
                                useDefault = selection.useDefault,
                            ),
                        )
                    }
                },
            )
        } catch (error: CompositionException) {
            throw error
        } catch (error: Exception) {
            throw CompositionException("Could not compose and verify MTZ", error)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun composeFont(request: FontExportRequest, output: Path): CompositionResult {
        if (request.metadata.name.isBlank()) throw CompositionException("Output font name is required")
        if (!Files.isRegularFile(request.fontFile)) throw CompositionException("Font source is not a regular file")
        val sourceBytes = Files.size(request.fontFile)
        if (sourceBytes !in 1..MAX_FONT_BYTES) throw CompositionException("Font source size is not supported")
        val safeFileName = request.archiveFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .take(96)
            .ifBlank { "Roboto-Regular.ttf" }
        if (!safeFileName.endsWith(".ttf", true) && !safeFileName.endsWith(".otf", true)) {
            throw CompositionException("Font source must be a TTF or OTF file")
        }
        request.previewFile?.let { preview ->
            if (!Files.isRegularFile(preview) || Files.size(preview) !in 1..MAX_PREVIEW_BYTES) {
                throw CompositionException("Font preview is missing or too large")
            }
        }

        val parent = output.toAbsolutePath().parent ?: throw CompositionException("Output must have a parent directory")
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                zip.setLevel(9)
                zip.putNextEntry(deterministicEntry("description.xml"))
                zip.write(fontDescriptionXml(request.metadata).encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(deterministicEntry("fonts/$safeFileName"))
                Files.newInputStream(request.fontFile).use { it.copyTo(zip) }
                zip.closeEntry()
                request.previewFile?.let { preview ->
                    val extension = preview.fileName.toString().substringAfterLast('.', "png")
                        .lowercase()
                        .takeIf { it == "png" || it == "jpg" || it == "jpeg" || it == "webp" }
                        ?: "png"
                    zip.putNextEntry(deterministicEntry("preview/preview_fonts_0.$extension"))
                    Files.newInputStream(preview).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            parser.parse(temporary)
            moveAtomically(temporary, output)
            val verified = parser.parse(output)
            if (verified.components.none { it.category == dev.glorioustr.mtzstudio.core.ComponentCategory.FONT }) {
                throw CompositionException("Generated MTZ does not contain a recognized font component")
            }
            return CompositionResult(
                output = output,
                outputSha256 = verified.sha256,
                verifiedArchive = verified,
                provenance = emptyList(),
            )
        } catch (error: CompositionException) {
            throw error
        } catch (error: Exception) {
            throw CompositionException("Could not package and verify the active font", error)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validate(request: CompositionRequest) {
        if (request.metadata.name.isBlank()) throw CompositionException("Output theme name is required")
        request.generatedPreviewBytes?.let { preview ->
            if (preview.size.toLong() !in 1..MAX_PREVIEW_BYTES) {
                throw CompositionException("Generated theme preview is empty or too large")
            }
        }
        if (request.baseSource == null && request.selections.none { !it.useDefault } &&
            request.customHomeWallpaperBytes == null && request.customLockWallpaperBytes == null
        ) {
            throw CompositionException("Select at least one component or custom wallpaper")
        }
        val duplicateCategories = request.selections.groupingBy { it.category }.eachCount().filterValues { it > 1 }.keys
        if (duplicateCategories.isNotEmpty()) throw CompositionException(
            "Only one source per component category is allowed: ${duplicateCategories.joinToString()}",
        )
    }

    private fun resolveEntries(request: CompositionRequest): List<SelectedEntry> {
        val result = linkedMapOf<String, SelectedEntry>()
        val overriddenCategories = request.selections.mapTo(hashSetOf()) { it.category }
        if (request.selections.any { it.useDefault && it.category == ComponentCategory.SYSTEM_UI }) {
            overriddenCategories += ComponentCategory.SYSTEM_UI_PLUGIN
        }
        request.baseSource?.let { base ->
            val archive = base.archive
            if (Hashing.sha256(archive.source) != archive.sha256) {
                throw CompositionException("Base theme changed after import: ${base.displayName}")
            }
            val overriddenBaseEntries = archive.components.asSequence()
                .filter { it.category in overriddenCategories }
                .flatMap { it.entryPaths.asSequence() }
                .mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
            overriddenCategories.flatMap { ThemeVisualPolicy.categoryPreviewPaths(archive.entries, it) }
                .forEach { overriddenBaseEntries += it.lowercase(Locale.ROOT) }
            archive.entries.asSequence()
                .filterNot { it.directory || it.rightsRelated }
                .filterNot { it.path.equals("description.xml", ignoreCase = true) }
                .filterNot { it.path.lowercase(Locale.ROOT) in overriddenBaseEntries }
                .forEach { entry ->
                    result[entry.path.lowercase(Locale.ROOT)] = SelectedEntry(archive.source, entry.path)
                }
        }
        request.selections.forEach { selection ->
            val archive = selection.source.archive
            if (Hashing.sha256(archive.source) != archive.sha256) {
                throw CompositionException("Source changed after import: ${selection.source.displayName}")
            }
            if (selection.useDefault && !ThemeVisualPolicy.isPreviewOnly(archive.components, archive.entries, selection.category)) {
                throw CompositionException("Default selection requires a preview and no component: ${selection.category}")
            }
            val component = if (selection.useDefault) null else (archive.components.singleOrNull {
                it.category == selection.category && it.rootPath == selection.rootPath
            } ?: throw CompositionException("Selected component no longer exists: ${selection.rootPath}"))
            val allowed = archive.entries.associateBy { it.path }
            component?.entryPaths.orEmpty().forEach { path ->
                val entry = allowed[path] ?: throw CompositionException("Missing selected entry: $path")
                if (entry.rightsRelated) throw CompositionException("Rights entries cannot be composed: $path")
                val identity = path.lowercase(Locale.ROOT)
                result[identity] = SelectedEntry(archive.source, path)
            }
            ThemeVisualPolicy.categoryPreviewPaths(archive.entries, selection.category)
                .forEach { path ->
                    val entry = allowed.getValue(path)
                    val identity = path.lowercase(Locale.ROOT)
                    if (!entry.rightsRelated) {
                        result[identity] = SelectedEntry(archive.source, path)
                    }
                }
        }
        return result.values.sortedBy { it.entryPath.lowercase(Locale.ROOT) }
    }

    private fun writePackage(target: Path, request: CompositionRequest, entries: List<SelectedEntry>) {
        val writtenEntries = hashSetOf<String>()
        ZipOutputStream(Files.newOutputStream(target)).use { output ->
            output.setLevel(9)
            output.putNextEntry(deterministicEntry("description.xml"))
            val compatibilityMetadata = request.baseSource?.archive?.metadata
                ?: request.selections.asSequence().mapNotNull { it.source.archive.metadata }.firstOrNull()
            val defaultNames = ThemeVisualPolicy.personalizationCategories.mapNotNull { category ->
                val selected = request.selections.firstOrNull { it.category == category }
                val name = when {
                    selected?.useDefault == true -> ThemeVisualPolicy.defaultSourceName(selected.source.archive, category)
                        ?: selected.source.archive.metadata?.name ?: selected.source.displayName
                    selected != null -> null
                    else -> request.baseSource?.archive?.let { ThemeVisualPolicy.defaultSourceName(it, category) }
                }
                name?.let { category to it }
            }.toMap()
            output.write(descriptionXml(request.metadata, compatibilityMetadata, defaultNames).encodeToByteArray())
            output.closeEntry()
            writtenEntries += "description.xml"

            var currentSource: Path? = null
            var currentZip: ZipFile? = null
            try {
                entries.forEach { selected ->
                    if (selected.source != currentSource) {
                        currentZip?.close()
                        currentSource = selected.source
                        currentZip = ZipFile(selected.source.toFile())
                    }
                    val inputEntry = currentZip!!.getEntry(selected.entryPath)
                        ?: throw CompositionException("Source ZIP entry disappeared: ${selected.entryPath}")

                    val lowerPath = selected.entryPath.lowercase(Locale.ROOT)
                    output.putNextEntry(deterministicEntry(selected.entryPath))
                    when {
                        lowerPath == "wallpaper/default_wallpaper.jpg" && request.customHomeWallpaperBytes != null -> {
                            output.write(request.customHomeWallpaperBytes)
                        }
                        lowerPath == "wallpaper/default_lock_wallpaper.jpg" && request.customLockWallpaperBytes != null -> {
                            output.write(request.customLockWallpaperBytes)
                        }
                        lowerPath == "preview/preview_launcher_0.jpg" && request.generatedPreviewBytes != null -> {
                            output.write(request.generatedPreviewBytes)
                        }
                        lowerPath == "lockscreen" && request.customLockWallpaperBytes != null -> {
                            currentZip!!.getInputStream(inputEntry).use { inStream ->
                                val repackaged = repackageLockscreen(inStream, request.customLockWallpaperBytes)
                                output.write(repackaged)
                            }
                        }
                        else -> {
                            currentZip!!.getInputStream(inputEntry).use { it.copyTo(output) }
                        }
                    }
                    output.closeEntry()
                    writtenEntries += lowerPath
                }
            } finally {
                currentZip?.close()
            }

            // Append custom home wallpaper if not written yet
            if (request.customHomeWallpaperBytes != null && !writtenEntries.contains("wallpaper/default_wallpaper.jpg")) {
                output.putNextEntry(deterministicEntry("wallpaper/default_wallpaper.jpg"))
                output.write(request.customHomeWallpaperBytes)
                output.closeEntry()
                writtenEntries += "wallpaper/default_wallpaper.jpg"
            }

            // Append custom lock wallpaper if not written yet
            if (request.customLockWallpaperBytes != null) {
                if (!writtenEntries.contains("wallpaper/default_lock_wallpaper.jpg")) {
                    output.putNextEntry(deterministicEntry("wallpaper/default_lock_wallpaper.jpg"))
                    output.write(request.customLockWallpaperBytes)
                    output.closeEntry()
                    writtenEntries += "wallpaper/default_lock_wallpaper.jpg"
                }
                if (!writtenEntries.contains("lockscreen")) {
                    output.putNextEntry(deterministicEntry("lockscreen"))
                    output.write(createBasicLockscreenPackage(request.customLockWallpaperBytes))
                    output.closeEntry()
                    writtenEntries += "lockscreen"
                }
            }

            if (request.generatedPreviewBytes != null && !writtenEntries.contains("preview/preview_launcher_0.jpg")) {
                output.putNextEntry(deterministicEntry("preview/preview_launcher_0.jpg"))
                output.write(request.generatedPreviewBytes)
                output.closeEntry()
                writtenEntries += "preview/preview_launcher_0.jpg"
            }
            if (request.generatedPreviewBytes != null && !writtenEntries.contains("preview/mtz_studio_generated.jpg")) {
                output.putNextEntry(deterministicEntry("preview/mtz_studio_generated.jpg"))
                output.write(request.generatedPreviewBytes)
                output.closeEntry()
                writtenEntries += "preview/mtz_studio_generated.jpg"
            }
        }
    }

    private fun repackageLockscreen(inputStream: InputStream, customLockWallpaperBytes: ByteArray): ByteArray {
        val tempOut = ByteArrayOutputStream()
        var replaced = false
        ZipInputStream(inputStream).use { zipIn ->
            ZipOutputStream(tempOut).use { zipOut ->
                zipOut.setLevel(9)
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val isImage = name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true)
                    val isWallpaper = isImage && (
                        name.contains("wallpaper", ignoreCase = true) ||
                        name.contains("default_lock", ignoreCase = true) ||
                        name.endsWith("default_wallpaper.jpg", ignoreCase = true)
                    )
                    if (isWallpaper) {
                        zipOut.putNextEntry(deterministicEntry(name))
                        zipOut.write(customLockWallpaperBytes)
                        zipOut.closeEntry()
                        replaced = true
                    } else {
                        zipOut.putNextEntry(deterministicEntry(name))
                        zipIn.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                    entry = zipIn.nextEntry
                }
                if (!replaced) {
                    zipOut.putNextEntry(deterministicEntry("advance/wallpaper/default_wallpaper.jpg"))
                    zipOut.write(customLockWallpaperBytes)
                    zipOut.closeEntry()
                }
            }
        }
        return tempOut.toByteArray()
    }

    private fun createBasicLockscreenPackage(customLockWallpaperBytes: ByteArray): ByteArray {
        val tempOut = ByteArrayOutputStream()
        ZipOutputStream(tempOut).use { zipOut ->
            zipOut.setLevel(9)
            zipOut.putNextEntry(deterministicEntry("advance/wallpaper/default_wallpaper.jpg"))
            zipOut.write(customLockWallpaperBytes)
            zipOut.closeEntry()
        }
        return tempOut.toByteArray()
    }

    private fun deterministicEntry(path: String) = ZipEntry(path).apply {
        time = 0L
        comment = null
        extra = null
    }

    private fun descriptionXml(metadata: CompositionMetadata, compatibility: MtzMetadata?, defaultNames: Map<ComponentCategory, String>): String = buildString {
        val author = metadata.author ?: compatibility?.author
        val designer = metadata.designer ?: compatibility?.designer
        val uiVersion = compatibility?.fields?.get("uiversion")?.takeIf(::isSafeVersionValue)
        val adapterVersion = compatibility?.fields?.get("miuiadapterversion")?.takeIf(::isSafeVersionValue)
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<theme>\n")
        append("  <title>").append(xml(metadata.name)).append("</title>\n")
        append("  <version>").append(xml(metadata.version)).append("</version>\n")
        uiVersion?.let { append("  <uiVersion>").append(xml(it)).append("</uiVersion>\n") }
        author?.takeIf(String::isNotBlank)?.let { append("  <author>").append(xml(it)).append("</author>\n") }
        designer?.takeIf(String::isNotBlank)?.let { append("  <designer>").append(xml(it)).append("</designer>\n") }
        append("  <description>").append(xml(metadata.description)).append("</description>\n")
        adapterVersion?.let {
            append("  <miuiAdapterVersion>").append(xml(it)).append("</miuiAdapterVersion>\n")
        }
        defaultNames.forEach { (category, name) ->
            val tag = ThemeVisualPolicy.defaultSourceKey(category)
            append("  <").append(tag).append(">").append(xml(name)).append("</").append(tag).append(">\n")
        }
        append("</theme>\n")
    }

    private fun isSafeVersionValue(value: String): Boolean =
        value.length in 1..24 && value.all { it.isDigit() || it == '.' || it == '-' || it == '_' }

    private fun fontDescriptionXml(metadata: CompositionMetadata): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<theme>\n")
        append("  <title>").append(xml(metadata.name)).append("</title>\n")
        metadata.author?.takeIf(String::isNotBlank)?.let { append("  <author>").append(xml(it)).append("</author>\n") }
        metadata.designer?.takeIf(String::isNotBlank)?.let { append("  <designer>").append(xml(it)).append("</designer>\n") }
        append("  <version>").append(xml(metadata.version)).append("</version>\n")
        append("  <uiVersion>16</uiVersion>\n")
        append("  <fontWeight>100,150,200,250,300,350,400,450,500,550,600,650,700,800,900</fontWeight>\n")
        append("  <description>").append(xml(metadata.description)).append("</description>\n")
        append("</theme>\n")
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }


    private data class SelectedEntry(val source: Path, val entryPath: String)

    private companion object {
        const val MAX_FONT_BYTES = 64L * 1024 * 1024
        const val MAX_PREVIEW_BYTES = 16L * 1024 * 1024
    }
}

package dev.glorioustr.mtzstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Process
import dev.glorioustr.mtzstudio.composer.CompositionMetadata
import dev.glorioustr.mtzstudio.composer.FontExportRequest
import dev.glorioustr.mtzstudio.composer.MtzComposer
import dev.glorioustr.mtzstudio.core.MtzParser
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import dev.glorioustr.mtzstudio.tester.PrivilegedCommandRunner
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DeviceThemeSummary(
    val localId: String,
    val title: String,
    val author: String?,
    val version: String?,
    val previewPath: Path?,
    val isAlreadyImported: Boolean,
)

internal data class DeviceThemeImportResult(
    val theme: LibraryTheme,
    val addedToLibrary: Boolean,
)

internal data class DeviceThemeBulkImportResult(
    val found: Int,
    val added: Int,
    val duplicates: Int,
    val failed: Int,
    val errors: List<String>,
)

internal class DeviceThemeImporter(
    context: Context,
    private val library: ThemeLibrary,
    private val composer: MtzComposer,
    private val commandRunner: PrivilegedCommandRunner,
    private val parser: MtzParser = MtzParser(),
) {
    private val appContext = context.applicationContext
    private val stagingRoot = appContext.filesDir.toPath().resolve("device-import")
    private val importOrigins = appContext.getSharedPreferences("theme-manager-imports", Context.MODE_PRIVATE)

    /** Scans Theme Manager and returns list of available themes with metadata. */
    @Synchronized
    fun listAvailableDeviceThemes(): List<DeviceThemeSummary> {
        val records = readRecords()
        val previewDirectory = stagingRoot.resolve("scan-previews")
        Files.createDirectories(previewDirectory)
        copyThemePreviewFiles(records, previewDirectory)
        val knownThemes = library.load().themes
        return records.map { record ->
            val isAlreadyImported = findExisting(record, knownThemes) != null
            DeviceThemeSummary(
                localId = record.localId,
                title = record.title,
                author = record.author,
                version = record.version,
                previewPath = previewDirectory.resolve("${record.localId}.preview")
                    .takeIf { Files.isRegularFile(it) && Files.size(it) > 0L },
                isAlreadyImported = isAlreadyImported,
            )
        }
    }

    /** Reconstructs selected Theme Manager items by localId. */
    @Synchronized
    fun importSelectedThemes(selectedLocalIds: Set<String>): DeviceThemeBulkImportResult {
        val records = readRecords().filter { selectedLocalIds.isEmpty() || it.localId in selectedLocalIds }
        if (records.isEmpty()) return DeviceThemeBulkImportResult(0, 0, 0, 0, emptyList())
        return importRecords(records)
    }

    private fun readRecords(): List<ThemeManagerRecord> {
        resetDirectory(stagingRoot)
        val metadataDirectory = stagingRoot.resolve("metadata")
        Files.createDirectories(metadataDirectory)
        copyThemeMetadata(metadataDirectory)

        return Files.list(metadataDirectory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mrm") }
                .map(::parseThemeRecord)
                .sorted(compareBy(ThemeManagerRecord::title, ThemeManagerRecord::localId))
                .collect(java.util.stream.Collectors.toList())
        }
    }

    private fun importRecords(records: List<ThemeManagerRecord>): DeviceThemeBulkImportResult {
        val knownThemes = library.load().themes.toMutableList()
        var added = 0
        var duplicates = 0
        val errors = mutableListOf<String>()
        records.forEachIndexed { index, record ->
            val existing = findExisting(record, knownThemes)
            if (existing != null) {
                rememberOrigin(record, existing)
                duplicates++
                return@forEachIndexed
            }

            val itemDirectory = stagingRoot.resolve("theme-$index")
            val output = library.newExportPath(record.title)
            try {
                Files.createDirectories(itemDirectory)
                copyThemeFiles(record, itemDirectory)
                buildMtz(record, itemDirectory, output)
                val verified = parser.parse(output)
                if (verified.components.isEmpty()) error("No recognizable MTZ component was produced")
                val imported = Files.newInputStream(output).use { library.importTheme(it, record.title) }
                knownThemes += imported
                rememberOrigin(record, imported)
                added++
            } catch (error: Exception) {
                errors += "${record.title}: ${error.message ?: error::class.simpleName}"
            } finally {
                Files.deleteIfExists(output)
                itemDirectory.toFile().deleteRecursively()
            }
        }
        stagingRoot.toFile().deleteRecursively()
        return DeviceThemeBulkImportResult(records.size, added, duplicates, errors.size, errors)
    }

    /** Reconstructs every local Theme Manager item from its .mrm metadata and .mrc components. */
    fun importAllThemes(): DeviceThemeBulkImportResult {
        return importSelectedThemes(emptySet())
    }

    /** Keeps the private editor cache aligned with Theme Manager, which remains the source of truth. */
    @Synchronized
    fun synchronizeModernLibrary(): DeviceThemeBulkImportResult {
        // One metadata snapshot, not two scans that may disagree during a native operation.
        val records = readRecords()
        val availableIds = records.mapTo(mutableSetOf(), ThemeManagerRecord::localId)
        val result = importRecords(records)
        if (result.failed > 0) return result
        val staleOrigins = importOrigins.all.keys.filter { key ->
            key.startsWith(ORIGIN_PREFIX) && key.removePrefix(ORIGIN_PREFIX) !in availableIds
        }
        staleOrigins.forEach { key ->
            // A catalog refresh must never delete the user's private MTZ source.
            // Detach stale entries; explicit native deletion handles its own mirror cleanup.
            importOrigins.edit().remove(key).apply()
        }
        return result
    }

    fun localIdFor(theme: LibraryTheme): String? = importOrigins.all.entries.firstNotNullOfOrNull { (key, value) ->
        if (!key.startsWith(ORIGIN_PREFIX)) return@firstNotNullOfOrNull null
        val mappedThemeId = value?.toString()?.substringAfter('|', "").orEmpty()
        key.removePrefix(ORIGIN_PREFIX).takeIf { mappedThemeId == theme.id.value }
    }

    fun rememberThemeManagerOrigin(localId: String, theme: LibraryTheme) {
        val safeLocalId = localId.requireSafeIdentifier("theme local ID")
        importOrigins.edit().putString(originKey(safeLocalId), "${theme.archive.sha256}|${theme.id.value}").apply()
    }

    fun forgetThemeManagerOrigin(localId: String) {
        if (localId.matches(SAFE_IDENTIFIER)) importOrigins.edit().remove(originKey(localId)).apply()
    }

    fun importActiveFont(): DeviceThemeImportResult {
        Files.createDirectories(stagingRoot)
        val font = stagingRoot.resolve("active-font.ttf")
        val preview = stagingRoot.resolve("active-font-preview.png")
        Files.deleteIfExists(font)
        Files.deleteIfExists(preview)
        try {
            copyFirstAvailable(
                candidates = listOf(
                    "/data/system/theme/fonts/Roboto-Regular.ttf",
                    "/data/system/theme/fonts/Miui-Regular.ttf",
                    "/data/system/theme/fonts/MiuiEx-Regular.ttf",
                ),
                target = font,
                missingMessage = "No active Theme Manager font file was found",
            )
            val displayName = appContext.getString(R.string.device_active_font_name)
            createFontPreview(font, preview, displayName)
            val output = library.newExportPath(displayName)
            val composition = composer.composeFont(
                FontExportRequest(
                    metadata = CompositionMetadata(
                        name = displayName,
                        description = "Exported locally from the active HyperOS Theme Manager font",
                    ),
                    fontFile = font,
                    previewFile = preview,
                ),
                output,
            )
            val existing = library.load().themes.firstOrNull {
                it.archive.sha256 == composition.outputSha256
            }
            if (existing != null) {
                Files.deleteIfExists(output)
                return DeviceThemeImportResult(existing, false)
            }
            val imported = Files.newInputStream(output).use { library.importTheme(it, null) }
            Files.deleteIfExists(output)
            return DeviceThemeImportResult(imported, true)
        } finally {
            Files.deleteIfExists(font)
            Files.deleteIfExists(preview)
        }
    }

    private fun createFontPreview(font: Path, target: Path, displayName: String) {
        val typeface = Typeface.createFromFile(font.toFile())
        val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(24, 22, 30))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textAlign = Paint.Align.CENTER
            }
            paint.color = Color.rgb(205, 178, 255)
            paint.textSize = 280f
            canvas.drawText("Aa", 360f, 390f, paint)
            paint.color = Color.WHITE
            paint.textSize = 62f
            canvas.drawText(displayName, 360f, 555f, paint)
            paint.color = Color.rgb(205, 198, 214)
            paint.textSize = 42f
            canvas.drawText("HyperOS MTZ Studio", 360f, 660f, paint)
            canvas.drawText("ABCÇĞİÖŞÜ 1234567890", 360f, 755f, paint)
            Files.newOutputStream(target).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("Font preview could not be generated")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyThemeMetadata(target: Path) {
        val command = dev.glorioustr.mtzstudio.tester.ThemeCatalogCommands.copyMetadata(
            "$THEME_DATA_ROOT/meta/theme", target.toAbsolutePath().toString(), Process.myUid(),
        )
        val result = commandRunner.run(command, 120)
        LiveDiagnosticsRecorder.get(appContext).record(
            "catalog_metadata_snapshot", "Tema kayıtları okundu",
            mapOf("exitCode" to result.exitCode, "output" to result.output),
        )
        check(result.exitCode == 0) {
            appContext.getString(if (result.exitCode == 4) R.string.catalog_location_unavailable else R.string.catalog_read_failed)
        }
        check(Regex("MTZ_METADATA_COUNT=[0-9]+").containsMatchIn(result.output)) {
            appContext.getString(R.string.catalog_read_failed)
        }
    }

    private fun copyThemeFiles(record: ThemeManagerRecord, target: Path) {
        val parts = target.resolve("parts")
        val previews = target.resolve("preview")
        Files.createDirectories(parts)
        Files.createDirectories(previews)
        val command = buildString {
            record.resources.forEachIndexed { index, resource ->
                val source = "$THEME_DATA_ROOT/content/${resource.resourceCode}/${resource.localId}.mrc"
                val destination = parts.resolve("$index.mrc").toAbsolutePath().toString()
                append("cp ").append(shellQuote(source)).append(' ').append(shellQuote(destination))
                    .append(" || exit ").append(20 + index).append('\n')
            }
            append("preview_count=0\n")
            record.previewNames.forEach { name ->
                val source = "$THEME_DATA_ROOT/preview/theme/${record.localId}/$name"
                val destination = previews.resolve(name).toAbsolutePath().toString()
                append("if [ \"${'$'}preview_count\" -lt $MAX_PREVIEWS_PER_THEME ] && [ -f ")
                    .append(shellQuote(source)).append(" ]; then cp ")
                    .append(shellQuote(source)).append(' ').append(shellQuote(destination))
                    .append(" && preview_count=${'$'}((preview_count + 1)); fi\n")
            }
            append("chown -R ").append(Process.myUid()).append(':').append(Process.myUid()).append(' ')
                .append(shellQuote(target.toAbsolutePath().toString())).append(" || exit 90\n")
            append("chmod -R u+rwX,go-rwx ").append(shellQuote(target.toAbsolutePath().toString()))
                .append(" || exit 91")
        }
        runPrivileged(command, 300, "Theme components could not be copied")
        record.resources.indices.forEach { index ->
            val file = parts.resolve("$index.mrc")
            if (!Files.isRegularFile(file) || Files.size(file) <= 0L) {
                error("Theme component ${record.resources[index].resourceCode} is missing")
            }
        }
    }

    private fun copyThemePreviewFiles(records: List<ThemeManagerRecord>, target: Path) {
        if (records.isEmpty()) return
        val command = buildString {
            records.forEach { record ->
                val destination = target.resolve("${record.localId}.preview").toAbsolutePath().toString()
                append("for candidate in ")
                record.previewNames.forEach { name ->
                    append(shellQuote("$THEME_DATA_ROOT/preview/theme/${record.localId}/$name")).append(' ')
                }
                append("; do if [ -f \"${'$'}candidate\" ]; then cp \"${'$'}candidate\" ")
                    .append(shellQuote(destination)).append("; break; fi; done\n")
            }
            append("chown -R ").append(Process.myUid()).append(':').append(Process.myUid()).append(' ')
                .append(shellQuote(target.toAbsolutePath().toString())).append(" || exit 90\n")
            append("chmod -R u+rwX,go-rwx ").append(shellQuote(target.toAbsolutePath().toString()))
                .append(" || exit 91")
        }
        runPrivileged(command, 120, "Theme previews could not be copied")
    }

    private fun buildMtz(record: ThemeManagerRecord, sourceDirectory: Path, output: Path) {
        val temporary = output.resolveSibling(".${output.fileName}.tmp")
        Files.deleteIfExists(temporary)
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                // MRC payloads are already compressed packages; level 0 keeps bulk import responsive.
                zip.setLevel(0)
                zip.putNextEntry(deterministicEntry("description.xml"))
                zip.write(descriptionXml(record).encodeToByteArray())
                zip.closeEntry()

                val written = hashSetOf("description.xml")
                record.resources.forEachIndexed { index, resource ->
                    val archivePath = archivePath(resource)
                    if (!written.add(archivePath.lowercase(Locale.ROOT))) {
                        error("Duplicate reconstructed MTZ path: $archivePath")
                    }
                    zip.putNextEntry(deterministicEntry(archivePath))
                    Files.newInputStream(sourceDirectory.resolve("parts/$index.mrc")).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                Files.list(sourceDirectory.resolve("preview")).use { previews ->
                    previews.filter(Files::isRegularFile).sorted().forEach { preview ->
                        val archivePath = "preview/${preview.fileName}"
                        if (written.add(archivePath.lowercase(Locale.ROOT))) {
                            zip.putNextEntry(deterministicEntry(archivePath))
                            Files.newInputStream(preview).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            parser.parse(temporary)
            Files.move(temporary, output)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun parseThemeRecord(path: Path): ThemeManagerRecord {
        val jsonText = Files.newBufferedReader(path).use { it.readText() }
        val json = JSONObject(jsonText)
        val localId = json.getString("localId").requireSafeIdentifier("theme local ID")
        val resourcesJson = json.optJSONArray("subResources") ?: JSONArray()
        val resources = buildList {
            for (index in 0 until resourcesJson.length()) {
                val item = resourcesJson.getJSONObject(index)
                add(
                    ThemeManagerResource(
                        localId = item.getString("localId").requireSafeIdentifier("component local ID"),
                        resourceCode = item.getString("resourceCode").requireSafeResourceCode(),
                    ),
                )
            }
        }
        if (resources.isEmpty()) error("Theme record $localId has no components")
        val previewNames = buildList {
            add("preview_lockscreen_0.jpg")
            add("preview_launcher_0.jpg")
            add("preview_icons_0.jpg")
            add("preview_miwallpaper_0.jpg")
            add("preview_statusbar_0.jpg")
            localizedArray(json.optJSONObject("builtInPreviews")).forEach { name ->
                if (name.matches(SAFE_PREVIEW_NAME)) add(name)
            }
        }.distinct().take(MAX_PREVIEW_CANDIDATES)
        return ThemeManagerRecord(
            localId = localId,
            hash = json.optString("hash").take(128),
            platform = json.optInt("platform", 17).coerceIn(1, 99),
            version = json.optString("version", "1.0").take(80),
            title = localizedString(json.optJSONObject("titles"))?.take(180) ?: "Theme $localId",
            author = localizedString(json.optJSONObject("authors"))?.take(180),
            designer = localizedString(json.optJSONObject("designers"))?.take(180),
            description = localizedString(json.optJSONObject("descriptions"))?.take(4_096),
            resources = resources,
            previewNames = previewNames,
        )
    }

    private fun findExisting(record: ThemeManagerRecord, themes: List<LibraryTheme>): LibraryTheme? {
        val stored = importOrigins.getString(originKey(record.localId), null)
        if (stored != null) {
            val pieces = stored.split('|', limit = 2)
            val storedHash = pieces.firstOrNull().orEmpty()
            val themeId = pieces.getOrNull(1).orEmpty()
            themes.firstOrNull { it.id.value == themeId && storedHash == record.hash }?.let { return it }
        }
        return themes.firstOrNull { theme ->
            val metadata = theme.archive.metadata
            metadata?.name.normalized() == record.title.normalized() &&
                metadata?.version.normalized() == record.version.normalized() &&
                metadata?.author.normalized() == record.author.normalized()
        }
    }

    private fun rememberOrigin(record: ThemeManagerRecord, theme: LibraryTheme) {
        importOrigins.edit().putString(originKey(record.localId), "${record.hash}|${theme.id.value}").apply()
    }

    private fun archivePath(resource: ThemeManagerResource): String = when (resource.resourceCode) {
        "contact" -> "com.android.contacts"
        "mms" -> "com.android.mms"
        "launcher" -> "com.miui.home"
        "statusbar" -> "com.android.systemui"
        "lockstyle" -> "lockscreen"
        "wallpaper" -> "wallpaper/default_wallpaper.jpg"
        "lockscreen" -> "wallpaper/default_lock_wallpaper.jpg"
        "fonts" -> "fonts/Roboto-Regular.ttf"
        "framework" -> "framework-res"
        "bootanimation" -> "boots/bootanimation.zip"
        else -> resource.resourceCode
    }

    private fun descriptionXml(record: ThemeManagerRecord): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<theme>\n")
        append("  <version>").append(xml(record.version)).append("</version>\n")
        append("  <uiVersion>").append(record.platform).append("</uiVersion>\n")
        record.author?.let { append("  <author>").append(xml(it)).append("</author>\n") }
        record.designer?.let { append("  <designer>").append(xml(it)).append("</designer>\n") }
        append("  <title>").append(xml(record.title)).append("</title>\n")
        record.description?.let { append("  <description>").append(xml(it)).append("</description>\n") }
        append("</theme>\n")
    }

    private fun localizedString(values: JSONObject?): String? {
        if (values == null) return null
        val locale = Locale.getDefault()
        val candidates = listOf(
            "${locale.language}_${locale.country}",
            locale.toLanguageTag().replace('-', '_'),
            locale.language,
            "en_US",
            "fallback",
            "zh_CN",
        )
        candidates.forEach { key -> values.optString(key).takeIf(String::isNotBlank)?.let { return it } }
        val keys = values.keys()
        while (keys.hasNext()) values.optString(keys.next()).takeIf(String::isNotBlank)?.let { return it }
        return null
    }

    private fun localizedArray(values: JSONObject?): List<String> {
        if (values == null) return emptyList()
        val array = listOf("fallback", "en_US", "zh_CN").firstNotNullOfOrNull(values::optJSONArray)
            ?: values.keys().asSequence().mapNotNull(values::optJSONArray).firstOrNull()
            ?: return emptyList()
        return buildList { for (index in 0 until array.length()) add(array.optString(index)) }
    }

    private fun copyFirstAvailable(candidates: List<String>, target: Path, missingMessage: String) {
        val sourceTests = candidates.joinToString(" ") { shellQuote(it) }
        val targetPath = shellQuote(target.toAbsolutePath().toString())
        val uid = Process.myUid()
        val command = """
            source_path=''
            for candidate in $sourceTests; do
              if [ -f "${'$'}candidate" ]; then source_path="${'$'}candidate"; break; fi
            done
            if [ -z "${'$'}source_path" ]; then echo ${shellQuote(missingMessage)}; exit 4; fi
            cp "${'$'}source_path" $targetPath || exit 5
            chown $uid:$uid $targetPath || exit 6
            chmod 600 $targetPath || exit 7
        """.trimIndent()
        runPrivileged(command, 120, missingMessage)
        if (!Files.isRegularFile(target) || Files.size(target) <= 0L) {
            error("The privileged copy completed without a readable file")
        }
    }

    private fun runPrivileged(command: String, timeoutSeconds: Long, fallbackMessage: String) {
        val result = commandRunner.run(command, timeoutSeconds)
        if (result.exitCode != 0) error(result.output.ifBlank { fallbackMessage })
    }

    private fun resetDirectory(path: Path) {
        path.toFile().deleteRecursively()
        Files.createDirectories(path)
    }

    private fun String.requireSafeIdentifier(label: String): String =
        takeIf { it.matches(SAFE_IDENTIFIER) } ?: error("Invalid $label")

    private fun String.requireSafeResourceCode(): String =
        takeIf { it.matches(SAFE_RESOURCE_CODE) } ?: error("Invalid Theme Manager resource code")

    private fun String?.normalized(): String = orEmpty().trim().lowercase(Locale.ROOT)

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun deterministicEntry(path: String) = ZipEntry(path).apply { time = 0L }
    private fun originKey(localId: String) = "$ORIGIN_PREFIX$localId"
    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private data class ThemeManagerRecord(
        val localId: String,
        val hash: String,
        val platform: Int,
        val version: String,
        val title: String,
        val author: String?,
        val designer: String?,
        val description: String?,
        val resources: List<ThemeManagerResource>,
        val previewNames: List<String>,
    )

    private data class ThemeManagerResource(val localId: String, val resourceCode: String)

    private companion object {
        const val THEME_DATA_ROOT =
            "/data/media/0/Android/data/com.android.thememanager/files/MIUI/theme/.data"
        const val MAX_PREVIEWS_PER_THEME = 16
        const val MAX_PREVIEW_CANDIDATES = 16
        const val ORIGIN_PREFIX = "theme:"
        val SAFE_IDENTIFIER = Regex("[A-Za-z0-9._-]{1,128}")
        val SAFE_RESOURCE_CODE = Regex("[A-Za-z0-9._-]{1,160}")
        val SAFE_PREVIEW_NAME = Regex("[A-Za-z0-9._-]{1,180}")
    }
}

package dev.glorioustr.mtzstudio.composer

import dev.glorioustr.mtzstudio.core.Hashing
import dev.glorioustr.mtzstudio.core.MtzParser
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class MtzComposer(private val parser: MtzParser = MtzParser()) {
    fun compose(request: CompositionRequest, output: Path): CompositionResult {
        validate(request)
        val parent = output.toAbsolutePath().parent ?: throw CompositionException("Output must have a parent directory")
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val selectedEntries = resolveEntries(request)
            writePackage(temporary, request.metadata, selectedEntries)
            parser.parse(temporary) // Fail before publishing a structurally invalid package.
            moveAtomically(temporary, output)
            val verified = parser.parse(output)
            return CompositionResult(
                output = output,
                outputSha256 = verified.sha256,
                verifiedArchive = verified,
                provenance = request.selections.map { selection ->
                    ProvenanceEntry(
                        sourceThemeId = selection.source.themeId,
                        sourceDisplayName = selection.source.displayName,
                        sourceSha256 = selection.source.archive.sha256,
                        category = selection.category,
                        rootPath = selection.rootPath,
                    )
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

    private fun validate(request: CompositionRequest) {
        if (request.metadata.name.isBlank()) throw CompositionException("Output theme name is required")
        if (request.selections.size < 2) throw CompositionException("Select at least two components")
        if (request.selections.map { it.source.themeId }.distinct().size < 2) {
            throw CompositionException("The spike requires components from at least two source themes")
        }
        val duplicateCategories = request.selections.groupingBy { it.category }.eachCount().filterValues { it > 1 }.keys
        if (duplicateCategories.isNotEmpty()) throw CompositionException(
            "Only one source per component category is allowed: ${duplicateCategories.joinToString()}",
        )
    }

    private fun resolveEntries(request: CompositionRequest): List<SelectedEntry> {
        val outputPaths = hashSetOf<String>()
        val result = mutableListOf<SelectedEntry>()
        request.selections.forEach { selection ->
            val archive = selection.source.archive
            if (Hashing.sha256(archive.source) != archive.sha256) {
                throw CompositionException("Source changed after import: ${selection.source.displayName}")
            }
            val component = archive.components.singleOrNull {
                it.category == selection.category && it.rootPath == selection.rootPath
            } ?: throw CompositionException("Selected component no longer exists: ${selection.rootPath}")
            val allowed = archive.entries.associateBy { it.path }
            component.entryPaths.forEach { path ->
                val entry = allowed[path] ?: throw CompositionException("Missing selected entry: $path")
                if (entry.rightsRelated) throw CompositionException("Rights entries cannot be composed: $path")
                val identity = path.lowercase(Locale.ROOT)
                if (!outputPaths.add(identity)) throw CompositionException("Selected components overlap at: $path")
                result += SelectedEntry(archive.source, path)
            }
        }
        return result.sortedBy { it.entryPath.lowercase(Locale.ROOT) }
    }

    private fun writePackage(target: Path, metadata: CompositionMetadata, entries: List<SelectedEntry>) {
        ZipOutputStream(Files.newOutputStream(target)).use { output ->
            output.setLevel(9)
            output.putNextEntry(deterministicEntry("description.xml"))
            output.write(descriptionXml(metadata).encodeToByteArray())
            output.closeEntry()

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
                    output.putNextEntry(deterministicEntry(selected.entryPath))
                    currentZip!!.getInputStream(inputEntry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            } finally {
                currentZip?.close()
            }
        }
    }

    private fun deterministicEntry(path: String) = ZipEntry(path).apply {
        time = 0L
        comment = null
        extra = null
    }

    private fun descriptionXml(metadata: CompositionMetadata): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<theme>\n")
        append("  <title>").append(xml(metadata.name)).append("</title>\n")
        metadata.author?.takeIf(String::isNotBlank)?.let { append("  <author>").append(xml(it)).append("</author>\n") }
        metadata.designer?.takeIf(String::isNotBlank)?.let { append("  <designer>").append(xml(it)).append("</designer>\n") }
        append("  <version>").append(xml(metadata.version)).append("</version>\n")
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
}


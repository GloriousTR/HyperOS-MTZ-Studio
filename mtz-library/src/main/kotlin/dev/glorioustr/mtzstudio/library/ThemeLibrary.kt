package dev.glorioustr.mtzstudio.library

import android.content.Context
import dev.glorioustr.mtzstudio.composer.CompositionResult
import dev.glorioustr.mtzstudio.core.MtzArchive
import dev.glorioustr.mtzstudio.core.MtzParser
import dev.glorioustr.mtzstudio.core.ThemeId
import dev.glorioustr.mtzstudio.core.UnsafeMtzException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Properties
import java.util.UUID

data class LibraryTheme(
    val id: ThemeId,
    val displayName: String,
    val importedAt: Instant,
    val archive: MtzArchive,
    val includeInThemeGallery: Boolean = false,
)

data class LibrarySnapshot(
    val themes: List<LibraryTheme>,
    val warnings: List<String>,
)

class ThemeLibrary internal constructor(
    private val filesRoot: Path,
    private val parser: MtzParser = MtzParser(),
    private val maxSourceBytes: Long = 256L * 1024 * 1024,
) {
    constructor(
        context: Context,
        parser: MtzParser = MtzParser(),
        maxSourceBytes: Long = 256L * 1024 * 1024,
    ) : this(context.filesDir.toPath(), parser, maxSourceBytes)

    private val libraryRoot: Path = filesRoot.resolve("mtz-library")
    private val exportsRoot: Path = filesRoot.resolve("exports")
    private val historyRoot: Path = filesRoot.resolve("mtz-history")

    fun importTheme(
        input: InputStream,
        suggestedName: String?,
        observer: ImportObserver = ImportObserver.NONE,
        includeInThemeGallery: Boolean = false,
    ): LibraryTheme {
        Files.createDirectories(libraryRoot)
        val staging = libraryRoot.resolve(".import-${UUID.randomUUID()}.tmp")
        var stage = "staging"
        notify(observer, ImportEvent.StagingCreated(staging.fileName.toString()))
        try {
            stage = "copy"
            notify(observer, ImportEvent.CopyStarted)
            val copiedBytes = copyBounded(input, staging, observer)
            notify(observer, ImportEvent.CopyCompleted(copiedBytes))

            stage = "validation"
            notify(observer, ImportEvent.ValidationStarted)
            val stagedArchive = parser.parse(staging)
            notify(
                observer,
                ImportEvent.ValidationCompleted(stagedArchive.sha256, stagedArchive.entries.size),
            )

            val id = ThemeId(UUID.randomUUID().toString())
            stage = "commit"
            notify(observer, ImportEvent.CommitStarted(id.value))
            val themeDirectory = libraryRoot.resolve(id.value)
            Files.createDirectory(themeDirectory)
            val source = themeDirectory.resolve("source.mtz")
            try {
                moveAtomically(staging, source)
                val archive = parser.parse(source)
                val theme = LibraryTheme(
                    id = id,
                    displayName = suggestedName?.takeIf(String::isNotBlank)?.take(180)
                        ?: archive.metadata?.name?.takeIf(String::isNotBlank)?.take(180)
                        ?: "Imported MTZ",
                    importedAt = Instant.now(),
                    archive = archive,
                    includeInThemeGallery = includeInThemeGallery,
                )
                writeManifest(themeDirectory, theme)
                notify(observer, ImportEvent.CommitCompleted(id.value, archive.sha256))
                return theme
            } catch (error: Exception) {
                themeDirectory.toFile().deleteRecursively()
                throw error
            }
        } catch (error: Exception) {
            notify(
                observer,
                ImportEvent.Failed(stage, error.message ?: error::class.simpleName ?: "unknown error"),
            )
            throw error
        } finally {
            val removed = Files.deleteIfExists(staging)
            notify(observer, ImportEvent.StagingCleaned(removed))
        }
    }

    fun load(): LibrarySnapshot {
        Files.createDirectories(libraryRoot)
        val themes = mutableListOf<LibraryTheme>()
        val warnings = mutableListOf<String>()
        Files.list(libraryRoot).use { directories ->
            directories.filter(Files::isDirectory).forEach { directory ->
                try {
                    themes += readTheme(directory)
                } catch (error: Exception) {
                    warnings += "${directory.fileName}: ${error.message ?: "could not load"}"
                }
            }
        }
        return LibrarySnapshot(themes.sortedByDescending(LibraryTheme::importedAt), warnings)
    }

    fun newExportPath(baseName: String): Path {
        Files.createDirectories(exportsRoot)
        val safeName = baseName
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.')
            .take(80)
            .ifBlank { "composed-theme" }
        var candidate = exportsRoot.resolve("$safeName.mtz")
        var suffix = 2
        while (Files.exists(candidate)) candidate = exportsRoot.resolve("$safeName-$suffix.mtz").also { suffix++ }
        return candidate
    }

    fun exportTheme(theme: LibraryTheme): Path {
        val current = parser.parse(theme.archive.source)
        if (current.sha256 != theme.archive.sha256) error("Private source hash changed")
        val target = newExportPath(theme.archive.metadata?.name ?: theme.displayName)
        Files.copy(theme.archive.source, target)
        return target
    }

    fun deleteTheme(themeId: ThemeId): Boolean {
        val themeDirectory = libraryRoot.resolve(themeId.value)
        return if (Files.exists(themeDirectory)) {
            themeDirectory.toFile().deleteRecursively()
        } else {
            false
        }
    }

    fun recordComposition(result: CompositionResult) {
        Files.createDirectories(historyRoot)
        val record = Properties().apply {
            setProperty("createdAt", Instant.now().toString())
            setProperty("output", result.output.fileName.toString())
            setProperty("outputSha256", result.outputSha256)
            setProperty("selectionCount", result.provenance.size.toString())
            result.provenance.forEachIndexed { index, entry ->
                setProperty("source.$index.id", entry.sourceThemeId.value)
                setProperty("source.$index.name", entry.sourceDisplayName)
                setProperty("source.$index.sha256", entry.sourceSha256)
                setProperty("source.$index.category", entry.category.name)
                setProperty("source.$index.root", entry.rootPath)
            }
        }
        val target = historyRoot.resolve("${Instant.now().toEpochMilli()}-${UUID.randomUUID()}.properties")
        Files.newOutputStream(target).use { record.store(it, "HyperOS MTZ Studio provenance") }
    }

    private fun copyBounded(input: InputStream, target: Path, observer: ImportObserver): Long {
        Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            var lastReported = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count.toLong() > maxSourceBytes - total) {
                    throw UnsafeMtzException(
                        UnsafeMtzException.Reason.ARCHIVE_TOO_LARGE,
                        "Selected MTZ exceeds the source-size limit",
                    )
                }
                output.write(buffer, 0, count)
                total += count
                if (total - lastReported >= PROGRESS_INTERVAL_BYTES) {
                    notify(observer, ImportEvent.CopyProgress(total))
                    lastReported = total
                }
            }
            if (total != lastReported) notify(observer, ImportEvent.CopyProgress(total))
            return total
        }
    }

    private fun notify(observer: ImportObserver, event: ImportEvent) {
        runCatching { observer.onEvent(event) }
    }

    private fun readTheme(directory: Path): LibraryTheme {
        val manifest = Properties().apply {
            Files.newInputStream(directory.resolve("manifest.properties")).use(::load)
        }
        val id = ThemeId(manifest.required("id"))
        if (directory.fileName.toString() != id.value) error("Manifest ID does not match its directory")
        val archive = parser.parse(directory.resolve("source.mtz"))
        if (archive.sha256 != manifest.required("sha256")) error("Private source hash changed")
        return LibraryTheme(
            id = id,
            displayName = manifest.required("displayName"),
            importedAt = Instant.parse(manifest.required("importedAt")),
            archive = archive,
            includeInThemeGallery = manifest.getProperty("includeInThemeGallery", "false").toBoolean(),
        )
    }

    private fun writeManifest(directory: Path, theme: LibraryTheme) {
        val manifest = Properties().apply {
            setProperty("id", theme.id.value)
            setProperty("displayName", theme.displayName)
            setProperty("importedAt", theme.importedAt.toString())
            setProperty("sha256", theme.archive.sha256)
            setProperty("includeInThemeGallery", theme.includeInThemeGallery.toString())
        }
        val temporary = directory.resolve(".manifest-${UUID.randomUUID()}.tmp")
        Files.newOutputStream(temporary).use { manifest.store(it, "HyperOS MTZ Studio library item") }
        moveAtomically(temporary, directory.resolve("manifest.properties"))
    }

    private fun Properties.required(key: String): String =
        getProperty(key)?.takeIf(String::isNotBlank) ?: error("Missing manifest field: $key")

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_BYTES = 8L * 1024 * 1024
    }
}

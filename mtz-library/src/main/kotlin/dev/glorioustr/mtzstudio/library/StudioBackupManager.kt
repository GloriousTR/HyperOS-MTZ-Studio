package dev.glorioustr.mtzstudio.library

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StudioBackupSummary(
    val themeCount: Int,
    val fileCount: Int,
    val contentBytes: Long,
)

class StudioBackupManager internal constructor(
    private val filesRoot: Path,
    private val cacheRoot: Path,
) {
    constructor(context: Context) : this(context.filesDir.toPath(), context.cacheDir.toPath())

    fun create(output: OutputStream): StudioBackupSummary {
        val files = backupFiles()
        val themeCount = ThemeLibrary(filesRoot).load().themes.size
        var contentBytes = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            val metadata = Properties().apply {
                setProperty("schemaVersion", BACKUP_SCHEMA_VERSION.toString())
                setProperty("createdAt", Instant.now().toString())
                setProperty("themeCount", themeCount.toString())
            }
            zip.putNextEntry(ZipEntry(BACKUP_MANIFEST))
            metadata.store(zip, "HyperOS MTZ Studio backup")
            zip.closeEntry()

            files.forEach { file ->
                val relative = filesRoot.relativize(file).toString().replace('\\', '/')
                zip.putNextEntry(ZipEntry(relative))
                Files.newInputStream(file).use { input -> contentBytes += input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return StudioBackupSummary(themeCount, files.size, contentBytes)
    }

    fun restore(input: InputStream): StudioBackupSummary {
        Files.createDirectories(cacheRoot)
        val staging = cacheRoot.resolve("backup-restore-${UUID.randomUUID()}")
        Files.createDirectory(staging)
        var fileCount = 0
        var contentBytes = 0L
        var manifest: Properties? = null
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = normalizedEntryName(entry.name)
                    if (entryName == BACKUP_MANIFEST) {
                        check(manifest == null) { "Backup manifest is duplicated" }
                        manifest = Properties().apply { load(zip) }
                    } else {
                        check(isAllowedContentPath(entryName)) { "Backup contains an unsupported path: $entryName" }
                        check(!entry.isDirectory) { "Backup contains an unexpected directory entry: $entryName" }
                        check(fileCount < MAX_BACKUP_FILES) { "Backup contains too many files" }
                        val target = staging.resolve(entryName).normalize()
                        check(target.startsWith(staging)) { "Backup path escapes the restore area" }
                        Files.createDirectories(target.parent)
                        Files.newOutputStream(target).use { output ->
                            val copied = copyBounded(zip, output, MAX_ENTRY_BYTES)
                            check(contentBytes <= MAX_TOTAL_BYTES - copied) { "Backup exceeds the restore size limit" }
                            contentBytes += copied
                        }
                        fileCount++
                    }
                    zip.closeEntry()
                }
            }

            val metadata = requireNotNull(manifest) { "Backup manifest is missing" }
            check(metadata.getProperty("schemaVersion") == BACKUP_SCHEMA_VERSION.toString()) {
                "Unsupported backup version"
            }
            val restoredSnapshot = ThemeLibrary(staging).load()
            check(restoredSnapshot.warnings.isEmpty()) {
                "Backup theme verification failed: ${restoredSnapshot.warnings.first()}"
            }
            mergeIntoAppStorage(staging)
            val finalSnapshot = ThemeLibrary(filesRoot).load()
            check(finalSnapshot.warnings.isEmpty()) {
                "Restored library verification failed: ${finalSnapshot.warnings.first()}"
            }
            return StudioBackupSummary(finalSnapshot.themes.size, fileCount, contentBytes)
        } finally {
            staging.toFile().deleteRecursively()
        }
    }

    private fun backupFiles(): List<Path> = ALLOWED_ROOTS.flatMap { rootName ->
        val root = filesRoot.resolve(rootName)
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return@flatMap emptyList()
        val files = mutableListOf<Path>()
        Files.walk(root).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
            }.forEach(files::add)
        }
        files
    }.sortedBy { filesRoot.relativize(it).toString() }

    private fun mergeIntoAppStorage(staging: Path) {
        ALLOWED_ROOTS.forEach { rootName ->
            val sourceRoot = staging.resolve(rootName)
            if (!Files.isDirectory(sourceRoot, LinkOption.NOFOLLOW_LINKS)) return@forEach
            Files.walk(sourceRoot).use { paths ->
                paths.forEach { source ->
                    val target = filesRoot.resolve(staging.relativize(source).toString()).normalize()
                    check(target.startsWith(filesRoot)) { "Restore target escapes app storage" }
                    if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    private fun normalizedEntryName(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        check(normalized.isNotBlank() && normalized == raw.replace('\\', '/')) { "Invalid backup path" }
        check(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Invalid backup path" }
        return normalized
    }

    private fun isAllowedContentPath(name: String): Boolean =
        ALLOWED_ROOTS.any { root -> name.startsWith("$root/") }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(count.toLong() <= limit - total) { "Backup entry exceeds the restore size limit" }
            output.write(buffer, 0, count)
            total += count
        }
        return total
    }

    private companion object {
        const val BACKUP_MANIFEST = "backup.properties"
        const val BACKUP_SCHEMA_VERSION = 1
        const val MAX_BACKUP_FILES = 50_000
        const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
        val ALLOWED_ROOTS = listOf("mtz-library", "exports", "mtz-history", "diagnostics", "settings")
    }
}

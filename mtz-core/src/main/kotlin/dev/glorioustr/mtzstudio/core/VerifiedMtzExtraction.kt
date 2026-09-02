package dev.glorioustr.mtzstudio.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Owns only a newly created private workspace, never the caller's MTZ or native catalog. */
class VerifiedMtzExtraction private constructor(
    private val workspace: Path,
    val directory: File,
    val fileSha1: Map<String, String>,
) : AutoCloseable {
    override fun close() = removeWorkspace(workspace)

    companion object {
        @JvmStatic
        @JvmOverloads
        fun extract(
            source: File,
            cacheParent: File,
            expectedSha256: String,
            limits: MtzSecurityLimits = MtzSecurityLimits(),
        ): VerifiedMtzExtraction {
            require(expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Missing MTZ identity" }
            val parent = cacheParent.toPath().toRealPath()
            require(Files.isDirectory(parent)) { "Private cache is unavailable" }
            val workspace = Files.createTempDirectory(parent, "mtz-bridge-")
            try {
                // Pin the input in private storage before validating or unpacking it.
                val snapshot = workspace.resolve("source.mtz")
                source.inputStream().use { input ->
                    Files.newOutputStream(snapshot, CREATE_NEW, WRITE).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            checkInterrupted()
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > limits.maxArchiveBytes) throw IOException("MTZ source exceeds size limit")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (!Hashing.sha256(snapshot).equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("MTZ SHA-256 changed before fallback extraction")
                }
                // Reuse all Studio checks: symlinks, duplicate/traversal paths, sizes and ZIP structure.
                val archive = MtzParser(limits).parse(snapshot)
                if (archive.entries.none { !it.directory }) throw IOException("MTZ contains no files")
                val root = Files.createDirectory(workspace.resolve("contents")).toRealPath()
                val hashes = linkedMapOf<String, String>()
                var total = 0L
                ZipFile(snapshot.toFile()).use { zip ->
                    archive.entries.forEach { entry ->
                        checkInterrupted()
                        val target = root.resolve(entry.path.removeSuffix("/")).normalize()
                        if (target == root || !target.startsWith(root)) throw IOException("Unsafe extraction target")
                        if (entry.directory) {
                            Files.createDirectories(target)
                        } else {
                            Files.createDirectories(target.parent)
                            if (!target.parent.toRealPath().startsWith(root)) throw IOException("Extraction parent escaped workspace")
                            val nativeEntry = zip.getEntry(entry.path) ?: throw IOException("Missing ZIP entry: ${entry.path}")
                            val crc = CRC32()
                            val sha1 = MessageDigest.getInstance("SHA-1")
                            var size = 0L
                            zip.getInputStream(nativeEntry).use { input ->
                                Files.newOutputStream(target, CREATE_NEW, WRITE).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        checkInterrupted()
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        size += read
                                        total += read
                                        if (size > limits.maxEntryBytes || total > limits.maxExpandedBytes) {
                                            throw IOException("MTZ expanded size exceeds limit")
                                        }
                                        output.write(buffer, 0, read)
                                        crc.update(buffer, 0, read)
                                        sha1.update(buffer, 0, read)
                                    }
                                }
                            }
                            if (size != entry.expandedBytes || crc.value != entry.crc) {
                                throw IOException("Extracted entry size/CRC mismatch: ${entry.path}")
                            }
                            if (!Files.isRegularFile(target, NOFOLLOW_LINKS)) throw IOException("Missing extracted file")
                            hashes[target.toString()] = sha1.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                        }
                    }
                }
                return VerifiedMtzExtraction(workspace, root.toFile(), hashes)
            } catch (error: Throwable) {
                runCatching { removeWorkspace(workspace) }.onFailure(error::addSuppressed)
                throw error
            }
        }

        private fun checkInterrupted() {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("MTZ extraction interrupted")
        }

        private fun removeWorkspace(workspace: Path) {
            // Files.walk does not follow symlinks. Only the workspace allocated above is eligible.
            if (!Files.exists(workspace, NOFOLLOW_LINKS)) return
            Files.walk(workspace).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

package dev.glorioustr.mtzstudio.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile

class MtzParser(private val limits: MtzSecurityLimits = MtzSecurityLimits()) {
    fun parse(source: Path): MtzArchive {
        if (!Files.isRegularFile(source)) fail(UnsafeMtzException.Reason.NOT_A_REGULAR_FILE, "MTZ is not a regular file")
        val archiveBytes = Files.size(source)
        if (archiveBytes > limits.maxArchiveBytes) fail(UnsafeMtzException.Reason.ARCHIVE_TOO_LARGE, "MTZ exceeds the source-size limit")

        try {
            val symlinks = ZipCentralDirectory.symlinkNames(source).mapTo(hashSetOf()) {
                SafeArchivePath.normalize(it, it.endsWith('/')).lowercase(Locale.ROOT)
            }
            return ZipFile(source.toFile()).use { zip -> inspect(source, zip, symlinks) }
        } catch (error: UnsafeMtzException) {
            throw error
        } catch (error: ZipException) {
            throw UnsafeMtzException(UnsafeMtzException.Reason.INVALID_ZIP, "Invalid MTZ/ZIP archive", error)
        } catch (error: Exception) {
            throw UnsafeMtzException(UnsafeMtzException.Reason.INVALID_ZIP, "Could not inspect MTZ archive", error)
        }
    }

    private fun inspect(source: Path, zip: ZipFile, symlinks: Set<String>): MtzArchive {
        val seen = hashSetOf<String>()
        val entries = mutableListOf<MtzEntry>()
        val nativeEntries = zip.entries().asSequence().toList()
        if (nativeEntries.size > limits.maxEntries) fail(UnsafeMtzException.Reason.TOO_MANY_ENTRIES, "MTZ has too many entries")

        var declaredTotal = 0L
        nativeEntries.forEach { entry ->
            val path = SafeArchivePath.normalize(entry.name, entry.isDirectory)
            val identity = path.lowercase(Locale.ROOT)
            if (!seen.add(identity)) fail(UnsafeMtzException.Reason.DUPLICATE_PATH, "Duplicate MTZ path: $path")
            if (identity in symlinks) fail(UnsafeMtzException.Reason.SYMLINK, "Symbolic-link entry rejected: $path")
            if (entry.size < 0L || entry.compressedSize < 0L) fail(UnsafeMtzException.Reason.INVALID_ZIP, "Unknown entry size: $path")
            if (entry.size > limits.maxEntryBytes) fail(UnsafeMtzException.Reason.ENTRY_TOO_LARGE, "Entry exceeds expanded-size limit: $path")
            declaredTotal = checkedAdd(declaredTotal, entry.size, path)
            val ratio = if (entry.compressedSize == 0L) {
                if (entry.size == 0L) 1.0 else Double.POSITIVE_INFINITY
            } else entry.size.toDouble() / entry.compressedSize.toDouble()
            if (ratio > limits.maxCompressionRatio) fail(
                UnsafeMtzException.Reason.SUSPICIOUS_COMPRESSION_RATIO,
                "Suspicious compression ratio for $path",
            )
            entries += MtzEntry(
                path = path,
                compressedBytes = entry.compressedSize,
                expandedBytes = entry.size,
                crc = entry.crc,
                directory = entry.isDirectory,
                rightsRelated = isRightsPath(path),
            )
        }

        var observedTotal = 0L
        nativeEntries.filterNot { it.isDirectory }.forEach { entry ->
            var observedEntry = 0L
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    observedEntry = checkedEntryAdd(observedEntry, count.toLong(), entry.name)
                    observedTotal = checkedAdd(observedTotal, count.toLong(), entry.name)
                }
            }
            if (observedEntry != entry.size) fail(UnsafeMtzException.Reason.TRUNCATED_ENTRY, "Entry size mismatch: ${entry.name}")
        }

        val metadataEntry = entries.singleOrNull { !it.directory && it.path.equals("description.xml", ignoreCase = true) }
        val metadata = metadataEntry?.let { info ->
            if (info.expandedBytes > limits.maxMetadataBytes) fail(UnsafeMtzException.Reason.METADATA_TOO_LARGE, "description.xml is too large")
            val native = zip.getEntry(info.path) ?: nativeEntries.first { it.name.equals(info.path, ignoreCase = true) }
            val bytes = zip.getInputStream(native).use { it.readNBytes((limits.maxMetadataBytes + 1).toInt()) }
            if (bytes.size > limits.maxMetadataBytes) fail(UnsafeMtzException.Reason.METADATA_TOO_LARGE, "description.xml is too large")
            DescriptionXmlParser.parse(bytes)
        }

        return MtzArchive(
            source = source,
            sha256 = Hashing.sha256(source),
            metadata = metadata,
            entries = entries.sortedBy { it.path.lowercase(Locale.ROOT) },
            components = ComponentRecognizer.recognize(entries),
            rightsEntries = entries.filter(MtzEntry::rightsRelated).map(MtzEntry::path).sorted(),
            expandedBytes = observedTotal,
        )
    }

    private fun checkedAdd(total: Long, amount: Long, path: String): Long {
        if (amount > limits.maxExpandedBytes - total) fail(
            UnsafeMtzException.Reason.TOTAL_SIZE_EXCEEDED,
            "Expanded MTZ size limit exceeded near $path",
        )
        return total + amount
    }

    private fun checkedEntryAdd(total: Long, amount: Long, path: String): Long {
        if (amount > limits.maxEntryBytes - total) fail(
            UnsafeMtzException.Reason.ENTRY_TOO_LARGE,
            "Entry exceeds expanded-size limit: $path",
        )
        return total + amount
    }

    private fun isRightsPath(path: String): Boolean {
        val segments = path.lowercase(Locale.ROOT).removeSuffix("/").split('/')
        return segments.any { segment ->
            segment == "rights" || segment == "rights.xml" || segment == "rights.json" ||
                segment.startsWith("rights.") || segment == "license.key" || segment == "entitlement"
        }
    }

    private fun fail(reason: UnsafeMtzException.Reason, message: String): Nothing =
        throw UnsafeMtzException(reason, message)
}

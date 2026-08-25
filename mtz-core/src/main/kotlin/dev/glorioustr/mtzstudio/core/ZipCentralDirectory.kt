package dev.glorioustr.mtzstudio.core

import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.file.Path

internal object ZipCentralDirectory {
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val UNIX_FILE_TYPE_MASK = 0xF000
    private const val UNIX_SYMLINK = 0xA000

    fun symlinkNames(path: Path): Set<String> = RandomAccessFile(path.toFile(), "r").use { file ->
        val eocd = findEocd(file)
        file.seek(eocd + 10)
        val entryCount = file.readU16Le()
        file.skipBytes(4)
        val centralOffset = file.readU32Le()
        if (entryCount == 0xFFFF || centralOffset == 0xFFFFFFFFL) {
            throw UnsafeMtzException(
                UnsafeMtzException.Reason.UNSUPPORTED_ZIP64,
                "ZIP64 MTZ files are not supported by this spike",
            )
        }

        val symlinks = linkedSetOf<String>()
        file.seek(centralOffset)
        repeat(entryCount) {
            if (file.readU32Le() != CENTRAL_SIGNATURE) invalid("Invalid central-directory entry")
            val versionMadeBy = file.readU16Le()
            val flags = file.readU16LeAtCurrentOffset(2)
            file.skipBytes(18)
            val nameLength = file.readU16Le()
            val extraLength = file.readU16Le()
            val commentLength = file.readU16Le()
            file.skipBytes(4)
            val externalAttributes = file.readU32Le()
            file.skipBytes(4)
            if (nameLength <= 0 || nameLength > 65_535) invalid("Invalid ZIP entry name")
            val nameBytes = ByteArray(nameLength)
            file.readFully(nameBytes)
            val charset = if (flags and 0x800 != 0) Charsets.UTF_8 else Charset.forName("CP437")
            val name = String(nameBytes, charset)
            file.skipBytes(extraLength + commentLength)

            val creatorSystem = versionMadeBy ushr 8
            val unixMode = (externalAttributes ushr 16).toInt() and 0xFFFF
            if (creatorSystem == 3 && unixMode and UNIX_FILE_TYPE_MASK == UNIX_SYMLINK) {
                symlinks += name
            }
        }
        symlinks
    }

    private fun findEocd(file: RandomAccessFile): Long {
        val start = (file.length() - 65_557L).coerceAtLeast(0L)
        var cursor = file.length() - 22L
        while (cursor >= start) {
            file.seek(cursor)
            if (file.readU32Le() == EOCD_SIGNATURE) return cursor
            cursor--
        }
        invalid("ZIP end-of-central-directory record not found")
    }

    private fun RandomAccessFile.readU16Le(): Int {
        val a = read()
        val b = read()
        if (a < 0 || b < 0) invalid("Unexpected end of ZIP")
        return a or (b shl 8)
    }

    private fun RandomAccessFile.readU16LeAtCurrentOffset(skip: Int): Int {
        skipBytes(skip)
        return readU16Le()
    }

    private fun RandomAccessFile.readU32Le(): Long {
        val low = readU16Le().toLong()
        val high = readU16Le().toLong()
        return low or (high shl 16)
    }

    private fun invalid(message: String): Nothing = throw UnsafeMtzException(
        UnsafeMtzException.Reason.INVALID_ZIP,
        message,
    )
}

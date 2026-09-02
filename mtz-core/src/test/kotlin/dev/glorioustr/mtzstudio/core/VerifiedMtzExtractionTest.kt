package dev.glorioustr.mtzstudio.core

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifiedMtzExtractionTest {
    @Test
    fun `extracts verified archive to owned workspace and cleans it`() {
        val parent = Files.createTempDirectory("mtz-extraction-test")
        val source = zip(
            parent,
            "description.xml" to "<theme><title>Safe</title></theme>".encodeToByteArray(),
            "icons" to byteArrayOf(1, 2, 3),
            "preview/cover.jpg" to byteArrayOf(4, 5),
        )
        val neighbor = parent.resolve("keep.txt")
        Files.writeString(neighbor, "unchanged")

        val extraction = VerifiedMtzExtraction.extract(source.toFile(), parent.toFile(), Hashing.sha256(source))
        val workspace = extraction.directory.toPath().parent
        try {
            assertEquals(byteArrayOf(1, 2, 3).toList(), extraction.directory.toPath().resolve("icons").readBytes().toList())
            assertEquals(byteArrayOf(4, 5).toList(), extraction.directory.toPath().resolve("preview/cover.jpg").readBytes().toList())
            assertEquals(40, extraction.fileSha1[extraction.directory.toPath().resolve("icons").toString()]?.length)
        } finally {
            extraction.close()
            extraction.close()
        }
        assertFalse(Files.exists(workspace))
        assertTrue(Files.exists(source))
        assertEquals("unchanged", Files.readString(neighbor))
    }

    @Test
    fun `rejects identity mismatch without retaining a workspace`() {
        val parent = Files.createTempDirectory("mtz-extraction-hash")
        val source = zip(parent, "icons" to byteArrayOf(7))

        assertFailsWith<java.io.IOException> {
            VerifiedMtzExtraction.extract(source.toFile(), parent.toFile(), "0".repeat(64))
        }

        Files.list(parent).use { children ->
            assertEquals(listOf(source.fileName.toString()), children.map { it.fileName.toString() }.sorted().toList())
        }
    }

    private fun zip(parent: java.nio.file.Path, vararg entries: Pair<String, ByteArray>): java.nio.file.Path {
        val path = Files.createTempFile(parent, "source", ".mtz")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return path
    }
}

package dev.glorioustr.mtzstudio.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudioBackupManagerTest {
    @Test
    fun `backup round trip preserves app content`() {
        val source = Files.createTempDirectory("studio-backup-source")
        val sourceCache = Files.createTempDirectory("studio-backup-source-cache")
        val diagnostics = source.resolve("diagnostics/events.jsonl")
        Files.createDirectories(diagnostics.parent)
        val expected = "{\"event\":\"imported\"}\n".encodeToByteArray()
        Files.write(diagnostics, expected)

        val archive = ByteArrayOutputStream()
        val created = StudioBackupManager(source, sourceCache).create(archive)
        assertEquals(1, created.fileCount)

        val target = Files.createTempDirectory("studio-backup-target")
        val targetCache = Files.createTempDirectory("studio-backup-target-cache")
        val restored = StudioBackupManager(target, targetCache).restore(ByteArrayInputStream(archive.toByteArray()))

        assertEquals(1, restored.fileCount)
        assertContentEquals(expected, Files.readAllBytes(target.resolve("diagnostics/events.jsonl")))
    }

    @Test
    fun `restore rejects paths outside app storage`() {
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("backup.properties"))
                Properties().apply { setProperty("schemaVersion", "1") }.store(zip, null)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("../outside.txt"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }
        val target = Files.createTempDirectory("studio-backup-slip")
        val cache = Files.createTempDirectory("studio-backup-slip-cache")

        assertFailsWith<IllegalStateException> {
            StudioBackupManager(target, cache).restore(ByteArrayInputStream(archive.toByteArray()))
        }
    }
}

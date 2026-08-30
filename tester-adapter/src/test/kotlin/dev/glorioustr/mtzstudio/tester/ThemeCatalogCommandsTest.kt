package dev.glorioustr.mtzstudio.tester

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Executes the production shell loop; ownership commands are stubbed only in the test shell. */
class ThemeCatalogCommandsTest {
    private fun shellPath(path: Path): String {
        val value = path.toAbsolutePath().toString().replace('\\', '/')
        return if (value.length > 2 && value[1] == ':') "/${value[0].lowercaseChar()}${value.drop(2)}" else value
    }

    private fun withCatalog(test: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("catalog test 'quoted'")
        try { test(Files.createDirectory(root.resolve("source")), Files.createDirectory(root.resolve("target"))) }
        finally { root.toFile().deleteRecursively() }
    }

    private fun execute(source: Path, target: Path, override: String = ""): Pair<Int, String> {
        val shell = if (System.getProperty("os.name").orEmpty().startsWith("Windows"))
            "C:/Program Files/Git/bin/bash.exe" else "/bin/sh"
        val process = ProcessBuilder(shell, "-s").redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use {
            it.write("chown() { return 0; }\nchmod() { return 0; }\n$override\n")
            it.write(ThemeCatalogCommands.copyMetadata(shellPath(source), shellPath(target), 10000))
            it.write("\n")
        }
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        assertTrue(finished, "Catalog test timed out")
        return process.exitValue() to process.inputStream.bufferedReader().readText()
    }

    @Test fun `empty directory is successful empty catalog not literal wildcard cp failure`() = withCatalog { source, target ->
        val (code, output) = execute(source, target)
        assertEquals(0, code, output)
        assertTrue("MTZ_METADATA_COUNT=0" in output)
        assertFalse("bad" in output)
    }

    @Test fun `missing folder is unavailable not empty success`() = withCatalog { source, target ->
        val (code, output) = execute(source.resolve("missing"), target)
        assertEquals(4, code, output)
        assertFalse("MTZ_METADATA_COUNT=" in output)
    }

    @Test fun `copies only metadata with quoted paths and reports exact count`() = withCatalog { source, target ->
        source.resolve("one ' two.mrm").toFile().writeText("metadata")
        source.resolve("ignored.txt").toFile().writeText("ignore")
        val (code, output) = execute(source, target)
        assertEquals(0, code, output)
        assertTrue("MTZ_METADATA_COUNT=1" in output)
        assertEquals("metadata", target.resolve("one ' two.mrm").toFile().readText())
        assertFalse(Files.exists(target.resolve("ignored.txt")))
    }

    @Test fun `failed copy never reports complete snapshot`() = withCatalog { source, target ->
        source.resolve("one.mrm").toFile().writeText("metadata")
        val (code, output) = execute(source, target, "cp() { return 1; }")
        assertEquals(5, code, output)
        assertFalse("MTZ_METADATA_COUNT=" in output)
    }

    @Test fun `ownership failure never reports complete snapshot`() = withCatalog { source, target ->
        val (code, output) = execute(source, target, "chown() { return 1; }")
        assertEquals(6, code, output)
        assertFalse("MTZ_METADATA_COUNT=" in output)
    }
}

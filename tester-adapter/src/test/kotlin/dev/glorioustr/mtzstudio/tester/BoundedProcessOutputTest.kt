package dev.glorioustr.mtzstudio.tester

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundedProcessOutputTest {
    private fun child(mode: String): Process {
        val executable = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "java.exe" else "java"
        val java = File(System.getProperty("java.home"), "bin/$executable").absolutePath
        val classpath = File(checkNotNull(OutputProcessFixture::class.java.protectionDomain).codeSource.location.toURI()).absolutePath
        return ProcessBuilder(java, "-cp", classpath, OutputProcessFixture::class.java.name, mode)
            .redirectErrorStream(true).start()
    }

    @Test fun `large merged output drains before waiting and retains bounded tail`() {
        val result = BoundedProcessOutput.collect(child("large"), 15, 512)
        assertFalse(result.timedOut)
        assertEquals(7, result.exitCode)
        assertEquals(512, result.output.length)
        assertTrue(result.output.endsWith("final diagnostic"))
    }

    @Test fun `timeout terminates child and preserves partial diagnostic`() {
        val process = child("timeout")
        val result = BoundedProcessOutput.collect(process, 2)
        assertTrue(result.timedOut)
        assertEquals(124, result.exitCode)
        assertTrue(result.output.contains("partial output"))
        assertFalse(process.isAlive)
    }
}

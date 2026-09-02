package dev.glorioustr.mtzstudio.tester

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class ProcessOutput(val exitCode: Int, val output: String, val timedOut: Boolean)

/** Drain while the child runs: waiting first deadlocks once the OS pipe fills. */
object BoundedProcessOutput {
    fun collect(process: Process, timeoutSeconds: Long, maxChars: Int = 8_192): ProcessOutput {
        require(timeoutSeconds > 0 && maxChars > 0)
        val tail = StringBuilder()
        val readFailure = AtomicReference<IOException?>()
        val reader = thread(name = "mtz-command-output", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().use { input ->
                    val buffer = CharArray(4_096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        synchronized(tail) {
                            tail.append(buffer, 0, count)
                            if (tail.length > maxChars) tail.delete(0, tail.length - maxChars)
                        }
                    }
                }
            } catch (error: IOException) {
                readFailure.set(error)
            }
        }
        try {
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            reader.join(1_000)
            val output = synchronized(tail) { tail.toString().trim() }
            if (!completed) return ProcessOutput(124, output, true)
            if (reader.isAlive) throw IOException("Command output did not close after process exit")
            readFailure.get()?.let { throw it }
            return ProcessOutput(process.exitValue(), output, false)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            // Never block the calling thread closing a pipe still held by a child process.
            if (!reader.isAlive) runCatching { process.inputStream.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }
}

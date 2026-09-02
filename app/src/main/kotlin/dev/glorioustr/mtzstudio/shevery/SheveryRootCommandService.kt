package dev.glorioustr.mtzstudio.shevery

import android.os.Process
import dev.glorioustr.mtzstudio.tester.BoundedProcessOutput
import kotlin.system.exitProcess

class SheveryRootCommandService : IRootCommandService.Stub() {
    override fun serviceUid(): Int = Process.myUid()

    override fun execute(command: String, timeoutSeconds: Int): String {
        require(command.isNotBlank()) { "Command must not be blank" }
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val result = BoundedProcessOutput.collect(process, timeoutSeconds.coerceIn(1, 300).toLong())
        val output = if (result.timedOut) "Privileged command timed out\n${result.output}" else result.output
        return "${result.exitCode}\u0000$output"
    }

    override fun destroy() {
        exitProcess(0)
    }
}

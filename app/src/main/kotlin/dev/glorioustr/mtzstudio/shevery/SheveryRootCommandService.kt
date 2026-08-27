package dev.glorioustr.mtzstudio.shevery

import android.os.Process
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class SheveryRootCommandService : IRootCommandService.Stub() {
    override fun serviceUid(): Int = Process.myUid()

    override fun execute(command: String, timeoutSeconds: Int): String {
        require(command.isNotBlank()) { "Command must not be blank" }
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return "124\u0000Privileged command timed out"
        }
        val output = process.inputStream.bufferedReader().use { it.readText().takeLast(8_192) }.trim()
        return "${process.exitValue()}\u0000$output"
    }

    override fun destroy() {
        exitProcess(0)
    }
}

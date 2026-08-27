package dev.glorioustr.mtzstudio.shevery

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.glorioustr.mtzstudio.BuildConfig
import dev.glorioustr.mtzstudio.tester.PrivilegedCommandResult
import dev.glorioustr.mtzstudio.tester.PrivilegedCommandRunner
import dev.glorioustr.mtzstudio.tester.SuPrivilegedCommandRunner
import dev.glorioustr.mtzstudio.tester.ThemeManagerUpdateException
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class SheveryAuthorizationStatus {
    ROOT_READY,
    ADB_ONLY,
    PERMISSION_REQUIRED,
    SERVICE_NOT_RUNNING,
}

class SheveryAccess(private val context: Context) {
    fun status(): SheveryAuthorizationStatus {
        return try {
            if (!Shizuku.pingBinder()) return SheveryAuthorizationStatus.SERVICE_NOT_RUNNING
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return SheveryAuthorizationStatus.PERMISSION_REQUIRED
            }
            if (Shizuku.getUid() == 0) SheveryAuthorizationStatus.ROOT_READY else SheveryAuthorizationStatus.ADB_ONLY
        } catch (_: Exception) {
            SheveryAuthorizationStatus.SERVICE_NOT_RUNNING
        }
    }

    fun requestPermission(requestCode: Int) {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(requestCode)
    }

    fun verifyRootService(): Boolean = runCatching {
        val result = executeRoot("id -u", 10)
        result.exitCode == 0 && result.output.lineSequence().firstOrNull()?.trim() == "0"
    }.getOrDefault(false)

    fun executeRoot(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
        if (status() != SheveryAuthorizationStatus.ROOT_READY) {
            throw ThemeManagerUpdateException("Shevery root authorization is not ready")
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, SheveryRootCommandService::class.java))
            .processNameSuffix("shevery_root")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
            .daemon(false)
        val latch = CountDownLatch(1)
        val service = AtomicReference<IRootCommandService?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service.set(IRootCommandService.Stub.asInterface(binder))
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service.set(null)
            }
        }
        Shizuku.bindUserService(args, connection)
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw ThemeManagerUpdateException("Shevery root service connection timed out")
            }
            val remote = service.get() ?: throw ThemeManagerUpdateException("Shevery root service is unavailable")
            if (remote.serviceUid() != 0) throw ThemeManagerUpdateException("Shevery service is not running as root")
            val encoded = remote.execute(command, timeoutSeconds.coerceIn(1, 300).toInt())
            val separator = encoded.indexOf('\u0000')
            val exitCode = encoded.substring(0, separator.coerceAtLeast(0)).toIntOrNull() ?: -1
            val output = if (separator >= 0) encoded.substring(separator + 1) else encoded
            return PrivilegedCommandResult(exitCode, output, "Shevery root")
        } finally {
            service.get()?.let { remote -> runCatching { remote.destroy() } }
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }
}

class PreferredPrivilegedCommandRunner(context: Context) : PrivilegedCommandRunner {
    private val shevery = SheveryAccess(context.applicationContext)
    private val su = SuPrivilegedCommandRunner()

    override fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
        if (shevery.status() == SheveryAuthorizationStatus.ROOT_READY) {
            runCatching { shevery.executeRoot(command, timeoutSeconds) }.getOrNull()?.let { return it }
        }
        return su.run(command, timeoutSeconds)
    }
}

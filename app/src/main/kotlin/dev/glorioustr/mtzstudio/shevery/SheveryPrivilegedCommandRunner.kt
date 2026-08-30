package dev.glorioustr.mtzstudio.shevery

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.glorioustr.mtzstudio.BuildConfig
import dev.glorioustr.mtzstudio.LiveDiagnosticsRecorder
import dev.glorioustr.mtzstudio.tester.VerifiedRootCommandRunner
import dev.glorioustr.mtzstudio.tester.RootAccessUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            throw ThemeManagerUpdateException("Shizuku-compatible root authorization is not ready")
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
                throw ThemeManagerUpdateException("Shizuku-compatible root service connection timed out")
            }
            val remote = service.get() ?: throw ThemeManagerUpdateException("Shizuku-compatible root service is unavailable")
            if (remote.serviceUid() != 0) throw ThemeManagerUpdateException("Shizuku-compatible service is not running as root")
            val encoded = remote.execute(command, timeoutSeconds.coerceIn(1, 300).toInt())
            val separator = encoded.indexOf('\u0000')
            val exitCode = encoded.substring(0, separator.coerceAtLeast(0)).toIntOrNull() ?: -1
            val output = if (separator >= 0) encoded.substring(separator + 1) else encoded
            return PrivilegedCommandResult(exitCode, output, "Shizuku-compatible root service")
        } finally {
            service.get()?.let { remote -> runCatching { remote.destroy() } }
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }
}

class PreferredPrivilegedCommandRunner(context: Context) : PrivilegedCommandRunner {
    private val appContext = context.applicationContext
    private val shevery = SheveryAccess(appContext)
    private val su = SuPrivilegedCommandRunner()
    private val accessFailure = MutableStateFlow<String?>(null)
    val authorizationFailure = accessFailure.asStateFlow()
    private val verifiedRunner = VerifiedRootCommandRunner(
        service = {
            if (shevery.status() == SheveryAuthorizationStatus.ROOT_READY) {
                PrivilegedCommandRunner { command, timeout -> shevery.executeRoot(command, timeout) }
            } else null
        },
        direct = su,
    )

    @Synchronized
    override fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
        val serviceStatus = shevery.status()
        return try {
            verifiedRunner.run(command, timeoutSeconds)
        } catch (error: RootAccessUnavailableException) {
            val message = when (serviceStatus) {
                SheveryAuthorizationStatus.PERMISSION_REQUIRED ->
                    appContext.getString(dev.glorioustr.mtzstudio.R.string.privileged_access_permission_required)
                SheveryAuthorizationStatus.ADB_ONLY ->
                    appContext.getString(dev.glorioustr.mtzstudio.R.string.privileged_access_adb_only)
                else -> appContext.getString(dev.glorioustr.mtzstudio.R.string.privileged_access_unavailable)
            }
            LiveDiagnosticsRecorder.get(appContext).record(
                "root_access_unavailable", "Yetkili işlem kanalı kullanılamıyor",
                mapOf("compatibleService" to serviceStatus), error,
            )
            accessFailure.value = message
            throw ThemeManagerUpdateException(message, error)
        }
    }

    fun dismissAuthorizationFailure() { accessFailure.value = null }

    fun requireRootReady() {
        run("id -u", ROOT_PROBE_TIMEOUT_SECONDS).also { result ->
            if (result.exitCode != 0 || result.output.lineSequence().firstOrNull()?.trim() != "0") {
                throw ThemeManagerUpdateException(
                    appContext.getString(dev.glorioustr.mtzstudio.R.string.privileged_access_unavailable),
                )
            }
        }
    }

    private companion object {
        const val ROOT_PROBE_TIMEOUT_SECONDS = 10L
    }
}

package dev.glorioustr.mtzstudio.tester

import android.content.Context
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

class VerifiedThemeManagerApk internal constructor(
    internal val stagedPath: Path,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sha256: String,
)

data class RootUpdateResult(
    val success: Boolean,
    val message: String,
    val commandOutput: String,
    val authorizationSource: String,
    val installedAfter: InstalledThemeManager,
)

data class PrivilegedCommandResult(
    val exitCode: Int,
    val output: String,
    val authorizationSource: String,
)

fun interface PrivilegedCommandRunner {
    fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult
}

class SuPrivilegedCommandRunner : PrivilegedCommandRunner {
    private val useGlobalMountNamespace: Boolean by lazy {
        try {
            val help = BoundedProcessOutput.collect(
                ProcessBuilder("su", "--help").redirectErrorStream(true).start(), 3,
            )
            !help.timedOut && SuCommandPolicy.supportsGlobalMountNamespace(help.output)
        } catch (error: Exception) {
            if (error is InterruptedException || error is java.util.concurrent.CancellationException) throw error
            false
        }
    }

    override fun run(command: String, timeoutSeconds: Long): PrivilegedCommandResult {
        val process = try {
            ProcessBuilder(SuCommandPolicy.arguments(command, useGlobalMountNamespace))
                .redirectErrorStream(true).start()
        } catch (error: IOException) {
            throw ThemeManagerUpdateException("Root command channel could not be started", error)
        }
        val result = BoundedProcessOutput.collect(process, timeoutSeconds)
        if (result.timedOut) throw ThemeManagerUpdateException("Root command timed out: ${result.output}")
        return PrivilegedCommandResult(
            exitCode = result.exitCode,
            output = result.output,
            authorizationSource = if (useGlobalMountNamespace) "Root yöneticisi (su, global mount namespace)" else "Root yöneticisi (su)",
        )
    }
}

class ThemeManagerUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RootThemeManagerUpdater(
    context: Context,
    private val inspector: ThemeManagerInspector = ThemeManagerInspector(context),
    private val maxApkBytes: Long = 256L * 1024 * 1024,
    private val commandRunner: PrivilegedCommandRunner = SuPrivilegedCommandRunner(),
) {
    private val stagingRoot = context.cacheDir.toPath().resolve("theme-manager-update")

    fun stageAndVerify(input: InputStream, installed: InstalledThemeManager): VerifiedThemeManagerApk {
        if (!installed.installed) throw ThemeManagerUpdateException("A supported Xiaomi Theme Manager package is not installed")
        Files.createDirectories(stagingRoot)
        val target = stagingRoot.resolve("themes-${UUID.randomUUID()}.apk")
        try {
            copyBounded(input, target)
            val archive = inspector.inspectArchive(target.toString())
            if (archive.packageName != installed.packageName) {
                throw ThemeManagerUpdateException(
                    "APK package ${archive.packageName} does not match installed ${installed.packageName}",
                )
            }
            val canonical = ThemeManagerContract.canonicalVersion(archive.versionName)
            if (canonical != ThemeManagerContract.RECOMMENDED_VERSION) {
                throw ThemeManagerUpdateException(
                    "APK version ${archive.versionName ?: "unknown"} is not ${ThemeManagerContract.RECOMMENDED_VERSION}",
                )
            }
            if (installed.signingCertificateSha256.isEmpty() || archive.signingCertificateSha256.isEmpty()) {
                throw ThemeManagerUpdateException("Could not verify Xiaomi signing certificates")
            }
            if (installed.signingCertificateSha256.intersect(archive.signingCertificateSha256).isEmpty()) {
                throw ThemeManagerUpdateException("APK signing certificate does not match the installed Theme Manager")
            }
            return VerifiedThemeManagerApk(
                stagedPath = target,
                packageName = archive.packageName,
                versionName = archive.versionName ?: canonical,
                versionCode = archive.versionCode,
                sha256 = sha256(target),
            )
        } catch (error: ThemeManagerUpdateException) {
            Files.deleteIfExists(target)
            throw error
        } catch (error: Exception) {
            Files.deleteIfExists(target)
            throw ThemeManagerUpdateException("Could not inspect selected APK", error)
        }
    }

    fun installVerifiedDowngrade(apk: VerifiedThemeManagerApk): RootUpdateResult {
        val before = inspector.inspect()
        if (!before.installed || before.packageName != apk.packageName) {
            throw ThemeManagerUpdateException("Installed Theme Manager changed after APK verification")
        }
        if (!Files.isRegularFile(apk.stagedPath) || sha256(apk.stagedPath) != apk.sha256) {
            throw ThemeManagerUpdateException("Verified APK changed before installation")
        }
        val rechecked = inspector.inspectArchive(apk.stagedPath.toString())
        if (rechecked.packageName != apk.packageName ||
            ThemeManagerContract.canonicalVersion(rechecked.versionName) != ThemeManagerContract.RECOMMENDED_VERSION ||
            before.signingCertificateSha256.intersect(rechecked.signingCertificateSha256).isEmpty()
        ) {
            throw ThemeManagerUpdateException("APK no longer passes package, version, and signature checks")
        }

        val temporaryPath = "/data/local/tmp/mtzstudio-theme-manager-${UUID.randomUUID()}.apk"
        val command = RootInstallCommand.forStagedApk(apk.stagedPath.toString(), temporaryPath)
        val execution = try {
            commandRunner.run(command, 180)
        } catch (error: Exception) {
            throw ThemeManagerUpdateException("Could not start a privileged installation", error)
        }
        return try {
            val after = inspector.inspect()
            val installedTarget = after.packageName == apk.packageName && after.isRecommended
            RootUpdateResult(
                success = execution.exitCode == 0 && installedTarget,
                message = when {
                    execution.exitCode != 0 -> "Root package manager rejected the downgrade"
                    !installedTarget -> "Package manager returned success but the target version is not active"
                    else -> "Theme Manager ${ThemeManagerContract.RECOMMENDED_VERSION} is now active"
                },
                commandOutput = execution.output,
                authorizationSource = execution.authorizationSource,
                installedAfter = after,
            )
        } catch (error: ThemeManagerUpdateException) {
            throw error
        } catch (error: Exception) {
            throw ThemeManagerUpdateException("Root installation failed", error)
        }
    }

    fun discard(apk: VerifiedThemeManagerApk) {
        Files.deleteIfExists(apk.stagedPath)
    }

    private fun copyBounded(input: InputStream, target: Path) {
        Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count.toLong() > maxApkBytes - total) {
                    throw ThemeManagerUpdateException("Selected APK exceeds the size limit")
                }
                output.write(buffer, 0, count)
                total += count
            }
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal object RootInstallCommand {
    fun forStagedApk(sourcePath: String, temporaryPath: String): String {
        require(sourcePath.isNotBlank()) { "Source path must not be blank" }
        require(temporaryPath.startsWith("/data/local/tmp/mtzstudio-theme-manager-")) {
            "Temporary path must stay in the app-owned install namespace"
        }
        val source = shellQuote(sourcePath)
        val temporary = shellQuote(temporaryPath)
        return buildString {
            append("/system/bin/cp ").append(source).append(' ').append(temporary)
            append(" && /system/bin/chmod 0644 ").append(temporary)
            append(" && /system/bin/pm install -r -d --user 0 ").append(temporary)
            append("; result=${'$'}?; /system/bin/rm -f ").append(temporary)
            append("; exit ${'$'}result")
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}

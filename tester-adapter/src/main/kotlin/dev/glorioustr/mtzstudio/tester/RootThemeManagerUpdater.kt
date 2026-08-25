package dev.glorioustr.mtzstudio.tester

import android.content.Context
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    val installedAfter: InstalledThemeManager,
)

class ThemeManagerUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RootThemeManagerUpdater(
    context: Context,
    private val inspector: ThemeManagerInspector = ThemeManagerInspector(context),
    private val maxApkBytes: Long = 256L * 1024 * 1024,
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

        val size = Files.size(apk.stagedPath)
        val command = RootInstallCommand.forApkBytes(size)
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (error: Exception) {
            throw ThemeManagerUpdateException("Could not start root shell", error)
        }
        return try {
            Files.newInputStream(apk.stagedPath).use { source ->
                process.outputStream.use { destination -> source.copyTo(destination) }
            }
            val finished = process.waitFor(180, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw ThemeManagerUpdateException("Root package installation timed out")
            }
            val output = process.inputStream.bufferedReader().use { it.readText().takeLast(8_192) }.trim()
            val after = inspector.inspect()
            val installedTarget = after.packageName == apk.packageName && after.isRecommended
            RootUpdateResult(
                success = process.exitValue() == 0 && installedTarget,
                message = when {
                    process.exitValue() != 0 -> "Root package manager rejected the downgrade"
                    !installedTarget -> "Package manager returned success but the target version is not active"
                    else -> "Theme Manager ${ThemeManagerContract.RECOMMENDED_VERSION} is now active"
                },
                commandOutput = output,
                installedAfter = after,
            )
        } catch (error: ThemeManagerUpdateException) {
            throw error
        } catch (error: Exception) {
            process.destroyForcibly()
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
    fun forApkBytes(size: Long): String {
        require(size > 0) { "APK size must be positive" }
        return "pm install -r -d --user 0 -S $size -"
    }
}

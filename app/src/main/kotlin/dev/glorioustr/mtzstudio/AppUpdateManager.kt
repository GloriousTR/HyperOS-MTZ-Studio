package dev.glorioustr.mtzstudio

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal object AppUpdateScheduler {
    private const val PERIODIC_JOB_ID = 33010
    private const val IMMEDIATE_JOB_ID = 33011
    private const val PERIOD_MS = 12L * 60 * 60 * 1000

    fun schedule(context: Context, checkNow: Boolean = true) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val component = ComponentName(context, AppUpdateJobService::class.java)
        scheduler.schedule(
            JobInfo.Builder(PERIODIC_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build(),
        )
        if (checkNow) {
            scheduler.schedule(
                JobInfo.Builder(IMMEDIATE_JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(2_000)
                    .build(),
            )
        }
    }
}

internal enum class AppUpdatePhase { IDLE, CHECKING, UP_TO_DATE, READY, ERROR }

internal data class AppUpdateState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val version: String? = null,
    val error: String? = null,
    val eventId: Long = 0L,
)

internal object AppUpdateStore {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    fun update(state: AppUpdateState) { mutableState.value = state }
}

class AppUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: kotlinx.coroutines.Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob = scope.launch {
            runCatching { AppUpdateManager(applicationContext).checkAndDownload() }
                .onFailure {
                    AppUpdateStore.update(AppUpdateState(AppUpdatePhase.ERROR, error = it.message ?: it::class.simpleName, eventId = System.currentTimeMillis()))
                    LiveDiagnosticsRecorder.get(applicationContext).record("app_update_failed", "Güncelleme kontrolü tamamlanamadı", error = it)
                }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        return true
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

internal class AppUpdateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val updateDirectory = context.filesDir.toPath().resolve("updates")
    private val apkPath = updateDirectory.resolve("MTZ_Studio_update.apk")

    fun checkAndDownload(force: Boolean = false) {
        AppUpdateStore.update(AppUpdateState(AppUpdatePhase.CHECKING, eventId = System.currentTimeMillis()))
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("last_check", 0L) < CHECK_INTERVAL_MS) {
            if (!restoreReadyState()) AppUpdateStore.update(AppUpdateState())
            return
        }
        prefs.edit().putLong("last_check", now).apply()
        val release = JSONObject(readUrl(RELEASE_API))
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            AppUpdateStore.update(AppUpdateState(AppUpdatePhase.UP_TO_DATE, eventId = now))
            return
        }
        val tag = release.getString("tag_name").removePrefix("v")
        if (!isNewer(tag, BuildConfig.VERSION_NAME)) {
            AppUpdateStore.update(AppUpdateState(AppUpdatePhase.UP_TO_DATE, eventId = now))
            return
        }
        val assets = release.getJSONArray("assets")
        var apkUrl: String? = null
        var checksumUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.getString("name")
            val url = asset.getString("browser_download_url")
            if (name.startsWith("MTZ_Studio_") && name.endsWith(".apk")) apkUrl = url
            if (name.startsWith("MTZ_Studio_") && name.endsWith(".apk.sha256")) checksumUrl = url
        }
        val downloadUrl = requireNotNull(apkUrl) { "Release APK is missing" }
        val expectedHash = requireNotNull(checksumUrl) { "Release checksum is missing" }
            .let(::readUrl).trim().substringBefore(' ').lowercase()
        require(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "Release checksum is invalid" }
        Files.createDirectories(updateDirectory)
        val staging = updateDirectory.resolve("download.tmp")
        try {
            downloadBounded(downloadUrl, staging)
            val actualHash = sha256(staging)
            require(actualHash == expectedHash) { "Downloaded APK checksum does not match" }
            Files.move(staging, apkPath, StandardCopyOption.REPLACE_EXISTING)
            verifyDownloadedApk()
            prefs.edit().putString("ready_version", tag).apply()
            AppUpdateStore.update(AppUpdateState(AppUpdatePhase.READY, version = tag, eventId = System.currentTimeMillis()))
            showReadyNotification(tag)
            LiveDiagnosticsRecorder.get(context).record("app_update_ready", "İmzalı uygulama güncellemesi indirildi", mapOf("version" to tag, "sha256" to actualHash))
        } finally {
            Files.deleteIfExists(staging)
        }
    }

    fun restoreReadyState(): Boolean {
        val version = prefs.getString("ready_version", null)?.takeIf { isNewer(it, BuildConfig.VERSION_NAME) }
            ?: return false
        val valid = runCatching { verifyDownloadedApk() }.getOrDefault(false)
        if (!valid) return false
        AppUpdateStore.update(AppUpdateState(AppUpdatePhase.READY, version = version, eventId = System.currentTimeMillis()))
        return true
    }

    fun verifyDownloadedApk(): Boolean {
        if (!Files.isRegularFile(apkPath)) return false
        val archive = (if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(apkPath.toString(), PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkPath.toString(), PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkPath.toString(), PackageManager.GET_SIGNATURES)
        }) ?: return false
        require(archive.packageName == context.packageName) { "Update package name does not match" }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else {
            @Suppress("DEPRECATION") archive.versionCode.toLong()
        }
        require(archiveVersionCode > BuildConfig.VERSION_CODE) { "Update is not newer than the installed app" }
        val installed = requireNotNull(if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }) { "Installed package information is unavailable" }
        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            installed.signatures.orEmpty()
        }.map { sha256(it.toByteArray()) }.toSet()
        val actual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            archive.signatures.orEmpty()
        }.map { sha256(it.toByteArray()) }.toSet()
        require(expected.isNotEmpty() && expected == actual) { "Update signing certificate does not match" }
        return true
    }

    fun installIntent(): Intent {
        check(verifyDownloadedApk()) { "No verified update is ready" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkPath.toFile())
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun showReadyNotification(version: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.app_update_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, UpdateInstallActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.app_update_ready_title, version))
                .setContentText(context.getString(R.string.app_update_ready_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun readUrl(url: String): String = open(url).let { connection ->
        try {
            check(connection.responseCode in 200..299) { "Update server returned HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun downloadBounded(url: String, target: Path) {
        val connection = open(url)
        try {
            check(connection.responseCode in 200..299) { "APK download returned HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                Files.newOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_APK_BYTES) { "Update APK is larger than allowed" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally { connection.disconnect() }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 20_000
        readTimeout = 120_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "HyperOS-MTZ-Studio/${BuildConfig.VERSION_NAME}")
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val comparison = (remoteParts.getOrNull(index) ?: 0).compareTo(currentParts.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/GloriousTR/HyperOS-MTZ-Studio/releases/latest"
        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
        private const val MAX_APK_BYTES = 200L * 1024 * 1024
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4403
    }
}

class UpdateInstallActivity : Activity() {
    private var openedSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        continueInstall()
    }

    override fun onResume() {
        super.onResume()
        if (openedSettings) continueInstall()
    }

    private fun continueInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            if (!openedSettings) {
                openedSettings = true
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }
            return
        }
        runCatching { startActivity(AppUpdateManager(applicationContext).installIntent()) }
        finish()
    }
}

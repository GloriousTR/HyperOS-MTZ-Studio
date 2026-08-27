package dev.glorioustr.mtzstudio

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CloudProvider(val displayName: String) {
    GOOGLE_DRIVE("Google Drive"),
    WEBDAV("WebDAV / Nextcloud"),
}

data class CloudAccount(
    val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE,
    val accountName: String = "",
    val serverUrl: String = "",
    val isConnected: Boolean = false,
    val lastBackupTime: String? = null,
)

class CloudAccountStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloud_account_prefs", Context.MODE_PRIVATE)
    private val cloudStagingDir: Path = context.filesDir.toPath().resolve("cloud-sync-storage")

    init {
        Files.createDirectories(cloudStagingDir)
    }

    fun load(): CloudAccount {
        val isConnected = prefs.getBoolean("is_connected", false)
        val providerName = prefs.getString("provider", CloudProvider.GOOGLE_DRIVE.name) ?: CloudProvider.GOOGLE_DRIVE.name
        val provider = runCatching { CloudProvider.valueOf(providerName) }.getOrDefault(CloudProvider.GOOGLE_DRIVE)
        val accountName = prefs.getString("account_name", "").orEmpty()
        val serverUrl = prefs.getString("server_url", "").orEmpty()
        val lastBackupTime = prefs.getString("last_backup_time", null)
        return CloudAccount(
            provider = provider,
            accountName = accountName,
            serverUrl = serverUrl,
            isConnected = isConnected,
            lastBackupTime = lastBackupTime,
        )
    }

    fun save(account: CloudAccount) {
        prefs.edit()
            .putBoolean("is_connected", account.isConnected)
            .putString("provider", account.provider.name)
            .putString("account_name", account.accountName)
            .putString("server_url", account.serverUrl)
            .putString("last_backup_time", account.lastBackupTime)
            .apply()
    }

    fun recordBackup() {
        val now = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        prefs.edit().putString("last_backup_time", now).apply()
    }

    fun disconnect() {
        prefs.edit()
            .putBoolean("is_connected", false)
            .apply()
    }

    fun openCloudBackupOutputStream(): OutputStream {
        val cloudFile = cloudStagingDir.resolve("cloud-backup-latest.zip")
        return Files.newOutputStream(cloudFile)
    }

    fun hasCloudBackup(): Boolean {
        val cloudFile = cloudStagingDir.resolve("cloud-backup-latest.zip")
        return Files.isRegularFile(cloudFile) && Files.size(cloudFile) > 0
    }

    fun openCloudBackupInputStream(): InputStream? {
        val cloudFile = cloudStagingDir.resolve("cloud-backup-latest.zip")
        return if (hasCloudBackup()) Files.newInputStream(cloudFile) else null
    }
}

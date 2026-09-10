package dev.glorioustr.mtzstudio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class CloudProvider(val displayName: String) {
    GOOGLE_DRIVE("Google Drive"),
    WEBDAV("WebDAV / Nextcloud"),
}

data class CloudAccount(
    val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE,
    val accountName: String = "",
    val serverUrl: String = "",
    val password: String = "",
    val treeUri: String = "",
    val oauthBacked: Boolean = false,
    val isConnected: Boolean = false,
    val lastBackupTime: String? = null,
)

/** Real remote storage backed by Google Drive app data or a WebDAV endpoint. */
class CloudAccountStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloud_account_prefs", Context.MODE_PRIVATE)

    fun load(): CloudAccount {
        val provider = runCatching {
            CloudProvider.valueOf(prefs.getString("provider", CloudProvider.GOOGLE_DRIVE.name).orEmpty())
        }.getOrDefault(CloudProvider.GOOGLE_DRIVE)
        val treeUri = prefs.getString("tree_uri", "").orEmpty()
        val serverUrl = prefs.getString("server_url", "").orEmpty()
        val connected = prefs.getBoolean("is_connected", false) && when (provider) {
            CloudProvider.GOOGLE_DRIVE -> prefs.getBoolean("drive_oauth", false)
            CloudProvider.WEBDAV -> serverUrl.startsWith("https://", ignoreCase = true)
        }
        return CloudAccount(
            provider = provider,
            accountName = prefs.getString("account_name", "").orEmpty(),
            serverUrl = serverUrl,
            password = decryptSecret(prefs.getString("password_encrypted", "").orEmpty()),
            treeUri = treeUri,
            oauthBacked = prefs.getBoolean("drive_oauth", false),
            isConnected = connected,
            lastBackupTime = prefs.getString("last_backup_time", null),
        )
    }

    fun save(account: CloudAccount) {
        prefs.edit()
            .putBoolean("is_connected", account.isConnected)
            .putString("provider", account.provider.name)
            .putString("account_name", account.accountName)
            .putString("server_url", account.serverUrl)
            .putString("password_encrypted", encryptSecret(account.password))
            .putString("tree_uri", account.treeUri)
            .putBoolean("drive_oauth", account.oauthBacked)
            .putString("last_backup_time", account.lastBackupTime)
            .apply()
    }

    fun recordBackup() {
        val now = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        prefs.edit().putString("last_backup_time", now).apply()
    }

    fun disconnect() {
        prefs.edit().putBoolean("is_connected", false).apply()
    }

    fun uploadBackup(source: Path) {
        val account = requireConnected()
        when (account.provider) {
            CloudProvider.GOOGLE_DRIVE -> GoogleDriveAppDataStore(context).upload(source)
            CloudProvider.WEBDAV -> uploadWebDav(account, source)
        }
    }

    fun downloadBackup(target: Path): Boolean {
        val account = requireConnected()
        Files.createDirectories(target.parent)
        return when (account.provider) {
            CloudProvider.GOOGLE_DRIVE -> GoogleDriveAppDataStore(context).download(target)
            CloudProvider.WEBDAV -> downloadWebDav(account, target)
        }
    }

    private fun requireConnected(): CloudAccount = load().also {
        check(it.isConnected) { "Cloud account permission is missing; reconnect cloud storage" }
    }

    private fun uploadWebDav(account: CloudAccount, source: Path) {
        val connection = openWebDav(account, "PUT").apply {
            doOutput = true
            setFixedLengthStreamingMode(Files.size(source))
        }
        try {
            connection.outputStream.use { output -> Files.newInputStream(source).use { it.copyTo(output) } }
            check(connection.responseCode in 200..299) { "WebDAV upload failed: HTTP ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadWebDav(account: CloudAccount, target: Path): Boolean {
        val connection = openWebDav(account, "GET")
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> false
                in 200..299 -> {
                    connection.inputStream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                    Files.size(target) > 0
                }
                else -> error("WebDAV download failed: HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openWebDav(account: CloudAccount, method: String): HttpURLConnection {
        val encodedName = URLEncoder.encode(BACKUP_FILE_NAME, Charsets.UTF_8.name()).replace("+", "%20")
        return (URL(account.serverUrl.trimEnd('/') + "/" + encodedName).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty(
                "Authorization",
                "Basic " + Base64.encodeToString(
                    "${account.accountName}:${account.password}".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                ),
            )
        }
    }

    private fun encryptSecret(secret: String): String {
        if (secret.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptSecret(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, bytes.copyOfRange(0, GCM_IV_BYTES)),
            )
            String(cipher.doFinal(bytes.copyOfRange(GCM_IV_BYTES, bytes.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    companion object {
        const val BACKUP_FILE_NAME = "hyperos-mtz-studio-backup-latest.zip"
        private const val KEY_ALIAS = "mtz_studio_cloud_secret"
        private const val GCM_IV_BYTES = 12
    }
}

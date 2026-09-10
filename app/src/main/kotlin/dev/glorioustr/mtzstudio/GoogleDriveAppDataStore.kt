package dev.glorioustr.mtzstudio

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Google Drive storage that is private to MTZ Studio and follows the Google account. */
internal class GoogleDriveAppDataStore(private val context: Context) {
    fun upload(source: Path) {
        val token = accessToken()
        val existingId = findBackupId(token)
        // Upload the new archive first so a failed transfer never destroys the last good backup.
        val newId = createBackup(token, source)
        if (existingId != null && existingId != newId) deleteBackup(token, existingId)
    }

    fun download(target: Path): Boolean {
        val token = accessToken()
        val fileId = findBackupId(token) ?: return false
        Files.createDirectories(target.parent)
        val connection = open(
            "https://www.googleapis.com/drive/v3/files/${encode(fileId)}?alt=media",
            "GET",
            token,
        )
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> false
                in 200..299 -> {
                    connection.inputStream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                    Files.size(target) > 0L
                }
                else -> driveError(connection, "Google Drive download")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun accessToken(): String {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(authorizationRequest()))
        check(!result.hasResolution()) { "Google Drive permission needs to be renewed in MTZ Studio" }
        return result.accessToken?.takeIf(String::isNotBlank)
            ?: error("Google Drive did not return an access token")
    }

    private fun findBackupId(token: String): String? {
        val query = "name='${CloudAccountStore.BACKUP_FILE_NAME}' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&q=${encode(query)}&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name)"
        val connection = open(url, "GET", token)
        return try {
            if (connection.responseCode !in 200..299) driveError(connection, "Google Drive backup search")
            val files = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("files")
            if (files.length() == 0) null else files.getJSONObject(0).getString("id")
        } finally {
            connection.disconnect()
        }
    }

    private fun createBackup(token: String, source: Path): String {
        val boundary = "mtz-studio-${System.currentTimeMillis()}"
        val metadata = JSONObject()
            .put("name", CloudAccountStore.BACKUP_FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val prefix = ("--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(Charsets.UTF_8)
        val middle = ("\r\n--$boundary\r\n" +
            "Content-Type: application/zip\r\n\r\n").toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val connection = open(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            "POST",
            token,
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setFixedLengthStreamingMode(prefix.size.toLong() + metadata.size + middle.size + Files.size(source) + suffix.size)
        }
        try {
            BufferedOutputStream(connection.outputStream).use { output ->
                output.write(prefix)
                output.write(metadata)
                output.write(middle)
                Files.newInputStream(source).use { it.copyTo(output) }
                output.write(suffix)
            }
            if (connection.responseCode !in 200..299) driveError(connection, "Google Drive upload")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getString("id")
        } finally {
            connection.disconnect()
        }
    }

    private fun deleteBackup(token: String, fileId: String) {
        val connection = open(
            "https://www.googleapis.com/drive/v3/files/${encode(fileId)}",
            "DELETE",
            token,
        )
        try {
            if (connection.responseCode !in 200..299 && connection.responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                driveError(connection, "Google Drive old-backup cleanup")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 180_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    private fun driveError(connection: HttpURLConnection, operation: String): Nothing {
        val details = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            ?.take(500)
            ?.takeIf(String::isNotBlank)
        error("$operation failed: HTTP ${connection.responseCode}${details?.let { " — $it" }.orEmpty()}")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    companion object {
        fun authorizationRequest(): AuthorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER), Scope(Scopes.EMAIL)))
            .build()

        fun accountName(result: AuthorizationResult): String =
            @Suppress("DEPRECATION")
            (result.toGoogleSignInAccount()?.email ?: "Google Drive")
    }
}

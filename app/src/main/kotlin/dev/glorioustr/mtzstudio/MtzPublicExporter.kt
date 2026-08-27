package dev.glorioustr.mtzstudio

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object MtzPublicExporter {

    fun exportToPublicDownloads(context: Context, sourcePath: Path, themeName: String): File? {
        val sanitized = themeName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "custom_theme" }
        val filename = if (sanitized.endsWith(".mtz", ignoreCase = true)) sanitized else "$sanitized.mtz"

        // 1. Direct file write to /sdcard/Download/MTZ Studio
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val studioDir = File(downloadDir, "MTZ Studio")
            if (!studioDir.exists()) {
                studioDir.mkdirs()
            }
            val targetFile = File(studioDir, filename)
            Files.copy(sourcePath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

            // Index in system media/download provider so file managers and Theme Manager see it immediately
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("application/zip", "application/octet-stream", "*/*"),
                null,
            )
            return targetFile
        } catch (_: Throwable) {
            // Fallback for scoped storage on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/MTZ Studio")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            Files.copy(sourcePath, out)
                        }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        return File("/storage/emulated/0/Download/MTZ Studio/$filename")
                    }
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
        return null
    }
}

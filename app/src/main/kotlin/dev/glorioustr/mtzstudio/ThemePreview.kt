package dev.glorioustr.mtzstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import dev.glorioustr.mtzstudio.core.ComponentCategory
import dev.glorioustr.mtzstudio.library.LibraryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile
import java.nio.file.Path

internal enum class ThemePreviewPurpose {
    PERSONALIZATION,
    GALLERY,
}

@Composable
internal fun ThemePreview(
    theme: LibraryTheme,
    category: ComponentCategory? = null,
    purpose: ThemePreviewPurpose = ThemePreviewPurpose.PERSONALIZATION,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, theme.id, category, purpose) {
        value = withContext(Dispatchers.IO) { decodePreview(theme, category, purpose) }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = theme.archive.metadata?.name ?: theme.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Text(
            text = if (
                category == ComponentCategory.FONT ||
                theme.archive.components.any { it.category == ComponentCategory.FONT }
            ) "Aa" else (theme.archive.metadata?.name ?: theme.displayName).take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.displayMedium,
        )
    }
}

@Composable
internal fun ThemeWallpaperPreview(
    theme: LibraryTheme,
    lockScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, theme.id, lockScreen) {
        value = withContext(Dispatchers.IO) { decodeWallpaper(theme, lockScreen) }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun UriImagePreview(
    uri: android.net.Uri,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                var sample = 1
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, bounds)
                    while (bounds.outWidth / sample > TARGET_DIMENSION || bounds.outHeight / sample > TARGET_DIMENSION) sample *= 2
                }
                context.contentResolver.openInputStream(uri)?.use { freshStream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeStream(freshStream, null, options)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun DeviceThemePreview(
    path: Path?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            path?.toFile()?.takeIf { it.isFile && it.length() > 0L }?.let { file ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Text(
            text = title.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.displayMedium,
        )
    }
}

private fun decodePreview(
    theme: LibraryTheme,
    category: ComponentCategory?,
    purpose: ThemePreviewPurpose,
): Bitmap? = runCatching {
    ZipFile(theme.archive.source.toFile()).use { zip ->
        val path = previewEntryPath(theme, category, purpose) ?: return@use null
        decodeZipBitmap(zip, path)
    }
}.getOrNull()

private fun decodeWallpaper(theme: LibraryTheme, lockScreen: Boolean): Bitmap? = runCatching {
    val path = wallpaperEntryPath(theme, lockScreen) ?: return@runCatching null
    ZipFile(theme.archive.source.toFile()).use { zip -> decodeZipBitmap(zip, path) }
}.getOrNull()

internal fun hasThemeWallpaper(theme: LibraryTheme, lockScreen: Boolean): Boolean =
    wallpaperEntryPath(theme, lockScreen) != null

private fun wallpaperEntryPath(theme: LibraryTheme, lockScreen: Boolean): String? {
    val requestedPath = if (lockScreen) {
        "wallpaper/default_lock_wallpaper.jpg"
    } else {
        "wallpaper/default_wallpaper.jpg"
    }
    return theme.archive.entries.firstOrNull {
        !it.directory && it.path.equals(requestedPath, ignoreCase = true)
    }?.path
}

private fun decodeZipBitmap(zip: ZipFile, path: String): Bitmap? {
    val entry = zip.getEntry(path) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
        bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION
    ) return null
    var sample = 1
    while (bounds.outWidth / sample > TARGET_DIMENSION || bounds.outHeight / sample > TARGET_DIMENSION) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
}

/** True only when the selected component has a real image that can be shown in its picker. */
internal fun hasThemePreview(theme: LibraryTheme, category: ComponentCategory): Boolean =
    previewEntryPath(theme, category, ThemePreviewPurpose.PERSONALIZATION) != null

private fun previewEntryPath(
    theme: LibraryTheme,
    category: ComponentCategory?,
    purpose: ThemePreviewPurpose,
): String? {
    val images = theme.archive.entries.asSequence()
        .filter { entry ->
            !entry.directory && entry.expandedBytes in 1..MAX_PREVIEW_BYTES && isImagePath(entry.path)
        }
        .map { it.path }
        .toList()
    val byLowerPath = images.associateBy(String::lowercase)
    val candidates = if (category == null && purpose == ThemePreviewPurpose.GALLERY) {
        if (theme.includeInThemeGallery) {
            listOf("preview/mtz_studio_generated.jpg", "wallpaper/default_wallpaper.jpg")
        } else {
            listOf(
                "wallpaper/default_wallpaper.jpg",
                "preview/preview_launcher_0.jpg",
                "preview/preview_launcher_1.jpg",
                "preview/preview_wallpaper_0.jpg",
            )
        }
    } else {
        previewCandidates(category)
    }
    candidates.forEach { candidate ->
        byLowerPath[candidate.lowercase()]?.let { return it }
    }
    val keywords = previewKeywords(category)
    if (keywords.isNotEmpty()) {
        images.firstOrNull { path ->
            path.startsWith("preview/", ignoreCase = true) &&
                keywords.any { keyword -> path.contains(keyword, ignoreCase = true) }
        }?.let { return it }
    }
    return null
}

private fun isImagePath(path: String): Boolean =
    path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) ||
        path.endsWith(".png", true) || path.endsWith(".webp", true)

private fun previewCandidates(category: ComponentCategory?): List<String> = when (category) {
    ComponentCategory.LOCKSCREEN -> listOf(
        "preview/preview_lockscreen_0.jpg",
        "preview/preview_lockscreen_1.jpg",
        "wallpaper/default_lock_wallpaper.jpg",
    )
    ComponentCategory.WALLPAPER -> listOf(
        "wallpaper/default_wallpaper.jpg",
        "preview/preview_launcher_0.jpg",
    )
    ComponentCategory.ICONS -> listOf(
        "preview/preview_icons_0.jpg",
        "preview/preview_icons_1.jpg",
        "preview/preview_launcher_0.jpg",
    )
    ComponentCategory.SYSTEM_UI -> listOf(
        "preview/preview_statusbar_0.jpg",
        "preview/preview_statusbar_0.png",
        "preview/preview_statusbar_1.jpg",
        "preview/preview_notification_0.jpg",
        "preview/preview_launcher_0.jpg",
    )
    ComponentCategory.CONTACTS -> listOf(
        "preview/preview_contact_0.jpg",
        "preview/preview_contact_0.png",
        "preview/preview_contact_1.jpg",
        "preview/preview_dialer_0.jpg",
        "preview/preview_dialer_0.png",
        "preview/preview_call_0.jpg",
        "preview/preview_launcher_2.jpg",
        "preview/preview_launcher_1.jpg",
        "preview/preview_launcher_0.jpg",
    )
    ComponentCategory.MMS -> listOf(
        "preview/preview_mms_0.jpg",
        "preview/preview_mms_0.png",
        "preview/preview_mms_1.jpg",
        "preview/preview_sms_0.jpg",
        "preview/preview_sms_0.png",
        "preview/preview_message_0.jpg",
        "preview/preview_launcher_1.jpg",
        "preview/preview_launcher_2.jpg",
        "preview/preview_launcher_0.jpg",
    )
    ComponentCategory.LAUNCHER -> listOf("preview/preview_launcher_0.jpg", "preview/preview_launcher_1.jpg")
    ComponentCategory.AOD -> listOf(
        "preview/preview_miwallpaper_0.jpg",
        "preview/preview_lockscreen_0.jpg",
    )
    ComponentCategory.FONT -> listOf(
        "preview/preview_fonts_0.jpg",
        "preview/preview_fonts_0.png",
    )
    ComponentCategory.FRAMEWORK,
    ComponentCategory.SYSTEM_UI_PLUGIN,
    ComponentCategory.RINGTONE,
    ComponentCategory.OTHER -> emptyList()
    null -> listOf(
        "preview/preview_lockscreen_0.jpg",
        "preview/preview_launcher_0.jpg",
        "wallpaper/default_lock_wallpaper.jpg",
        "wallpaper/default_wallpaper.jpg",
        "preview/preview_icons_0.jpg",
    )
}

private fun previewKeywords(category: ComponentCategory?): List<String> = when (category) {
    ComponentCategory.ICONS -> listOf("icon", "launcher")
    ComponentCategory.LOCKSCREEN -> listOf("lockscreen", "lock_style")
    ComponentCategory.WALLPAPER -> listOf("wallpaper", "launcher")
    ComponentCategory.SYSTEM_UI -> listOf("statusbar", "notification", "controlcenter", "systemui", "launcher")
    ComponentCategory.CONTACTS -> listOf("contact", "dialer", "call", "phone", "launcher")
    ComponentCategory.MMS -> listOf("mms", "sms", "message", "launcher")
    ComponentCategory.LAUNCHER -> listOf("launcher", "home")
    ComponentCategory.AOD -> listOf("aod", "miwallpaper", "lockscreen")
    ComponentCategory.FONT -> listOf("font")
    ComponentCategory.FRAMEWORK,
    ComponentCategory.SYSTEM_UI_PLUGIN,
    ComponentCategory.RINGTONE,
    ComponentCategory.OTHER -> emptyList()
    null -> emptyList()
}

private const val MAX_PREVIEW_BYTES = 16L * 1024 * 1024
private const val MAX_DIMENSION = 16_384
private const val TARGET_DIMENSION = 1_200

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

@Composable
internal fun ThemePreview(
    theme: LibraryTheme,
    category: ComponentCategory? = null,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, theme.id, category) {
        value = withContext(Dispatchers.IO) { decodePreview(theme, category) }
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

private fun decodePreview(theme: LibraryTheme, category: ComponentCategory?): Bitmap? = runCatching {
    ZipFile(theme.archive.source.toFile()).use { zip ->
        val path = previewEntryPath(theme, category) ?: return@use null
        val entry = zip.getEntry(path) ?: return@use null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION) {
            return@use null
        }
        var sample = 1
        while (bounds.outWidth / sample > TARGET_DIMENSION || bounds.outHeight / sample > TARGET_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
    }
}.getOrNull()

/** True only when the selected component has a real image that can be shown in its picker. */
internal fun hasThemePreview(theme: LibraryTheme, category: ComponentCategory): Boolean =
    previewEntryPath(theme, category) != null

private fun previewEntryPath(theme: LibraryTheme, category: ComponentCategory?): String? {
    val images = theme.archive.entries.asSequence()
        .filter { entry ->
            !entry.directory && entry.expandedBytes in 1..MAX_PREVIEW_BYTES && isImagePath(entry.path)
        }
        .map { it.path }
        .toList()
    val byLowerPath = images.associateBy(String::lowercase)
    previewCandidates(category).forEach { candidate ->
        byLowerPath[candidate.lowercase()]?.let { return it }
    }
    val keywords = previewKeywords(category)
    if (keywords.isNotEmpty()) {
        images.firstOrNull { path ->
            path.startsWith("preview/", ignoreCase = true) &&
                keywords.any { keyword -> path.contains(keyword, ignoreCase = true) }
        }?.let { return it }
    }
    return if (category == null) images.firstOrNull { it.startsWith("preview/", ignoreCase = true) } else null
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
    ComponentCategory.SYSTEM_UI -> listOf("statusbar", "launcher")
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

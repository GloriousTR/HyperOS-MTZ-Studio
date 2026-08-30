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
import dev.glorioustr.mtzstudio.core.ThemeVisualPolicy
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
    previewPath: String? = null,
) {
    val bitmap by produceState<Bitmap?>(null, theme.id, category, purpose, previewPath) {
        value = withContext(Dispatchers.IO) {
            if (previewPath == null) decodePreview(theme, category, purpose) else runCatching {
                if (previewPath !in ThemeVisualPolicy.imagePaths(theme.archive.entries)) return@runCatching null
                ZipFile(theme.archive.source.toFile()).use { decodeZipBitmap(it, previewPath) }
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
                contentDescription = theme.archive.metadata?.name ?: theme.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (previewPath == null) ContentScale.Crop else ContentScale.Fit,
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
        previewEntryPaths(theme, category, purpose).firstNotNullOfOrNull { path ->
            runCatching { decodeZipBitmap(zip, path) }.getOrNull()
        }
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

/** A category-specific image or the theme's default cover can be shown. */
internal fun hasThemePreview(theme: LibraryTheme, category: ComponentCategory): Boolean =
    previewEntryPaths(theme, category, ThemePreviewPurpose.PERSONALIZATION).isNotEmpty()

private fun previewEntryPaths(
    theme: LibraryTheme,
    category: ComponentCategory?,
    purpose: ThemePreviewPurpose,
): List<String> {
    if (category != null) return ThemeVisualPolicy.categoryWithFallback(theme.archive.entries, category)
    val candidates = when {
        purpose == ThemePreviewPurpose.GALLERY && theme.includeInThemeGallery ->
            listOf("preview/mtz_studio_generated.jpg", "wallpaper/default_wallpaper.jpg")
        purpose == ThemePreviewPurpose.GALLERY -> listOf(
            "wallpaper/default_wallpaper.jpg", "preview/preview_launcher_0.jpg",
            "preview/preview_launcher_1.jpg", "preview/preview_wallpaper_0.jpg",
        )
        else -> listOf(
            "preview/preview_lockscreen_0.jpg", "preview/preview_launcher_0.jpg",
            "wallpaper/default_lock_wallpaper.jpg", "wallpaper/default_wallpaper.jpg",
            "preview/preview_icons_0.jpg",
        )
    }
    return ThemeVisualPolicy.previewPaths(theme.archive.entries, candidates, emptyList())
}

private const val MAX_DIMENSION = 16_384
private const val TARGET_DIMENSION = 1_200

package dev.glorioustr.mtzstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import dev.glorioustr.mtzstudio.library.LibraryTheme
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile

/** Builds the persistent cover stored inside themes created by MTZ Studio. */
internal object GeneratedThemePreviewFactory {
    fun create(themeName: String, wallpaperBytes: ByteArray?): ByteArray? = runCatching {
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val wallpaper = wallpaperBytes?.let(::decodeWallpaper)

        if (wallpaper != null) {
            val destination = Rect(0, 0, WIDTH, HEIGHT)
            canvas.drawBitmap(wallpaper, centerCropRect(wallpaper), destination, paint)
            wallpaper.recycle()
        } else {
            paint.shader = LinearGradient(
                0f,
                0f,
                WIDTH.toFloat(),
                HEIGHT.toFloat(),
                intArrayOf(Color.rgb(31, 22, 72), Color.rgb(18, 105, 145), Color.rgb(8, 12, 24)),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
            paint.shader = null
        }

        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            250f,
            Color.argb(155, 0, 0, 0),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 260f, paint)
        paint.shader = LinearGradient(
            0f,
            650f,
            0f,
            HEIGHT.toFloat(),
            Color.TRANSPARENT,
            Color.argb(232, 0, 0, 0),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 620f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 31f
        canvas.drawText("12:45", 48f, 72f, paint)
        repeat(3) { index -> canvas.drawCircle(WIDTH - 54f - index * 23f, 60f, 6f, paint) }

        paint.color = Color.argb(225, 255, 255, 255)
        paint.textSize = 20f
        paint.letterSpacing = 0.18f
        canvas.drawText("HYPEROS  •  MTZ STUDIO", 48f, HEIGHT - 150f, paint)

        paint.letterSpacing = 0f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = fittedTextSize(themeName, paint, WIDTH - 96f, 48f, 30f)
        paint.color = Color.WHITE
        canvas.drawText(themeName, 48f, HEIGHT - 82f, paint)

        ByteArrayOutputStream().use { bytes ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 92, bytes))
            output.recycle()
            bytes.toByteArray()
        }
    }.getOrNull()

    private fun decodeWallpaper(bytes: ByteArray): Bitmap? {
        if (bytes.size.toLong() !in 1..MAX_SOURCE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION
        ) return null
        var sample = 1
        while (bounds.outWidth / sample > WIDTH * 2 || bounds.outHeight / sample > HEIGHT * 2) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun centerCropRect(bitmap: Bitmap): Rect {
        val sourceAspect = bitmap.width.toFloat() / bitmap.height
        val targetAspect = WIDTH.toFloat() / HEIGHT
        return if (sourceAspect > targetAspect) {
            val width = (bitmap.height * targetAspect).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - width) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + width).coerceAtMost(bitmap.width), bitmap.height)
        } else {
            val height = (bitmap.width / targetAspect).toInt().coerceAtLeast(1)
            val top = ((bitmap.height - height) / 2).coerceAtLeast(0)
            Rect(0, top, bitmap.width, (top + height).coerceAtMost(bitmap.height))
        }
    }

    private fun fittedTextSize(text: String, paint: Paint, maxWidth: Float, initial: Float, minimum: Float): Float {
        var size = initial
        paint.textSize = size
        while (size > minimum && paint.measureText(text) > maxWidth) {
            size -= 1f
            paint.textSize = size
        }
        return size
    }

    private const val WIDTH = 720
    private const val HEIGHT = 1_200
    private const val MAX_DIMENSION = 16_384
    private const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
}

internal fun readHomePreviewSource(theme: LibraryTheme): ByteArray? = runCatching {
    val candidates = listOf(
        "wallpaper/default_wallpaper.jpg",
        "preview/preview_launcher_0.jpg",
    )
    val byLowerPath = theme.archive.entries.asSequence()
        .filterNot { it.directory }
        .associateBy { it.path.lowercase() }
    val path = candidates.firstNotNullOfOrNull { byLowerPath[it]?.path } ?: return@runCatching null
    ZipFile(theme.archive.source.toFile()).use { zip ->
        val entry = zip.getEntry(path) ?: return@use null
        if (entry.size !in 1..(32L * 1024 * 1024)) return@use null
        zip.getInputStream(entry).use { it.readBytes() }
    }
}.getOrNull()

package dev.glorioustr.mtzstudio

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.nio.file.Files
import androidx.annotation.StringRes
import java.nio.file.StandardCopyOption

internal enum class AppAppearance(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    SYSTEM(R.string.appearance_system_title, R.string.appearance_system_desc),
    LIGHT(R.string.appearance_light_title, R.string.appearance_light_desc),
    DARK(R.string.appearance_dark_title, R.string.appearance_dark_desc),
    AMOLED(R.string.appearance_amoled_title, R.string.appearance_amoled_desc),
}

internal enum class AppContentStyle(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    DEFAULT(R.string.style_default_title, R.string.style_default_desc),
    MATERIAL_YOU(R.string.style_material_you_title, R.string.style_material_you_desc),
    LIQUID_GLASS(R.string.style_liquid_glass_title, R.string.style_liquid_glass_desc),
}

internal val LocalAppContentStyle = staticCompositionLocalOf { AppContentStyle.DEFAULT }

internal class AppearanceStore(context: Context) {
    private val settingsRoot = context.filesDir.toPath().resolve("settings")
    private val appearanceFile = settingsRoot.resolve("appearance.txt")
    private val contentStyleFile = settingsRoot.resolve("content-style.txt")

    fun load(): AppAppearance = runCatching {
        val stored = Files.newBufferedReader(appearanceFile).use { reader -> reader.readLine() }
        AppAppearance.valueOf(stored.trim())
    }.getOrDefault(AppAppearance.SYSTEM)

    fun save(appearance: AppAppearance) {
        saveValue(appearanceFile, ".appearance.tmp", appearance.name)
    }

    fun loadContentStyle(): AppContentStyle = runCatching {
        val stored = Files.newBufferedReader(contentStyleFile).use { reader -> reader.readLine() }
        AppContentStyle.valueOf(stored.trim())
    }.getOrDefault(AppContentStyle.DEFAULT)

    fun saveContentStyle(style: AppContentStyle) {
        saveValue(contentStyleFile, ".content-style.tmp", style.name)
    }

    private fun saveValue(destination: java.nio.file.Path, temporaryName: String, value: String) {
        Files.createDirectories(settingsRoot)
        val temporary = settingsRoot.resolve(temporaryName)
        Files.newBufferedWriter(temporary).use { writer -> writer.write(value) }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}

@Composable
internal fun StudioAppTheme(
    appearance: AppAppearance,
    contentStyle: AppContentStyle,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appearance) {
        AppAppearance.SYSTEM -> systemDark
        AppAppearance.LIGHT -> false
        AppAppearance.DARK, AppAppearance.AMOLED -> true
    }
    val context = LocalContext.current
    val colors = when (contentStyle) {
        AppContentStyle.DEFAULT -> when {
            appearance == AppAppearance.AMOLED -> AmoledColors
            useDark -> DarkColors
            else -> LightColors
        }
        AppContentStyle.MATERIAL_YOU -> {
            val dynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (useDark) {
                DarkColors
            } else {
                LightColors
            }
            if (appearance == AppAppearance.AMOLED) {
                dynamic.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceDim = Color.Black,
                    surfaceContainerLowest = Color.Black,
                )
            } else {
                dynamic
            }
        }
        AppContentStyle.LIQUID_GLASS -> liquidGlassColors(useDark, appearance == AppAppearance.AMOLED)
    }
    val shapes = when (contentStyle) {
        AppContentStyle.DEFAULT -> Shapes()
        AppContentStyle.MATERIAL_YOU -> Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(32.dp),
            extraLarge = RoundedCornerShape(40.dp),
        )
        AppContentStyle.LIQUID_GLASS -> Shapes(
            extraSmall = RoundedCornerShape(18.dp),
            small = RoundedCornerShape(24.dp),
            medium = RoundedCornerShape(30.dp),
            large = RoundedCornerShape(40.dp),
            extraLarge = RoundedCornerShape(48.dp),
        )
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !useDark
            isAppearanceLightNavigationBars = !useDark
        }
    }
    MaterialTheme(colorScheme = colors, shapes = shapes) {
        CompositionLocalProvider(LocalAppContentStyle provides contentStyle) {
            val backgroundModifier = if (contentStyle == AppContentStyle.LIQUID_GLASS) {
                val gradient = when {
                    appearance == AppAppearance.AMOLED -> listOf(Color.Black, Color.Black)
                    useDark -> listOf(Color(0xFF08111F), Color(0xFF16213A), Color(0xFF241B38))
                    else -> listOf(Color(0xFFE7F6FF), Color(0xFFF5ECFF), Color(0xFFDDEBFF))
                }
                Modifier.background(Brush.linearGradient(gradient))
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize().then(backgroundModifier)) {
                content()
            }
        }
    }
}

private fun liquidGlassColors(useDark: Boolean, amoled: Boolean) = if (useDark) {
    darkColorScheme(
        primary = Color(0xFFBBD9FF),
        onPrimary = Color(0xFF10243D),
        secondary = Color(0xFFD9C4FF),
        onSecondary = Color(0xFF2B1C3E),
        background = if (amoled) Color.Black else Color(0xFF08111F),
        onBackground = Color(0xFFF5F7FF),
        surface = Color(0x662B3448),
        onSurface = Color(0xFFF5F7FF),
        surfaceVariant = Color(0x77404A62),
        onSurfaceVariant = Color(0xFFD6DEEF),
        surfaceContainer = Color(0x66333D52),
        surfaceContainerLow = Color(0x552B3448),
        surfaceContainerHigh = Color(0x77414A60),
        surfaceContainerHighest = Color(0x884B5469),
        outline = Color(0x99D7E7FF),
        outlineVariant = Color(0x55D7E7FF),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF315E91),
        onPrimary = Color.White,
        secondary = Color(0xFF6B538A),
        onSecondary = Color.White,
        background = Color(0xFFE7F6FF),
        onBackground = Color(0xFF172034),
        surface = Color(0x66FFFFFF),
        onSurface = Color(0xFF172034),
        surfaceVariant = Color(0x88FFFFFF),
        onSurfaceVariant = Color(0xFF354158),
        surfaceContainer = Color(0x66FFFFFF),
        surfaceContainerLow = Color(0x55FFFFFF),
        surfaceContainerHigh = Color(0x88FFFFFF),
        surfaceContainerHighest = Color(0xAAFFFFFF),
        outline = Color(0x996D7890),
        outlineVariant = Color(0x5576869E),
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4EB3),
    secondary = Color(0xFF675A7A),
    background = Color(0xFFFFF7FF),
    surface = Color(0xFFFFF7FF),
    surfaceVariant = Color(0xFFE9E1EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD2BBFF),
    secondary = Color(0xFFD0C1DD),
    background = Color(0xFF121117),
    surface = Color(0xFF121117),
    surfaceVariant = Color(0xFF302D35),
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFFD2BBFF),
    secondary = Color(0xFFD0C1DD),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
)

package dev.glorioustr.mtzstudio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.glorioustr.mtzstudio.core.Hashing
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.tester.PrivilegedCommandRunner
import dev.glorioustr.mtzstudio.tester.ThemeManagerContract
import java.util.UUID

data class PreparedThemeApply(
    val themeId: String,
    val themeName: String,
    val stagedPath: String,
    val intent: Intent,
)

class ThemeApplyCoordinator(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
) {
    fun prepare(theme: LibraryTheme): PreparedThemeApply {
        check(Hashing.sha256(theme.archive.source) == theme.archive.sha256) { "Tema kaynağı doğrulama sonrası değişmiş" }
        val stagedPath = "$THEME_MANAGER_STAGING_ROOT/${UUID.randomUUID()}.mtz"
        val command = buildString {
            append("/system/bin/mkdir -p ").append(shellQuote(THEME_MANAGER_STAGING_ROOT))
            append(" && /system/bin/cp ").append(shellQuote(theme.archive.source.toString()))
            append(' ').append(shellQuote(stagedPath))
            append(" && /system/bin/chmod 777 ").append(shellQuote(stagedPath))
            append(" && /system/bin/mkdir -p /sdcard/MIUI/theme")
            append(" && /system/bin/cp ").append(shellQuote(theme.archive.source.toString()))
            append(" /sdcard/MIUI/theme/${UUID.randomUUID()}.mtz 2>/dev/null || true")
        }
        val result = commandRunner.run(command, 120)
        check(result.exitCode == 0) { "Tema, Tema Yöneticisine hazırlanamadı: ${result.output.takeLast(500)}" }

        val request = ThemeManagerContract.legacyTesterRequest(
            stagedPath,
            context.packageName,
        )
        val intent = Intent(request.action).apply {
            component = ComponentName(THEME_MANAGER_PACKAGE, request.componentClassName)
            request.stringExtras.forEach(::putExtra)
            request.longExtras.forEach(::putExtra)
        }
        check(intent.resolveActivity(context.packageManager) != null) { "Uyumlu Tema Yöneticisi tester aktivitesi bulunamadı" }
        return PreparedThemeApply(theme.id.value, theme.archive.metadata?.name ?: theme.displayName, stagedPath, intent)
    }

    fun cleanup(prepared: PreparedThemeApply) {
        // Retain file temporarily or clean asynchronously so background services aren't starved during application
        commandRunner.run("/system/bin/rm -f ${shellQuote(prepared.stagedPath)}", 30)
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private companion object {
        const val THEME_MANAGER_PACKAGE = "com.android.thememanager"
        const val THEME_MANAGER_STAGING_ROOT = "/sdcard/Android/data/com.android.thememanager/files/mtzstudio"
    }
}

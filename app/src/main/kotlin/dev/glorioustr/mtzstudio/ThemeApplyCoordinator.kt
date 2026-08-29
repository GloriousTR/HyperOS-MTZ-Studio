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
    val protocol: ThemeApplyProtocol = ThemeApplyProtocol.LEGACY_TESTER,
)

enum class ThemeApplyProtocol {
    LEGACY_TESTER,
    THEME_MANAGER_10_8_BRIDGE,
}

class ThemeApplyCoordinator(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
) {
    fun prepare(theme: LibraryTheme): PreparedThemeApply {
        check(Hashing.sha256(theme.archive.source) == theme.archive.sha256) { "Tema kaynağı doğrulama sonrası değişmiş" }
        return if (ThemeManagerContract.behavior(installedThemeManagerVersion()) ==
            dev.glorioustr.mtzstudio.tester.ThemeManagerBehavior.MODDED_PERSISTENT_IMPORT
        ) {
            prepareThemeManager10_8(theme)
        } else {
            prepareLegacyTester(theme)
        }
    }

    private fun prepareThemeManager10_8(theme: LibraryTheme): PreparedThemeApply {
        ensureThemeManager10_8BridgeScope()
        val stagedPath = "$THEME_MANAGER_10_8_DOWNLOAD_ROOT/${UUID.randomUUID()}.mtz"
        val command = buildString {
            append("/system/bin/mkdir -p ").append(shellQuote(THEME_MANAGER_10_8_DOWNLOAD_ROOT))
            append(" && /system/bin/cp ").append(shellQuote(theme.archive.source.toString()))
            append(' ').append(shellQuote(stagedPath))
            append(" && /system/bin/chmod 0644 ").append(shellQuote(stagedPath))
        }
        val result = commandRunner.run(command, 120)
        check(result.exitCode == 0) { "Tema, Temalar 10.8 içe aktarma alanına hazırlanamadı: ${result.output.takeLast(500)}" }

        val intent = Intent(ThemeManagerBridgeContract.ACTION_APPLY_10_8).apply {
            component = ComponentName(THEME_MANAGER_PACKAGE, THEME_MANAGER_10_8_LOCAL_ACTIVITY)
            putExtra("REQUEST_RESOURCE_CODE", "theme")
            putExtra(ThemeManagerBridgeContract.EXTRA_THEME_PATH, stagedPath)
            putExtra(ThemeManagerBridgeContract.EXTRA_THEME_SHA256, theme.archive.sha256)
        }
        check(intent.resolveActivity(context.packageManager) != null) { "Temalar 10.8 yerel tema ekranı bulunamadı" }
        return PreparedThemeApply(
            themeId = theme.id.value,
            themeName = theme.archive.metadata?.name ?: theme.displayName,
            stagedPath = stagedPath,
            intent = intent,
            protocol = ThemeApplyProtocol.THEME_MANAGER_10_8_BRIDGE,
        )
    }

    private fun prepareLegacyTester(theme: LibraryTheme): PreparedThemeApply {
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
        return PreparedThemeApply(
            themeId = theme.id.value,
            themeName = theme.archive.metadata?.name ?: theme.displayName,
            stagedPath = stagedPath,
            intent = intent,
            protocol = ThemeApplyProtocol.LEGACY_TESTER,
        )
    }

    fun cleanup(prepared: PreparedThemeApply) {
        // Retain file temporarily or clean asynchronously so background services aren't starved during application
        commandRunner.run("/system/bin/rm -f ${shellQuote(prepared.stagedPath)}", 30)
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun installedThemeManagerVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(THEME_MANAGER_PACKAGE, 0).versionName
    }.getOrNull()

    private fun isImportBridgeReady(): Boolean {
        val command = BRIDGE_MARKER_FILES.joinToString(separator = " || ") { path ->
            "/system/bin/grep -q '^ready=true' ${shellQuote(path)} 2>/dev/null"
        }
        return commandRunner.run(command, 30).exitCode == 0
    }

    private fun ensureThemeManager10_8BridgeScope() {
        val vectorCommand = buildString {
            append("if [ -x ").append(shellQuote(VECTOR_CLI)).append(" ]; then ")
            append(shellQuote(VECTOR_CLI)).append(" modules enable ").append(shellQuote(context.packageName))
            append(" >/dev/null 2>&1 || exit 41; ")
            append(shellQuote(VECTOR_CLI)).append(" scope rm ").append(shellQuote(context.packageName))
            append(" system/0 >/dev/null 2>&1 || true; ")
            append("if ! ").append(shellQuote(VECTOR_CLI)).append(" scope ls ").append(shellQuote(context.packageName))
            append(" | /system/bin/grep -q ").append(shellQuote(THEME_MANAGER_PACKAGE)).append("; then ")
            append(shellQuote(VECTOR_CLI)).append(" scope add ").append(shellQuote(context.packageName))
            append(' ').append(shellQuote("$THEME_MANAGER_PACKAGE/0"))
            append(" >/dev/null 2>&1 || exit 42; ")
            append("fi; /system/bin/rm -f ")
            BRIDGE_MARKER_FILES.forEach { path -> append(shellQuote(path)).append(' ') }
            append("; /system/bin/am force-stop ").append(shellQuote(THEME_MANAGER_PACKAGE))
            append("; exit 0; else exit 127; fi")
        }
        val vectorResult = commandRunner.run(vectorCommand, 30)
        if (vectorResult.exitCode == 0) return
        check(isImportBridgeReady()) {
            "Temalar 10.8 uyumluluk köprüsü etkinleştirilemedi. Vector/LSPosed içinde MTZ Studio için yalnızca Temalar kapsamını etkinleştir."
        }
    }

    private companion object {
        const val THEME_MANAGER_PACKAGE = "com.android.thememanager"
        const val THEME_MANAGER_STAGING_ROOT = "/sdcard/Android/data/com.android.thememanager/files/mtzstudio"
        const val THEME_MANAGER_10_8_DOWNLOAD_ROOT =
            "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.download"
        const val THEME_MANAGER_10_8_LOCAL_ACTIVITY =
            "com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity"
        const val VECTOR_CLI = "/data/adb/modules/zygisk_vector/cli"
        val BRIDGE_MARKER_FILES = listOf(
            "/data/system/theme/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/data/com.android.thememanager/files/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/user/0/com.android.thememanager/files/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/local/tmp/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
        )
    }
}

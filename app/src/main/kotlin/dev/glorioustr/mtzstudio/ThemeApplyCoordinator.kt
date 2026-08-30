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
    val manualImportPath: String? = null,
    val operation: ThemeManagerOperation = ThemeManagerOperation.APPLY,
    val themeManagerLocalId: String? = null,
)

enum class ThemeManagerOperation { APPLY, IMPORT_ONLY, DELETE }

enum class ThemeApplyProtocol {
    LEGACY_TESTER,
    MODERN_THEME_MANAGER_BRIDGE,
    MODERN_THEME_MANAGER_MANUAL_IMPORT,
}

class ThemeApplyCoordinator(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
) {
    private val diagnostics get() = LiveDiagnosticsRecorder.get(context)

    fun prepare(theme: LibraryTheme, themeManagerLocalId: String? = null): PreparedThemeApply {
        diagnostics.record("apply_source_check", "Tema kaynağı ve sürüm kontrol ediliyor", mapOf(
            "theme" to theme.displayName, "version" to installedThemeManagerVersion(), "localId" to themeManagerLocalId,
        ))
        check(Hashing.sha256(theme.archive.source) == theme.archive.sha256) { "Tema kaynağı doğrulama sonrası değişmiş" }
        diagnostics.record("apply_hash_verified", "Tema kaynak SHA-256 doğrulaması başarılı")
        return if (ThemeManagerContract.behavior(installedThemeManagerVersion()) ==
            dev.glorioustr.mtzstudio.tester.ThemeManagerBehavior.MODERN_NATIVE_LIBRARY
        ) {
            if (themeManagerLocalId != null) prepareModernExistingTheme(theme, themeManagerLocalId)
            else prepareModernImport(theme, ThemeManagerOperation.APPLY)
        } else {
            prepareLegacyTester(theme)
        }
    }

    fun prepareModernImportOnly(theme: LibraryTheme): PreparedThemeApply =
        prepareModernImport(theme, ThemeManagerOperation.IMPORT_ONLY)

    private fun prepareModernImport(theme: LibraryTheme, operation: ThemeManagerOperation): PreparedThemeApply {
        diagnostics.record("modern_import_prepare", "Yerleşik MTZ aktarımı hazırlanıyor", mapOf("operation" to operation, "theme" to theme.displayName))
        val themeName = theme.archive.metadata?.name ?: theme.displayName
        val manualImportFile = checkNotNull(
            MtzPublicExporter.exportToPublicDownloads(context, theme.archive.source, themeName),
        ) { "Tema, Xiaomi Temalar içe aktarma akışı için İndirilenler/MTZ Studio klasörüne hazırlanamadı" }
        val bridgeReady = ensureModernThemeManagerBridgeScope()
        val stagedPath = "$THEME_MANAGER_MODERN_DOWNLOAD_ROOT/${UUID.randomUUID()}.mtz"
        val command = buildString {
            append("/system/bin/mkdir -p ").append(shellQuote(THEME_MANAGER_MODERN_DOWNLOAD_ROOT))
            append(" && /system/bin/cp ").append(shellQuote(theme.archive.source.toString()))
            append(' ').append(shellQuote(stagedPath))
            append(" && /system/bin/chmod 0644 ").append(shellQuote(stagedPath))
        }
        val result = runRecorded("modern_staging", command, 120)
        check(result.exitCode == 0) { "Tema, Temalar 10.8 içe aktarma alanına hazırlanamadı: ${result.output.takeLast(500)}" }

        val intent = Intent().apply {
            component = ComponentName(THEME_MANAGER_PACKAGE, THEME_MANAGER_MODERN_LOCAL_ACTIVITY)
            putExtra("REQUEST_RESOURCE_CODE", "theme")
            if (bridgeReady) {
                action = if (operation == ThemeManagerOperation.IMPORT_ONLY) {
                    ThemeManagerBridgeContract.ACTION_IMPORT_MODERN
                } else {
                    ThemeManagerBridgeContract.ACTION_APPLY_MODERN
                }
                putExtra(ThemeManagerBridgeContract.EXTRA_THEME_PATH, stagedPath)
                putExtra(ThemeManagerBridgeContract.EXTRA_THEME_SHA256, theme.archive.sha256)
            }
        }
        check(intent.resolveActivity(context.packageManager) != null) { "Xiaomi Temalar yerel tema ekranı bulunamadı" }
        return PreparedThemeApply(
            themeId = theme.id.value,
            themeName = themeName,
            stagedPath = stagedPath,
            intent = intent,
            protocol = if (bridgeReady) {
                ThemeApplyProtocol.MODERN_THEME_MANAGER_BRIDGE
            } else {
                ThemeApplyProtocol.MODERN_THEME_MANAGER_MANUAL_IMPORT
            },
            manualImportPath = manualImportFile.absolutePath,
            operation = operation,
        )
    }

    private fun prepareModernExistingTheme(theme: LibraryTheme, localId: String): PreparedThemeApply {
        require(localId.matches(SAFE_LOCAL_ID)) { "Geçersiz Tema Mağazası yerel kimliği" }
        check(ensureModernThemeManagerBridgeScope()) { "Tema Mağazası köprüsü etkin değil" }
        val themeName = theme.archive.metadata?.name ?: theme.displayName
        val intent = modernThemeManagerIntent(ThemeManagerBridgeContract.ACTION_APPLY_EXISTING).apply {
            putExtra(ThemeManagerBridgeContract.EXTRA_THEME_LOCAL_ID, localId)
        }
        return PreparedThemeApply(
            themeId = theme.id.value,
            themeName = themeName,
            stagedPath = "",
            intent = intent,
            protocol = ThemeApplyProtocol.MODERN_THEME_MANAGER_BRIDGE,
            operation = ThemeManagerOperation.APPLY,
            themeManagerLocalId = localId,
        )
    }

    fun prepareModernDelete(theme: LibraryTheme, localId: String): PreparedThemeApply {
        diagnostics.record("native_delete_prepare", "Yerleşik tema silme hazırlanıyor", mapOf("theme" to theme.displayName, "localId" to localId))
        require(localId.matches(SAFE_LOCAL_ID)) { "Geçersiz Tema Mağazası yerel kimliği" }
        check(ensureModernThemeManagerBridgeScope()) { "Tema Mağazası köprüsü etkin değil" }
        return PreparedThemeApply(
            themeId = theme.id.value,
            themeName = theme.archive.metadata?.name ?: theme.displayName,
            stagedPath = "",
            intent = modernThemeManagerIntent(ThemeManagerBridgeContract.ACTION_DELETE_EXISTING).apply {
                putExtra(ThemeManagerBridgeContract.EXTRA_THEME_LOCAL_ID, localId)
            },
            protocol = ThemeApplyProtocol.MODERN_THEME_MANAGER_BRIDGE,
            operation = ThemeManagerOperation.DELETE,
            themeManagerLocalId = localId,
        )
    }

    fun prepareModernManualFallback(prepared: PreparedThemeApply): PreparedThemeApply {
        check(prepared.manualImportPath != null) { "Yerleşik içe aktarma için hazırlanmış MTZ bulunamadı" }
        return prepared.copy(
            intent = Intent().apply {
                component = ComponentName(THEME_MANAGER_PACKAGE, THEME_MANAGER_MODERN_LOCAL_ACTIVITY)
                putExtra("REQUEST_RESOURCE_CODE", "theme")
            },
            protocol = ThemeApplyProtocol.MODERN_THEME_MANAGER_MANUAL_IMPORT,
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
        val result = runRecorded("legacy_staging", command, 120)
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
        if (prepared.stagedPath.isNotBlank()) {
            runRecorded("staging_cleanup", "/system/bin/rm -f ${shellQuote(prepared.stagedPath)}", 30)
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun installedThemeManagerVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(THEME_MANAGER_PACKAGE, 0).versionName
    }.getOrNull()

    private fun isImportBridgeReady(): Boolean {
        val command = BRIDGE_MARKER_FILES.joinToString(separator = " || ") { path ->
            "/system/bin/grep -q '^ready=true' ${shellQuote(path)} 2>/dev/null"
        }
        return runRecorded("bridge_marker_check", command, 30).exitCode == 0
    }

    private fun ensureModernThemeManagerBridgeScope(): Boolean {
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
        val vectorResult = runRecorded("vector_scope_prepare", vectorCommand, 30)
        if (vectorResult.exitCode == 0) return true
        return isImportBridgeReady()
    }

    private fun modernThemeManagerIntent(actionName: String): Intent = Intent(actionName).apply {
        component = ComponentName(THEME_MANAGER_PACKAGE, THEME_MANAGER_MODERN_LOCAL_ACTIVITY)
        putExtra("REQUEST_RESOURCE_CODE", "theme")
        check(resolveActivity(context.packageManager) != null) { "Temalar yerel tema ekranı bulunamadı" }
    }

    private fun runRecorded(stage: String, command: String, timeoutSeconds: Long) = try {
        diagnostics.record("privileged_step_started", "Yetkili işlem başladı", mapOf("stage" to stage))
        commandRunner.run(command, timeoutSeconds).also { result ->
            diagnostics.record("privileged_step_result", "Yetkili işlem sonucu", mapOf(
                "stage" to stage, "exitCode" to result.exitCode,
                "output" to result.output.takeLast(1500),
            ))
        }
    } catch (error: Exception) {
        diagnostics.record("privileged_step_failed", "Yetkili işlem tamamlanamadı", mapOf("stage" to stage), error)
        throw error
    }

    /** Only inspect recent crash records for the two packages involved in this request. */
    fun captureFailureDiagnostics(startedAt: Long) {
        runCatching {
            val since = java.time.Instant.ofEpochMilli(maxOf(startedAt, System.currentTimeMillis() - 300_000))
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", java.util.Locale.US))
            val result = commandRunner.run("logcat -b crash -d -v threadtime -T ${shellQuote(since)} | tail -n 200", 10)
            val lines = result.output.lines()
            val ownPids = lines.filter { it.contains("Process: $THEME_MANAGER_PACKAGE,") || it.contains("Process: ${context.packageName},") }
                .mapNotNull { Regex("PID: (\\d+)").find(it)?.groupValues?.get(1) }.toSet()
            val relevant = lines.filter { line -> line.trim().split(Regex("\\s+"), limit = 5).getOrNull(2) in ownPids }
            diagnostics.record("host_crash_check", if (relevant.isEmpty()) "Bu işlem aralığında ilgili çökme kaydı bulunamadı; iptal veya yanıtsız dönüş olabilir" else "Temalar işlemine ait çökme ayrıntısı bulundu",
                mapOf("crash" to relevant.joinToString("\n").takeLast(6000)))
        }.onFailure { diagnostics.record("host_crash_check_unavailable", "Ek çökme kaydı okunamadı", error = it) }
    }

    private companion object {
        const val THEME_MANAGER_PACKAGE = "com.android.thememanager"
        const val THEME_MANAGER_STAGING_ROOT = "/sdcard/Android/data/com.android.thememanager/files/mtzstudio"
        const val THEME_MANAGER_MODERN_DOWNLOAD_ROOT =
            "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.download"
        const val THEME_MANAGER_MODERN_LOCAL_ACTIVITY =
            "com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity"
        const val VECTOR_CLI = "/data/adb/modules/zygisk_vector/cli"
        val SAFE_LOCAL_ID = Regex("[A-Za-z0-9._-]{1,128}")
        val BRIDGE_MARKER_FILES = listOf(
            "/data/system/theme/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/data/com.android.thememanager/files/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/user/0/com.android.thememanager/files/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/local/tmp/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
        )
    }
}

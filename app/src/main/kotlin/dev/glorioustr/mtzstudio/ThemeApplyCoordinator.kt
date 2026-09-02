package dev.glorioustr.mtzstudio

import android.content.ComponentName
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
    ROOTLESS_MANUAL_IMPORT,
    ROOTLESS_LEGACY_TESTER,
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

    /**
     * Rootless hand-off: retain a public MTZ copy and open the best public Theme Manager surface.
     * No claim is made that Xiaomi accepted or applied the file; the user completes the import.
     */
    fun prepareRootlessManualImport(theme: LibraryTheme): PreparedThemeApply {
        check(Hashing.sha256(theme.archive.source) == theme.archive.sha256) {
            "Tema kaynağı doğrulama sonrası değişmiş"
        }
        val themeName = theme.archive.metadata?.name ?: theme.displayName
        val publicFile = checkNotNull(
            MtzPublicExporter.exportToPublicDownloads(context, theme.archive.source, themeName),
        ) { "MTZ, İndirilenler/MTZ Studio klasörüne kaydedilemedi" }
        val installedVersion = installedThemeManagerVersion()
        val behavior = ThemeManagerContract.behavior(installedVersion)
        val legacyIntent = if (behavior ==
            dev.glorioustr.mtzstudio.tester.ThemeManagerBehavior.LOCAL_THEME_IMPORT
        ) {
            val request = ThemeManagerContract.legacyTesterRequest(publicFile.absolutePath, context.packageName)
            Intent(request.action).apply {
                component = ComponentName(THEME_MANAGER_PACKAGE, request.componentClassName)
                request.stringExtras.forEach(::putExtra)
                request.longExtras.forEach(::putExtra)
            }.takeIf { it.resolveActivity(context.packageManager) != null }
        } else null
        diagnostics.record(
            "rootless_manual_handoff",
            if (legacyIntent != null) {
                "MTZ rootsuz Global içe aktarma için Temalar tester geçidine hazırlandı"
            } else {
                "MTZ rootsuz elle içe aktarma için hazırlandı"
            },
            mapOf(
                "theme" to themeName,
                "file" to publicFile.name,
                "themeManagerVersion" to installedVersion,
                "themeManagerBehavior" to behavior.name,
                "legacyTesterResolved" to (legacyIntent != null),
            ),
        )
        return PreparedThemeApply(
            themeId = theme.id.value,
            themeName = themeName,
            stagedPath = "",
            intent = legacyIntent ?: publicThemeManagerIntent(theme.archive.source),
            protocol = if (legacyIntent != null) {
                ThemeApplyProtocol.ROOTLESS_LEGACY_TESTER
            } else {
                ThemeApplyProtocol.ROOTLESS_MANUAL_IMPORT
            },
            manualImportPath = publicFile.absolutePath,
        )
    }

    fun requireModernPrivilegedAccess() {
        diagnostics.record("privileged_preflight_started", "Root veya Shizuku uyumlu yetki denetleniyor")
        val result = runRecorded("privileged_preflight", "id -u", 10)
        check(result.exitCode == 0 && result.output.lineSequence().firstOrNull()?.trim() == "0") {
            context.getString(R.string.privileged_access_unavailable)
        }
        diagnostics.record(
            "privileged_preflight_completed",
            "Yetkili işlem kanalı hazır",
            mapOf("source" to result.authorizationSource),
        )
    }

    private fun prepareModernImport(theme: LibraryTheme, operation: ThemeManagerOperation): PreparedThemeApply {
        diagnostics.record("modern_import_prepare", "Yerleşik MTZ aktarımı hazırlanıyor", mapOf("operation" to operation, "theme" to theme.displayName))
        val themeName = theme.archive.metadata?.name ?: theme.displayName
        val bridgeReady = ensureModernThemeManagerBridgeScope()
        val manualImportFile = checkNotNull(
            MtzPublicExporter.exportToPublicDownloads(context, theme.archive.source, themeName),
        ) { "Tema, Xiaomi Temalar içe aktarma akışı için İndirilenler/MTZ Studio klasörüne hazırlanamadı" }
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
                putExtra(ThemeManagerBridgeContract.EXTRA_THEME_NAME, themeName.take(180))
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

    fun nativeLibraryIntent(): Intent = Intent().apply {
        component = ComponentName(THEME_MANAGER_PACKAGE, THEME_MANAGER_MODERN_LOCAL_ACTIVITY)
        putExtra("REQUEST_RESOURCE_CODE", "theme")
        check(resolveActivity(context.packageManager) != null) { "Theme Manager local library is unavailable" }
    }

    private fun publicThemeManagerIntent(source: java.nio.file.Path): Intent {
        val sourceUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            source.toFile(),
        )
        val directFileOpen = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(sourceUri, "application/octet-stream")
            setPackage(THEME_MANAGER_PACKAGE)
            clipData = ClipData.newRawUri("MTZ", sourceUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (directFileOpen.resolveActivity(context.packageManager) != null) return directFileOpen

        val localLibrary = Intent().apply {
            component = ComponentName(THEME_MANAGER_PACKAGE, THEME_MANAGER_MODERN_LOCAL_ACTIVITY)
            putExtra("REQUEST_RESOURCE_CODE", "theme")
        }
        if (localLibrary.resolveActivity(context.packageManager) != null) return localLibrary
        return checkNotNull(context.packageManager.getLaunchIntentForPackage(THEME_MANAGER_PACKAGE)) {
            "Xiaomi Temalar uygulamasının açılabilir bir ekranı bulunamadı"
        }
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
            "( /system/bin/grep -q '^ready=true' ${shellQuote(path)} 2>/dev/null && " +
                "/system/bin/grep -q '^version=${BuildConfig.VERSION_CODE}$' ${shellQuote(path)} 2>/dev/null )"
        }
        return runRecorded("bridge_marker_check", command, 5).exitCode == 0
    }

    private fun ensureModernThemeManagerBridgeScope(): Boolean {
        // Scope approval only shows that Vector/LSPosed accepted the selection. It does not prove
        // the module was injected into the currently running Themes process. Require the marker
        // emitted by onPackageReady before dispatching a bridge request; otherwise use the manual
        // native-library screen instead of leaving the operation waiting for a callback forever.
        val scopeApproved = ThemeProtectionServiceClient.isModernBridgeScopeApproved()
        val markerReady = isImportBridgeReady()
        if (markerReady) {
            diagnostics.record(
                "bridge_runtime_ready",
                "Temalar köprüsü çalışan süreçte doğrulandı; ayarlar değiştirilmedi",
                mapOf("scopeApproved" to scopeApproved),
            )
            return true
        }
        diagnostics.record(
            "bridge_runtime_unavailable",
            "Temalar kapsamı seçili olsa da köprü çalışan süreçte hazır değil; güvenli el ile içe aktarma açılacak",
            mapOf("scopeApproved" to scopeApproved),
        )
        return false
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
            check(result.exitCode == 0) { "Crash log unavailable (${result.exitCode}): ${result.output}" }
            val lines = result.output.lines()
            val ownPids = lines.filter { it.contains("Process: $THEME_MANAGER_PACKAGE,") || it.contains("Process: ${context.packageName},") }
                .mapNotNull { Regex("PID: (\\d+)").find(it)?.groupValues?.get(1) }.toSet()
            val relevant = lines.filter { line -> line.trim().split(Regex("\\s+"), limit = 5).getOrNull(2) in ownPids }
            diagnostics.record("host_crash_check", if (relevant.isEmpty()) "Bu işlem aralığında ilgili çökme kaydı bulunamadı; iptal veya yanıtsız dönüş olabilir" else "Temalar işlemine ait çökme ayrıntısı bulundu",
                mapOf("crash" to relevant.joinToString("\n").takeLast(6000)))
        }.onFailure { diagnostics.record("host_crash_check_unavailable", "Ek çökme kaydı okunamadı", error = it) }
        runCatching {
            val filter = "thememanager|ThemeImport|ResourceImport|action_resource_import|MTZStudioProtection"
            val result = commandRunner.run(
                "logcat -b main -b system -d -v threadtime -t 1000 " +
                    "| /system/bin/grep -E ${shellQuote(filter)} | /system/bin/tail -n 250",
                30,
            )
            check(result.exitCode == 0) { "Import log unavailable (${result.exitCode}): ${result.output}" }
            diagnostics.record(
                "host_import_log",
                if (result.output.isBlank()) "Tema Yöneticisi içe aktarma ayrıntısı üretmedi" else "Tema Yöneticisi içe aktarma çalışma kaydı alındı",
                mapOf("exitCode" to result.exitCode, "log" to result.output.takeLast(6_000)),
            )
        }.onFailure {
            diagnostics.record("host_import_log_unavailable", "Tema Yöneticisi çalışma kaydı okunamadı", error = it)
        }
    }

    private companion object {
        const val THEME_MANAGER_PACKAGE = "com.android.thememanager"
        const val THEME_MANAGER_STAGING_ROOT = "/sdcard/Android/data/com.android.thememanager/files/mtzstudio"
        const val THEME_MANAGER_MODERN_DOWNLOAD_ROOT =
            "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.download"
        const val THEME_MANAGER_MODERN_LOCAL_ACTIVITY =
            "com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity"
        val SAFE_LOCAL_ID = Regex("[A-Za-z0-9._-]{1,128}")
        val BRIDGE_MARKER_FILES = listOf(
            "/data/system/theme/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/data/com.android.thememanager/files/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/user/0/com.android.thememanager/files/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
            "/data/local/tmp/${ThemeManagerBridgeContract.BRIDGE_MARKER}",
        )
    }
}

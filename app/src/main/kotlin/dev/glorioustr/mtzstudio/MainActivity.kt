package dev.glorioustr.mtzstudio

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import dev.glorioustr.mtzstudio.composer.ComponentSelection
import dev.glorioustr.mtzstudio.composer.CompositionMetadata
import dev.glorioustr.mtzstudio.composer.CompositionRequest
import dev.glorioustr.mtzstudio.composer.CompositionResult
import dev.glorioustr.mtzstudio.composer.CompositionSource
import dev.glorioustr.mtzstudio.composer.MtzComposer
import dev.glorioustr.mtzstudio.core.ComponentCategory
import dev.glorioustr.mtzstudio.core.ThemeId
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.library.StudioBackupManager
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import dev.glorioustr.mtzstudio.tester.RootThemeManagerUpdater
import dev.glorioustr.mtzstudio.tester.ThemeManagerInspector
import dev.glorioustr.mtzstudio.tester.ThemeManagerBehavior
import dev.glorioustr.mtzstudio.tester.ThemeManagerCapabilityProbe
import dev.glorioustr.mtzstudio.tester.StudioCapabilityPolicy
import dev.glorioustr.mtzstudio.shevery.PreferredPrivilegedCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

private const val MAX_SAFE_FULL_CATALOG_THEMES = 24

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = ThemeLibrary(applicationContext)
        val backupManager = StudioBackupManager(applicationContext)
        val composer = MtzComposer()
        val themeManagerInspector = ThemeManagerInspector(applicationContext)
        val privilegedRunner = PreferredPrivilegedCommandRunner(applicationContext)
        val themeManagerUpdater = RootThemeManagerUpdater(
            context = applicationContext,
            inspector = themeManagerInspector,
            commandRunner = privilegedRunner,
        )
        val themeApplyCoordinator = ThemeApplyCoordinator(applicationContext, privilegedRunner)
        val bakImporter = ThemeManagerBakImporter(applicationContext, privilegedRunner)
        val themeLanguageTool = ThemeLanguageTool(applicationContext, library)
        val deviceThemeImporter = DeviceThemeImporter(
            context = applicationContext,
            library = library,
            composer = composer,
            commandRunner = privilegedRunner,
        )
        val diagnostics = LiveDiagnosticsRecorder.get(applicationContext)
        val appearanceStore = AppearanceStore(applicationContext)
        val installedThemeManager = themeManagerInspector.inspect()
        val capabilityProfile = ThemeManagerCapabilityProbe(applicationContext).probe(installedThemeManager)
        val globalThemeProtectionRequired = installedThemeManager.requiresGlobalThemeProtection
        val modernThemeManagerMode = installedThemeManager.usesModernNativeLibrary
        diagnostics.record("activity_started", "Uygulama ekranı açıldı", mapOf(
            "themeManagerVersion" to installedThemeManager.versionName,
            "provider" to if (modernThemeManagerMode) "modern" else "global",
            "restored" to (savedInstanceState != null),
            "knownBehavior" to capabilityProfile.knownBehavior,
            "legacyTesterResolvable" to capabilityProfile.legacyTesterResolvable,
            "splitApkCount" to capabilityProfile.splitApkCount,
        ))
        setContent {
            var appearance by remember { mutableStateOf(appearanceStore.load()) }
            var contentStyle by remember { mutableStateOf(appearanceStore.loadContentStyle()) }
            StudioAppTheme(appearance, contentStyle) {
                StudioScreen(
                    library = library,
                    backupManager = backupManager,
                    composer = composer,
                    themeManagerInspector = themeManagerInspector,
                    themeManagerUpdater = themeManagerUpdater,
                    privilegedRunner = privilegedRunner,
                    themeApplyCoordinator = themeApplyCoordinator,
                    bakImporter = bakImporter,
                    themeLanguageTool = themeLanguageTool,
                    deviceThemeImporter = deviceThemeImporter,
                    diagnostics = diagnostics,
                    documentDiagnostics = ::documentDiagnostics,
                    openInput = contentResolver::openInputStream,
                    openOutput = { uri -> contentResolver.openOutputStream(uri) },
                    shareMtz = { share(it, "application/zip", "Export MTZ") },
                    shareDiagnostics = { share(it, "text/plain", "Export diagnostics") },
                    shareThemeManagerApk = { share(it, "application/vnd.android.package-archive", "Export Xiaomi Themes APK") },
                    appearance = appearance,
                    onAppearanceChange = { selected ->
                        appearanceStore.save(selected)
                        appearance = selected
                    },
                    contentStyle = contentStyle,
                    onContentStyleChange = { selected ->
                        appearanceStore.saveContentStyle(selected)
                        contentStyle = selected
                    },
                    globalThemeProtectionRequired = globalThemeProtectionRequired,
                    modernThemeManagerMode = modernThemeManagerMode,
                    themeManagerBehavior = installedThemeManager.behavior,
                )
            }
        }
    }

    private fun documentDiagnostics(uri: Uri): SelectedDocumentDiagnostics {
        var name: String? = null
        var size: Long? = null
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor: Cursor? = contentResolver.query(uri, columns, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !it.isNull(nameIndex)) name = it.getString(nameIndex)
                if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex).takeIf { value -> value >= 0 }
            }
        }
        return SelectedDocumentDiagnostics(name, size, uri.scheme, uri.authority)
    }

    private fun share(path: Path, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", path.toFile())
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(chooserTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, chooserTitle))
    }
}

internal data class UiSelection(
    val themeId: ThemeId,
    val category: ComponentCategory,
    val rootPath: String,
    val useDefault: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StudioScreen(
    library: ThemeLibrary,
    backupManager: StudioBackupManager,
    composer: MtzComposer,
    themeManagerInspector: ThemeManagerInspector,
    themeManagerUpdater: RootThemeManagerUpdater,
    privilegedRunner: PreferredPrivilegedCommandRunner,
    themeApplyCoordinator: ThemeApplyCoordinator,
    bakImporter: ThemeManagerBakImporter,
    themeLanguageTool: ThemeLanguageTool,
    deviceThemeImporter: DeviceThemeImporter,
    diagnostics: LiveDiagnosticsRecorder,
    documentDiagnostics: (Uri) -> SelectedDocumentDiagnostics,
    openInput: (Uri) -> InputStream?,
    openOutput: (Uri) -> OutputStream?,
    shareMtz: (Path) -> Unit,
    shareDiagnostics: (Path) -> Unit,
    shareThemeManagerApk: (Path) -> Unit,
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    contentStyle: AppContentStyle,
    onContentStyleChange: (AppContentStyle) -> Unit,
    globalThemeProtectionRequired: Boolean,
    modernThemeManagerMode: Boolean,
    themeManagerBehavior: ThemeManagerBehavior,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var themes by remember { mutableStateOf<List<LibraryTheme>>(emptyList()) }
    var checkingImportAccess by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(resources.getString(R.string.status_loading_library)) }
    val defaultCompositionName = stringResource(R.string.default_composition_name)
    var compositionName by rememberSaveable { mutableStateOf(defaultCompositionName) }
    var compositionMakerName by rememberSaveable { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<CompositionResult?>(null) }
    val selections = remember { mutableStateMapOf<ComponentCategory, UiSelection>() }
    val diagnosticState by diagnostics.state.collectAsState()
    val themeProtectionState by ThemeProtectionServiceClient.state.collectAsState()
    var destination by rememberSaveable { mutableStateOf(StudioDestination.HOME) }
    var returnDestination by rememberSaveable { mutableStateOf(StudioDestination.HOME) }
    var appMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var importExpanded by rememberSaveable { mutableStateOf(false) }
    var bakImporting by remember { mutableStateOf(false) }
    var pendingBakArchive by remember { mutableStateOf<ThemeManagerBakArchive?>(null) }
    var bakVersionMismatchAccepted by remember { mutableStateOf(false) }
    var pendingApplyTheme by remember { mutableStateOf<LibraryTheme?>(null) }
    var preparedApply by remember { mutableStateOf<PreparedThemeApply?>(null) }
    var themeOperationRunning by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf(resources.getString(R.string.status_no_backup_yet)) }
    val cloudAccountStore = remember { CloudAccountStore(context) }
    var cloudAccount by remember { mutableStateOf(cloudAccountStore.load()) }
    var customHomeWallpaperUri by rememberSaveable { mutableStateOf<String?>(null) }
    var customLockWallpaperUri by rememberSaveable { mutableStateOf<String?>(null) }
    var baseThemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var themeDeviceImportStatus by remember { mutableStateOf(resources.getString(R.string.device_import_idle)) }
    var fontDeviceImportStatus by remember { mutableStateOf(resources.getString(R.string.device_import_idle)) }
    var deviceImportRunning by remember { mutableStateOf(false) }
    val pauseCatalog = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val catalogProgress by deviceThemeImporter.catalogProgress.collectAsState()
    var availableDeviceThemes by remember { mutableStateOf<List<DeviceThemeSummary>>(emptyList()) }
    var isScanningDeviceThemes by remember { mutableStateOf(false) }
    var showDeviceThemePicker by remember { mutableStateOf(false) }
    var largeCatalogThemeCount by remember { mutableStateOf<Int?>(null) }
    var showThemeProtectionRestartDialog by remember { mutableStateOf(false) }
    val studioState = remember { context.getSharedPreferences("studio-ui-state", 0) }
    var rootAccessAvailable by remember { mutableStateOf<Boolean?>(null) }
    // Keep this unknown until the asynchronous root/Shizuku probe finishes. Rendering STANDARD
    // here caused a misleading rootless card to flash briefly on rooted devices.
    var accessMode by remember { mutableStateOf<StudioAccessMode?>(null) }
    val capabilities = StudioCapabilityPolicy(
        rootAvailable = rootAccessAvailable == true,
        themeManagerBehavior = themeManagerBehavior,
    )
    var catalogLoadFinished by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var activeThemeId by rememberSaveable {
        mutableStateOf(studioState.getString("last-applied-theme-id", null))
    }

    fun persistPreparedApply(prepared: PreparedThemeApply) {
        if (prepared.protocol == ThemeApplyProtocol.MODERN_THEME_MANAGER_BRIDGE) {
            prepared.intent.putExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_RECEIVER, diagnostics.nativeStepReceiver())
        }
        diagnostics.record("theme_request_ready", "Temalar işlemine geçiliyor", mapOf(
            "operation" to prepared.operation, "theme" to prepared.themeName,
            "themeId" to prepared.themeId, "localId" to prepared.themeManagerLocalId,
            "protocol" to prepared.protocol,
        ))
        studioState.edit()
            .putLong("pending-started-at", System.currentTimeMillis())
            .putString("pending-theme-id", prepared.themeId)
            .putString("pending-theme-name", prepared.themeName)
            .putString("pending-staged-path", prepared.stagedPath)
            .putString("pending-protocol", prepared.protocol.name)
            .putString("pending-manual-path", prepared.manualImportPath)
            .putString("pending-operation", prepared.operation.name)
            .putString("pending-local-id", prepared.themeManagerLocalId)
            .apply()
    }

    fun restorePreparedApply(): PreparedThemeApply? = runCatching {
        val themeId = studioState.getString("pending-theme-id", null) ?: return@runCatching null
        val themeName = studioState.getString("pending-theme-name", null) ?: return@runCatching null
        PreparedThemeApply(
            themeId = themeId,
            themeName = themeName,
            stagedPath = studioState.getString("pending-staged-path", "").orEmpty(),
            intent = Intent(),
            protocol = ThemeApplyProtocol.valueOf(
                studioState.getString("pending-protocol", ThemeApplyProtocol.LEGACY_TESTER.name).orEmpty(),
            ),
            manualImportPath = studioState.getString("pending-manual-path", null),
            operation = ThemeManagerOperation.valueOf(
                studioState.getString("pending-operation", ThemeManagerOperation.APPLY.name).orEmpty(),
            ),
            themeManagerLocalId = studioState.getString("pending-local-id", null),
        )
    }.getOrNull()

    fun clearPreparedApply() {
        studioState.edit()
            .remove("pending-theme-id")
            .remove("pending-theme-name")
            .remove("pending-staged-path")
            .remove("pending-protocol")
            .remove("pending-manual-path")
            .remove("pending-operation")
            .remove("pending-local-id")
            .remove("pending-started-at")
            .apply()
    }

    val homeWallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            customHomeWallpaperUri = uri.toString()
            status = resources.getString(R.string.custom_home_wallpaper_selected)
        }
    }

    val lockWallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            customLockWallpaperUri = uri.toString()
            status = resources.getString(R.string.custom_lock_wallpaper_selected)
        }
    }

    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                val account = CloudAccount(
                    provider = CloudProvider.GOOGLE_DRIVE,
                    accountName = accountName,
                    isConnected = true,
                )
                cloudAccountStore.save(account)
                cloudAccount = account
                backupStatus = resources.getString(R.string.status_cloud_connected, accountName)
            }
        }
    }

    val onChooseGoogleAccount: () -> Unit = {
        val intent = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
        googleAccountPickerLauncher.launch(intent)
    }

    suspend fun loadLibrarySnapshot(): Boolean {
        val snapshot = withContext(Dispatchers.IO) { library.load() }
        themes = snapshot.themes
        status = when {
            snapshot.warnings.isNotEmpty() -> resources.getString(R.string.status_library_warnings, snapshot.warnings.size)
            themes.isEmpty() -> resources.getString(R.string.status_library_empty)
            else -> resources.getString(R.string.status_library_ready)
        }
        return snapshot.warnings.isEmpty()
    }

    fun reload(openThemesAfter: Boolean = false) {
        scope.launch {
            val valid = loadLibrarySnapshot()
            if (openThemesAfter && valid) destination = StudioDestination.THEMES
        }
    }

    fun localizeTheme(theme: LibraryTheme) {
        if (themeOperationRunning) return
        themeOperationRunning = true
        scope.launch {
            status = resources.getString(R.string.theme_language_tool_working)
            runCatching {
                withContext(Dispatchers.IO) { themeLanguageTool.translateTextToSystemLanguage(theme) }
            }.onSuccess { localized ->
                // Keep a portable copy alongside other Studio-generated MTZ files.
                MtzPublicExporter.exportToPublicDownloads(context, localized.archive.source, localized.displayName)
                loadLibrarySnapshot()
                destination = StudioDestination.THEMES
                status = resources.getString(R.string.theme_language_tool_complete)
                operationError = status
            }.onFailure { error ->
                status = resources.getString(
                    R.string.theme_language_tool_failed,
                    error.message ?: error::class.simpleName,
                )
                operationError = status
            }
            themeOperationRunning = false
        }
    }

    val applyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val prepared = preparedApply ?: restorePreparedApply()
        val requestStartedAt = studioState.getLong("pending-started-at", System.currentTimeMillis())
        diagnostics.record("theme_activity_returned", "Temalar ekranından yanıt geldi", mapOf(
            "operation" to prepared?.operation, "theme" to prepared?.themeName,
            "resultCode" to result.resultCode,
            "bridgeResult" to result.data?.getStringExtra(ThemeManagerBridgeContract.EXTRA_RESULT),
            "error" to result.data?.getStringExtra(ThemeManagerBridgeContract.EXTRA_ERROR),
            "durationMs" to (System.currentTimeMillis() - requestStartedAt),
        ))
        result.data?.getStringArrayListExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_TRACE)
            ?.take(40)?.forEach(diagnostics::recordNativeStep)
        preparedApply = null
        themeOperationRunning = false
        pauseCatalog.set(false)
        clearPreparedApply()
        if (prepared != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(10_000)
                    runCatching { themeApplyCoordinator.cleanup(prepared) }
                }
            }
            when (prepared.protocol) {
                ThemeApplyProtocol.LEGACY_TESTER -> {
                    diagnostics.record("legacy_apply_unverified", "Global tester çağrısı döndü; bu protokol kesin uygulama sonucu bildirmiyor", mapOf("theme" to prepared.themeName))
                    status = resources.getString(R.string.status_legacy_apply_unverified, prepared.themeName)
                    activeThemeId = prepared.themeId
                    studioState.edit().putString("last-applied-theme-id", prepared.themeId).apply()
                }

                ThemeApplyProtocol.MODERN_THEME_MANAGER_BRIDGE -> {
                    val bridgeSucceeded = result.resultCode == Activity.RESULT_OK &&
                        result.data?.getStringExtra(ThemeManagerBridgeContract.EXTRA_RESULT) == ThemeManagerBridgeContract.RESULT_OK
                    if (bridgeSucceeded) {
                        diagnostics.record("theme_operation_completed", "Temalar işlemi tamamlandı", mapOf("operation" to prepared.operation, "theme" to prepared.themeName))
                        val localId = result.data?.getStringExtra(ThemeManagerBridgeContract.EXTRA_THEME_LOCAL_ID)
                        when (prepared.operation) {
                            ThemeManagerOperation.APPLY -> {
                                status = resources.getString(R.string.status_apply_success, prepared.themeName)
                                activeThemeId = prepared.themeId
                                studioState.edit().putString("last-applied-theme-id", prepared.themeId).apply()
                            }
                            ThemeManagerOperation.IMPORT_ONLY -> {
                                scope.launch {
                                    // A Theme Manager operation can recreate this activity before
                                    // its initial library load completes. Resolve the persisted item.
                                    withContext(Dispatchers.IO) {
                                        library.load().themes.firstOrNull {
                                            it.id.value == prepared.themeId
                                        }?.let { imported ->
                                            if (!localId.isNullOrBlank()) {
                                                deviceThemeImporter.rememberThemeManagerOrigin(localId, imported)
                                            }
                                        }
                                    }
                                    status = resources.getString(R.string.status_modern_theme_imported, prepared.themeName)
                                    reload(openThemesAfter = true)
                                }
                            }
                            ThemeManagerOperation.DELETE -> {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        library.deleteTheme(ThemeId(prepared.themeId))
                                        prepared.themeManagerLocalId?.let(deviceThemeImporter::forgetThemeManagerOrigin)
                                    }
                                    if (activeThemeId == prepared.themeId) {
                                        activeThemeId = null
                                        studioState.edit().remove("last-applied-theme-id").apply()
                                    }
                                    status = resources.getString(R.string.status_theme_removed, prepared.themeName)
                                    reload()
                                }
                            }
                        }
                    } else {
                        diagnostics.record("theme_operation_unconfirmed", "Temalar işlemi başarısız veya sonuç doğrulanamadı", mapOf(
                            "operation" to prepared.operation, "theme" to prepared.themeName,
                            "error" to result.data?.getStringExtra(ThemeManagerBridgeContract.EXTRA_ERROR),
                        ))
                        scope.launch(Dispatchers.IO) { themeApplyCoordinator.captureFailureDiagnostics(requestStartedAt) }
                        // Do not bounce straight back to the host after a failed or cancelled request.
                        status = resources.getString(
                            R.string.status_apply_failed,
                            result.data?.getStringExtra(ThemeManagerBridgeContract.EXTRA_ERROR)
                                ?: resources.getString(R.string.error_modern_bridge_failed),
                        )
                        operationError = status
                    }
                }

                ThemeApplyProtocol.MODERN_THEME_MANAGER_MANUAL_IMPORT -> {
                    status = resources.getString(
                        R.string.status_manual_import_ready,
                        prepared.manualImportPath.orEmpty(),
                    )
                }

                ThemeApplyProtocol.ROOTLESS_MANUAL_IMPORT -> {
                    diagnostics.record(
                        "rootless_manual_returned",
                        "Rootsuz Temalar yönlendirmesinden uygulamaya dönüldü",
                        mapOf("theme" to prepared.themeName),
                    )
                    status = resources.getString(
                        R.string.status_manual_import_ready,
                        prepared.manualImportPath.orEmpty(),
                    )
                }

                ThemeApplyProtocol.ROOTLESS_LEGACY_TESTER -> {
                    diagnostics.record(
                        "rootless_legacy_apply_unverified",
                        "Rootsuz Global tester çağrısı döndü; kesin uygulama sonucu ve kalıcılık doğrulanamaz",
                        mapOf("theme" to prepared.themeName),
                    )
                    status = resources.getString(R.string.status_legacy_apply_unverified, prepared.themeName)
                    activeThemeId = prepared.themeId
                    studioState.edit().putString("last-applied-theme-id", prepared.themeId).apply()
                }
            }
        }
    }

    val rootlessNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        diagnostics.record(
            "rootless_restore_notification_permission",
            if (granted) "Rootsuz yeniden uygulama bildirim izni verildi" else "Rootsuz yeniden uygulama bildirim izni verilmedi",
            mapOf("granted" to granted),
        )
    }

    fun launchThemeOperation(block: suspend () -> Unit) {
        pauseCatalog.set(true)
        scope.launch {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                diagnostics.record("theme_operation_failed", "Tema işlemi tamamlanamadı", error = error)
                status = resources.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
                operationError = status
            } finally {
                themeOperationRunning = preparedApply != null
                if (preparedApply == null) pauseCatalog.set(false)
            }
        }
    }

    fun launchPreparedTheme(prepared: PreparedThemeApply) {
        try {
            preparedApply = prepared
            persistPreparedApply(prepared)
            diagnostics.record(
                "theme_activity_launching",
                "Temalar etkinliği başlatılıyor",
                mapOf(
                    "protocol" to prepared.protocol,
                    "action" to prepared.intent.action,
                    "component" to prepared.intent.component?.flattenToShortString(),
                    "rootless" to prepared.protocol.name.startsWith("ROOTLESS_"),
                ),
            )
            applyLauncher.launch(prepared.intent)
            diagnostics.record(
                "theme_activity_launched",
                "Temalar etkinliği başlatıldı; dönüş bekleniyor",
                mapOf("protocol" to prepared.protocol, "theme" to prepared.themeName),
            )
        } catch (error: Exception) {
            preparedApply = null
            clearPreparedApply()
            diagnostics.record("theme_activity_launch_failed", "Temalar etkinliği başlatılamadı", error = error)
            throw error
        }
    }

    fun deleteTheme(theme: LibraryTheme) {
        if (themeOperationRunning) return
        themeOperationRunning = true
        diagnostics.record("delete_requested", "Tema kaldırma istendi", mapOf("theme" to theme.displayName, "themeId" to theme.id.value))
        if (capabilities.usesNativeCatalog) {
            val localId = deviceThemeImporter.localIdFor(theme)
            if (localId != null) {
                launchThemeOperation {
                    runCatching {
                        withContext(Dispatchers.IO) { themeApplyCoordinator.prepareModernDelete(theme, localId) }
                    }.onSuccess { prepared ->
                        launchPreparedTheme(prepared)
                    }.onFailure { error ->
                        diagnostics.record("theme_request_prepare_failed", "Temalar işlemi hazırlanamadı", error = error)
                        status = resources.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
                        operationError = status
                    }
                }
                return
            }
        }
        launchThemeOperation {
            val success = withContext(Dispatchers.IO) { library.deleteTheme(theme.id) }
            if (success) {
                val snapshot = withContext(Dispatchers.IO) { library.load() }
                themes = snapshot.themes
                if (baseThemeId == theme.id.value) {
                    baseThemeId = null
                }
                if (activeThemeId == theme.id.value) {
                    activeThemeId = null
                    studioState.edit().remove("last-applied-theme-id").apply()
                }
                status = resources.getString(R.string.status_theme_removed, theme.archive.metadata?.name ?: theme.displayName)
            }
        }
    }

    fun navigateTo(target: StudioDestination, returnTo: StudioDestination = StudioDestination.HOME) {
        returnDestination = returnTo
        destination = target
    }

    fun navigateBack() {
        destination = returnDestination
        returnDestination = StudioDestination.HOME
    }

    fun composeTheme() {
        if (themeOperationRunning) return
        themeOperationRunning = true
        launchThemeOperation {
            diagnostics.record("compose_started", "Tema oluşturma başladı", mapOf(
                "name" to compositionName, "baseThemeId" to baseThemeId,
                "selections" to selections.values.joinToString { "${it.category}=${it.themeId};default=${it.useDefault}" },
                "customHomeWallpaper" to (customHomeWallpaperUri != null),
                "customLockWallpaper" to (customLockWallpaperUri != null),
            ))
            status = resources.getString(R.string.status_composing)
            runCatching {
                withContext(Dispatchers.IO) {
                    val byId = themes.associateBy { it.id }
                    val homeBytes = customHomeWallpaperUri?.let { uriStr ->
                        openInput(Uri.parse(uriStr))?.use { it.readBytes() }
                    }
                    val lockBytes = customLockWallpaperUri?.let { uriStr ->
                        openInput(Uri.parse(uriStr))?.use { it.readBytes() }
                    }
                    val baseTheme = baseThemeId?.let { id -> themes.firstOrNull { it.id.value == id } }
                    val previewSource = homeBytes
                        ?: baseTheme?.let(::readHomePreviewSource)
                        ?: selections.values.asSequence()
                            .mapNotNull { selected -> byId[selected.themeId] }
                            .mapNotNull(::readHomePreviewSource)
                            .firstOrNull()
                    val generatedPreview = GeneratedThemePreviewFactory.create(
                        themeName = compositionName.trim(),
                        wallpaperBytes = previewSource,
                    )
                    val makerName = compositionMakerName.trim().takeIf(String::isNotEmpty)
                    diagnostics.record("compose_preview_ready", "Önizleme hazırlığı tamamlandı")
                    val request = CompositionRequest(
                        metadata = CompositionMetadata(
                            name = compositionName.trim(),
                            author = makerName,
                            designer = makerName,
                        ),
                        baseSource = baseTheme?.let { theme ->
                            CompositionSource(theme.id, theme.displayName, theme.archive)
                        },
                        selections = selections.values
                            .filter { it.category.isPersonalizationOption() }
                            .map { selected ->
                            val theme = byId.getValue(selected.themeId)
                            ComponentSelection(
                                source = CompositionSource(theme.id, theme.displayName, theme.archive),
                                category = selected.category,
                                rootPath = selected.rootPath,
                                useDefault = selected.useDefault,
                            )
                        },
                        customHomeWallpaperBytes = homeBytes,
                        customLockWallpaperBytes = lockBytes,
                        generatedPreviewBytes = generatedPreview,
                    )
                    val result = composer.compose(request, library.newExportPath(compositionName))
                    diagnostics.record("compose_archive_created", "MTZ bileşenleri birleştirildi", mapOf("file" to result.output.fileName))
                    library.recordComposition(result)
                    val importedTheme = java.nio.file.Files.newInputStream(result.output).use { input ->
                        library.importTheme(
                            input = input,
                            suggestedName = compositionName.trim(),
                            includeInThemeGallery = true,
                        )
                    }
                    // Export copy to device public Downloads/MTZ Studio folder
                    diagnostics.record("compose_library_saved", "Oluşturulan tema özel kitaplığa kaydedildi", mapOf("themeId" to importedTheme.id.value, "sha256" to importedTheme.archive.sha256))
                    val publicCopy = MtzPublicExporter.exportToPublicDownloads(context, result.output, compositionName.trim())
                    diagnostics.record("compose_public_export", if (publicCopy != null) "MTZ, İndirilenler/MTZ Studio klasörüne kaydedildi" else "MTZ genel klasöre kaydedilemedi; özel kopya korundu")
                    result to importedTheme
                }
            }.onSuccess { (result, importedTheme) ->
                diagnostics.record("compose_completed", "Tema oluşturma tamamlandı", mapOf("name" to compositionName))
                lastResult = result
                val snapshot = withContext(Dispatchers.IO) { library.load() }
                themes = snapshot.themes
                destination = StudioDestination.THEMES
                status = resources.getString(R.string.status_compose_success, compositionName.trim())
                if (capabilities.usesNativeCatalog) {
                    runCatching {
                        withContext(Dispatchers.IO) { themeApplyCoordinator.prepareModernImportOnly(importedTheme) }
                    }.onSuccess { prepared ->
                        launchPreparedTheme(prepared)
                    }.onFailure { error ->
                        themeOperationRunning = false
                        diagnostics.record("theme_request_prepare_failed", "Temalar işlemi hazırlanamadı", error = error)
                        status = resources.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
                        operationError = status
                    }
                } else {
                    themeOperationRunning = false
                }
            }.onFailure { error ->
                themeOperationRunning = false
                status = resources.getString(R.string.status_compose_failed, error.message ?: error::class.simpleName)
                diagnostics.record("compose_failed", "Tema oluşturulamadı", error = error)
            }
        }
    }

    fun openDeviceThemePicker() {
        showDeviceThemePicker = true
        isScanningDeviceThemes = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { deviceThemeImporter.listAvailableDeviceThemes() }
            }.onSuccess { list ->
                availableDeviceThemes = list
                isScanningDeviceThemes = false
            }.onFailure { err ->
                isScanningDeviceThemes = false
                themeDeviceImportStatus = resources.getString(R.string.device_import_failed, err.message ?: "Failed to scan")
            }
        }
    }

    fun importSelectedDeviceThemes(selectedIds: Set<String>) {
        if (deviceImportRunning || selectedIds.isEmpty()) return
        scope.launch {
            deviceImportRunning = true
            themeDeviceImportStatus = resources.getString(R.string.device_import_working)
            runCatching {
                withContext(Dispatchers.IO) { deviceThemeImporter.importSelectedThemes(selectedIds) }
            }.onSuccess { summary ->
                reload(openThemesAfter = true)
                themeDeviceImportStatus = resources.getString(
                    R.string.device_theme_import_summary,
                    summary.found,
                    summary.added,
                    summary.duplicates,
                    summary.failed,
                )
                if (summary.errors.isNotEmpty()) status = summary.errors.take(3).joinToString("\n")
            }.onFailure { error ->
                themeDeviceImportStatus = resources.getString(
                    R.string.device_import_failed,
                    error.message ?: error::class.simpleName,
                )
            }
            deviceImportRunning = false
        }
    }

    fun importFromThemeManager(fontOnly: Boolean) {
        if (deviceImportRunning) return
        scope.launch {
            deviceImportRunning = true
            if (fontOnly) fontDeviceImportStatus = resources.getString(R.string.device_import_working)
            else themeDeviceImportStatus = resources.getString(R.string.device_import_working)
            if (fontOnly) {
                runCatching {
                    withContext(Dispatchers.IO) { deviceThemeImporter.importActiveFont() }
                }.onSuccess { result ->
                    destination = StudioDestination.FONTS
                    reload()
                    val name = result.theme.archive.metadata?.name ?: result.theme.displayName
                    fontDeviceImportStatus = resources.getString(
                        if (result.addedToLibrary) R.string.device_import_added else R.string.device_import_duplicate,
                        name,
                    )
                }.onFailure { error ->
                    fontDeviceImportStatus = resources.getString(
                        R.string.device_import_failed,
                        error.message ?: error::class.simpleName,
                    )
                }
            } else {
                openDeviceThemePicker()
            }
            deviceImportRunning = false
        }
    }

    fun refreshModernThemeManagerCatalog() {
        if (!capabilities.usesNativeCatalog || deviceImportRunning || preparedApply != null || themeOperationRunning || checkingImportAccess) return
        // Claim the refresh before launching so entry/resume cannot start two scans.
        deviceImportRunning = true
        pauseCatalog.set(false)
        scope.launch {
            catalogError = null
            themeDeviceImportStatus = resources.getString(R.string.device_import_working)
            android.util.Log.i("MtzCatalog", "Automatic catalog refresh started")
            diagnostics.record("catalog_sync_started", "Tema kitaplığı eşitleniyor")
            try {
                // Keep cached sources visible while a large Themes library is reconstructed in
                // small resumable batches.  This work remains off the UI thread.
                loadLibrarySnapshot()
                catalogLoadFinished = true
                var totalAdded = 0
                var totalFailed = 0
                var found = 0
                var completed = false
                while (!completed) {
                    if (pauseCatalog.get()) {
                        diagnostics.record("catalog_sync_waiting", "Öncelikli MTZ işlemi için katalog eşitlemesi bekletildi")
                        while (pauseCatalog.get()) delay(250)
                    }
                    val batch = try {
                        withContext(Dispatchers.IO) { deviceThemeImporter.synchronizeModernLibrary { pauseCatalog.get() } }
                    } catch (error: Exception) {
                        themeDeviceImportStatus = resources.getString(
                            R.string.device_import_failed, error.message ?: error::class.simpleName,
                        )
                        diagnostics.record("catalog_sync_failed", "Tema kitaplığı eşitlenemedi", error = error)
                        catalogError = themeDeviceImportStatus
                        break
                    }
                    loadLibrarySnapshot()
                    found = batch.found
                    totalAdded += batch.added
                    totalFailed += batch.failed
                    completed = batch.completed
                    themeDeviceImportStatus = resources.getString(
                        R.string.modern_catalog_sync_summary,
                        found, totalAdded, totalFailed,
                    )
                    android.util.Log.i("MtzCatalog", "Catalog batch: found=$found, added=$totalAdded, failed=$totalFailed, completed=$completed")
                    diagnostics.record(
                        if (completed) "catalog_sync_completed" else "catalog_sync_batch_completed",
                        if (completed) "Tema kitaplığı eşitlendi" else "Tema kitaplığı arka planda sonraki gruba geçiyor",
                        mapOf("found" to found, "added" to totalAdded, "failed" to totalFailed,
                            "completed" to completed, "batchErrors" to batch.errors.joinToString("\n")),
                    )
                    if (totalFailed > 0) {
                        catalogError = resources.getString(R.string.catalog_partial_failure)
                    }
                    if (!completed) delay(350)
                }
                if (completed) {
                    Toast.makeText(context, resources.getString(R.string.catalog_sync_all_complete), Toast.LENGTH_LONG).show()
                }
            } finally {
                catalogLoadFinished = true
                deviceImportRunning = false
            }
        }
    }

    fun requestFullThemeManagerCatalog() {
        if (!capabilities.usesNativeCatalog || deviceImportRunning || themeOperationRunning || checkingImportAccess) return
        scope.launch {
            isScanningDeviceThemes = true
            val count = runCatching {
                withContext(Dispatchers.IO) { deviceThemeImporter.availableThemeCount() }
            }.onFailure { error ->
                diagnostics.record("catalog_count_failed", "Tema Yöneticisi tema sayısı okunamadı", error = error)
                themeDeviceImportStatus = resources.getString(
                    R.string.device_import_failed, error.message ?: error::class.simpleName,
                )
            }.getOrNull()
            isScanningDeviceThemes = false
            if (count == null) return@launch
            diagnostics.record("catalog_full_access_requested", "Tema Yöneticisindeki tüm temalar istendi", mapOf("count" to count))
            if (count > MAX_SAFE_FULL_CATALOG_THEMES) {
                largeCatalogThemeCount = count
            } else {
                refreshModernThemeManagerCatalog()
            }
        }
    }

    fun exportTheme(theme: LibraryTheme) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { library.exportTheme(theme) }
            }.onSuccess(shareMtz).onFailure { error ->
                status = resources.getString(
                    R.string.status_export_failed,
                    error.message ?: error::class.simpleName,
                )
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        checkingImportAccess = false
        if (uri != null && !themeOperationRunning) {
            themeOperationRunning = true
            diagnostics.recordPickerResultReceived()
            launchThemeOperation {
                status = resources.getString(R.string.status_copying_verifying)
                var diagnosticSession: ImportDiagnosticSession? = null
                runCatching {
                    withContext(Dispatchers.IO) {
                        val document = documentDiagnostics(uri)
                        val session = diagnostics.beginImport(document)
                        diagnosticSession = session
                        // Saving a private MTZ needs no root channel. Verify root only for host operations.
                        openInput(uri)?.use { input ->
                            library.importTheme(input, document.displayName, session.observer)
                        } ?: run {
                            val openErr = resources.getString(R.string.error_open_document)
                            session.failBeforeImport(openErr)
                            error(openErr)
                        }
                    }
                }.onSuccess { importedTheme ->
                    themes = (themes.filterNot { it.id == importedTheme.id } + importedTheme)
                    diagnostics.record("import_components", "Tema bileşenleri ve genel önizleme incelendi", mapOf(
                        "themeId" to importedTheme.id.value,
                        "components" to importedTheme.archive.components.joinToString { it.category.name },
                        "defaultPreviews" to dev.glorioustr.mtzstudio.core.ThemeVisualPolicy
                            .defaultPreviewPaths(importedTheme.archive.entries).joinToString(),
                    ))
                    if (capabilities.usesNativeCatalog) {
                        runCatching {
                            withContext(Dispatchers.IO) { themeApplyCoordinator.prepareModernImportOnly(importedTheme) }
                        }.onSuccess { prepared ->
                            launchPreparedTheme(prepared)
                        }.onFailure { error ->
                            themeOperationRunning = false
                            diagnostics.record(
                                "modern_import_source_retained",
                                "Tema Yöneticisi hazırlanamadı; özel MTZ tekrar denemek için korundu",
                                mapOf("themeId" to importedTheme.id.value),
                            )
                            status = resources.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
                            operationError = status
                        }
                    } else {
                        themeOperationRunning = false
                        reload(openThemesAfter = true)
                    }
                }.onFailure { error ->
                    themeOperationRunning = false
                    diagnosticSession?.failBeforeImport(error.message ?: error::class.simpleName ?: "unknown error")
                    status = resources.getString(R.string.status_import_rejected, error.message ?: error::class.simpleName)
                }
            }
        } else {
            pauseCatalog.set(false)
        }
    }

    val bakPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || bakImporting || themeOperationRunning) return@rememberLauncherForActivityResult
        bakImporting = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = documentDiagnostics(uri).displayName ?: "Themes.bak"
                    val stream = openInput(uri) ?: error(resources.getString(R.string.error_open_document))
                    bakImporter.stageAndInspect(stream, name)
                }
            }.onSuccess { archive ->
                pendingBakArchive = archive
                bakVersionMismatchAccepted = false
            }.onFailure { error ->
                status = resources.getString(R.string.bak_import_failed, error.message ?: error::class.simpleName)
                diagnostics.record("bak_inspect_failed", "BAK arşivi incelenemedi", error = error)
            }
            bakImporting = false
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                backupStatus = resources.getString(R.string.status_backup_preparing)
                runCatching {
                    withContext(Dispatchers.IO) {
                        openOutput(uri)?.use { output -> backupManager.create(output) }
                            ?: error(resources.getString(R.string.error_backup_target))
                    }
                }.onSuccess { summary ->
                    backupStatus = resources.getString(R.string.status_backup_success, summary.themeCount, summary.fileCount)
                }.onFailure { error ->
                    backupStatus = resources.getString(R.string.status_backup_failed, error.message ?: error::class.simpleName)
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                backupStatus = resources.getString(R.string.status_restore_preparing)
                runCatching {
                    withContext(Dispatchers.IO) {
                        openInput(uri)?.use { input -> backupManager.restore(input) }
                            ?: error(resources.getString(R.string.error_restore_target))
                    }
                }.onSuccess { summary ->
                    reload()
                    backupStatus = resources.getString(R.string.status_restore_success, summary.themeCount, summary.fileCount)
                }.onFailure { error ->
                    backupStatus = resources.getString(R.string.status_restore_failed, error.message ?: error::class.simpleName)
                }
            }
        }
    }

    SheveryAuthorizationGate(privilegedRunner) {
        scope.launch(Dispatchers.IO) {
            val mode = privilegedRunner.accessModeSilently()
            withContext(Dispatchers.Main) {
                accessMode = mode
                rootAccessAvailable = mode == StudioAccessMode.ROOT
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // The local library is always available, including while capability detection runs.
        reload()
        val mode = withContext(Dispatchers.IO) { privilegedRunner.accessModeSilently() }
        accessMode = mode
        rootAccessAvailable = mode == StudioAccessMode.ROOT
        val rootReady = mode == StudioAccessMode.ROOT
        diagnostics.record(
            "privilege_mode_selected",
            when (mode) {
                StudioAccessMode.ROOT -> "Rootlu tam erişim modu etkin"
                StudioAccessMode.SHIZUKU -> "Shizuku gelişmiş rootsuz modu etkin"
                StudioAccessMode.STANDARD -> "Standart rootsuz çalışma alanı etkin"
            },
            mapOf("root" to rootReady, "mode" to mode.name, "themeManagerBehavior" to themeManagerBehavior),
        )
        if (rootReady) {
            ThemeProtectionServiceClient.initialize(context.applicationContext, globalThemeProtectionRequired)
            ThemeProtectionServiceClient.setCommandRunner { cmd ->
                runCatching { privilegedRunner.run(cmd, 3).output }.getOrNull()
            }
            ThemeProtectionServiceClient.refresh()
        }
    }
    androidx.compose.runtime.LaunchedEffect(capabilities.usesNativeCatalog) {
        if (!capabilities.usesNativeCatalog) {
            catalogLoadFinished = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(catalogProgress) {
        if (catalogProgress.total > 0) {
            // Merge incremental catalog items without hiding a concurrently imported private source.
            themes = (themes + catalogProgress.themes).associateBy { it.id }.values.toList()
            if (deviceImportRunning) {
                themeDeviceImportStatus = resources.getString(R.string.device_import_working) +
                    " ${catalogProgress.processed}/${catalogProgress.total}"
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(destination, modernThemeManagerMode) {
        diagnostics.record("screen_opened", "Ekran açıldı", mapOf("screen" to destination))
    }
    androidx.compose.runtime.LaunchedEffect(status) {
        diagnostics.record("operation_status", status)
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        // Also pick up changes made in Xiaomi Themes while Studio was in the background.
        if (destination == StudioDestination.THEMES && capabilities.usesNativeCatalog) refreshModernThemeManagerCatalog()
    }
    androidx.compose.runtime.LaunchedEffect(globalThemeProtectionRequired) {
        if (!globalThemeProtectionRequired && destination == StudioDestination.THEME_PROTECTION) {
            destination = StudioDestination.HOME
            returnDestination = StudioDestination.HOME
        }
    }
    BackHandler(enabled = destination != StudioDestination.HOME, onBack = ::navigateBack)

    // Keep private drafts visible even while native synchronization is incomplete or unavailable.
    val workspaceThemes = themes

    Scaffold(
        containerColor = if (contentStyle == AppContentStyle.LIQUID_GLASS) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.background
        },
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (destination == StudioDestination.HOME) {
                        Image(
                            painter = painterResource(R.drawable.logo_banner),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.height(44.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(stringResource(destination.titleRes))
                    }
                },
                navigationIcon = {
                    if (destination == StudioDestination.HOME) {
                        IconButton(
                            onClick = { appMenuExpanded = true },
                            modifier = Modifier.semantics { contentDescription = resources.getString(R.string.menu_title) },
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = ::navigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (contentStyle == AppContentStyle.LIQUID_GLASS) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            destination == StudioDestination.HOME -> HomeMenuScreen(
                importExpanded = importExpanded,
                importing = diagnosticState.activeSessionId != null || checkingImportAccess || themeOperationRunning,
                accessMode = accessMode,
                themeManagerInspector = themeManagerInspector,
                themeManagerUpdater = themeManagerUpdater,
                openInput = openInput,
                onToggleImport = { importExpanded = !importExpanded },
                onAddMtz = {
                    if (!checkingImportAccess && !themeOperationRunning) {
                        pauseCatalog.set(true)
                        checkingImportAccess = true
                        runCatching {
                            diagnostics.recordPickerLaunched()
                            picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }.onFailure { error ->
                            checkingImportAccess = false
                            status = resources.getString(R.string.status_import_rejected, error.message ?: "")
                        }
                    }
                },
                showBakImport = rootAccessAvailable == true,
                bakImporting = bakImporting,
                onAddBak = {
                    runCatching { bakPicker.launch(arrayOf("application/octet-stream", "application/x-tar", "*/*")) }
                        .onFailure { error -> status = resources.getString(R.string.bak_import_failed, error.message ?: "") }
                },
                onNavigate = { navigateTo(it) },
                showThemeManagerVersionTool = rootAccessAvailable == true && !modernThemeManagerMode,
                modifier = contentModifier,
            )
            destination == StudioDestination.THEMES -> ThemesScreen(
                themes = workspaceThemes,
                activeThemeId = activeThemeId,
                deviceImportStatus = themeDeviceImportStatus,
                deviceImportRunning = deviceImportRunning || (capabilities.usesNativeCatalog && !catalogLoadFinished),
                catalogError = catalogError,
                onRetryCatalog = ::refreshModernThemeManagerCatalog,
                onOpenDeviceThemePicker = ::openDeviceThemePicker,
                onShowAllDeviceThemes = ::requestFullThemeManagerCatalog,
                showDeviceImport = rootAccessAvailable == true,
                nativeCatalogMode = capabilities.usesNativeCatalog,
                rootlessMode = rootAccessAvailable == false,
                onApplyTheme = { if (!themeOperationRunning) pendingApplyTheme = it },
                onTranslateTheme = ::localizeTheme,
                onDeleteTheme = ::deleteTheme,
                modifier = contentModifier,
            )
            destination == StudioDestination.PERSONALIZE -> PersonalizeScreen(
                themes = workspaceThemes,
                selections = selections,
                compositionName = compositionName,
                compositionMakerName = compositionMakerName,
                operationRunning = themeOperationRunning,
                lastResult = lastResult,
                status = status,
                baseThemeId = baseThemeId,
                onSelectBaseTheme = { theme ->
                    baseThemeId = theme?.id?.value
                    // Switching the base must not retain components absent from the new theme.
                    selections.clear()
                    if (theme != null) {
                        theme.archive.components.forEach { comp ->
                            if (comp.category.isPersonalizationOption()) {
                                selections[comp.category] = UiSelection(theme.id, comp.category, comp.rootPath)
                            }
                        }
                        dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.personalizationCategories.forEach { category ->
                            if (dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.isPreviewOnly(
                                    theme.archive.components, theme.archive.entries, category)) {
                                selections[category] = UiSelection(theme.id, category, "", useDefault = true)
                            }
                        }
                        val themeName = theme.archive.metadata?.name ?: theme.displayName
                        compositionName = if (themeName.endsWith(" Karmam", ignoreCase = true)) {
                            themeName
                        } else {
                            "$themeName Karmam"
                        }
                    }
                },
                customHomeWallpaperUri = customHomeWallpaperUri,
                customLockWallpaperUri = customLockWallpaperUri,
                onPickHomeWallpaper = { homeWallpaperPickerLauncher.launch("image/*") },
                onRemoveHomeWallpaper = { customHomeWallpaperUri = null },
                onPickLockWallpaper = { lockWallpaperPickerLauncher.launch("image/*") },
                onRemoveLockWallpaper = { customLockWallpaperUri = null },
                onCompositionNameChange = { compositionName = it },
                onCompositionMakerNameChange = { compositionMakerName = it },
                onCompose = ::composeTheme,
                onShare = shareMtz,
                modifier = contentModifier,
            )
            destination == StudioDestination.DIAGNOSTICS -> LiveDiagnosticsCard(
                recorder = diagnostics,
                shareDiagnostics = shareDiagnostics,
                shareThemeManagerApk = shareThemeManagerApk,
                modifier = contentModifier,
            )
            destination == StudioDestination.BACKUP -> BackupRestoreScreen(
                themeCount = themes.size,
                status = backupStatus,
                cloudAccount = cloudAccount,
                onSelectGoogleDrive = onChooseGoogleAccount,
                onConnectCloud = { account ->
                    cloudAccountStore.save(account)
                    cloudAccount = account
                    backupStatus = resources.getString(R.string.status_cloud_connected, account.accountName)
                },
                onDisconnectCloud = {
                    cloudAccountStore.disconnect()
                    cloudAccount = cloudAccountStore.load()
                    backupStatus = resources.getString(R.string.status_cloud_disconnected)
                },
                onBackupCloud = {
                    scope.launch {
                        backupStatus = resources.getString(R.string.status_cloud_uploading, cloudAccount.provider.displayName)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                cloudAccountStore.openCloudBackupOutputStream().use { output ->
                                    backupManager.create(output)
                                }
                            }
                        }.onSuccess { summary ->
                            cloudAccountStore.recordBackup()
                            cloudAccount = cloudAccountStore.load()
                            backupStatus = resources.getString(R.string.status_cloud_upload_success, cloudAccount.provider.displayName)
                        }.onFailure { error ->
                            backupStatus = resources.getString(R.string.status_backup_failed, error.message ?: error::class.simpleName)
                        }
                    }
                },
                onRestoreCloud = {
                    scope.launch {
                        if (!cloudAccountStore.hasCloudBackup()) {
                            backupStatus = resources.getString(R.string.status_cloud_no_backup, cloudAccount.provider.displayName)
                            return@launch
                        }
                        backupStatus = resources.getString(R.string.status_cloud_downloading, cloudAccount.provider.displayName)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                cloudAccountStore.openCloudBackupInputStream()?.use { input ->
                                    backupManager.restore(input)
                                } ?: error(resources.getString(R.string.status_cloud_no_backup, cloudAccount.provider.displayName))
                            }
                        }.onSuccess { summary ->
                            reload()
                            backupStatus = resources.getString(R.string.status_restore_success, summary.themeCount, summary.fileCount)
                        }.onFailure { error ->
                            backupStatus = resources.getString(R.string.status_restore_failed, error.message ?: error::class.simpleName)
                        }
                    }
                },
                onBackupLocal = {
                    backupLauncher.launch("hyperos-mtz-studio-backup-${System.currentTimeMillis()}.zip")
                },
                onRestoreLocal = {
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                modifier = contentModifier,
            )
            destination == StudioDestination.APPEARANCE -> AppearanceScreen(
                selected = appearance,
                onSelect = onAppearanceChange,
                contentStyle = contentStyle,
                onContentStyleSelect = onContentStyleChange,
                modifier = contentModifier,
            )
            destination == StudioDestination.ABOUT -> AboutScreen(modifier = contentModifier)
            destination == StudioDestination.THEME_PROTECTION -> ThemeProtectionScreen(
                state = themeProtectionState,
                onEnable = ThemeProtectionServiceClient::requestActivation,
                onDisable = ThemeProtectionServiceClient::disable,
                onOpenFramework = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            XposedManagerLauncher(context.applicationContext, privilegedRunner).open()
                        }
                        if (!result.opened) {
                            status = resources.getString(R.string.theme_protection_manager_missing)
                            android.widget.Toast.makeText(
                                context,
                                resources.getString(R.string.theme_protection_manager_open_failed, result.detail),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                onRestartDevice = { showThemeProtectionRestartDialog = true },
                onRefresh = {
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            privilegedRunner.run("am force-stop com.android.thememanager", 5)
                            privilegedRunner.run("am start -n com.android.thememanager/.ThemeResourceTabActivity", 5)
                            Thread.sleep(600)
                        }
                        ThemeProtectionServiceClient.refresh()
                    }
                },
                modifier = contentModifier,
            )
            destination.category != null -> CategoryScreen(
                destination = destination,
                themes = workspaceThemes,
                selections = selections,
                customHomeWallpaperUri = customHomeWallpaperUri,
                customLockWallpaperUri = customLockWallpaperUri,
                onPickHomeWallpaper = { homeWallpaperPickerLauncher.launch("image/*") },
                onRemoveHomeWallpaper = { customHomeWallpaperUri = null },
                onPickLockWallpaper = { lockWallpaperPickerLauncher.launch("image/*") },
                onRemoveLockWallpaper = { customLockWallpaperUri = null },
                deviceImportStatus = fontDeviceImportStatus,
                deviceImportRunning = deviceImportRunning,
                onImportActiveFont = { importFromThemeManager(fontOnly = true) },
                showDeviceFontImport = rootAccessAvailable == true,
                modifier = contentModifier,
            )
        }
    }

    if (appMenuExpanded) {
        StudioOverlayMenu(
            showThemeProtection = globalThemeProtectionRequired && rootAccessAvailable == true,
            onDismiss = { appMenuExpanded = false },
            onNavigate = { target ->
                appMenuExpanded = false
                navigateTo(target)
            },
        )
    }

    if (showThemeProtectionRestartDialog) {
        AlertDialog(
            onDismissRequest = { showThemeProtectionRestartDialog = false },
            title = { Text(stringResource(R.string.theme_protection_restart_title)) },
            text = { Text(stringResource(R.string.theme_protection_restart_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showThemeProtectionRestartDialog = false
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val result = privilegedRunner.run("setprop sys.powerctl reboot", 10)
                                if (result.exitCode != 0) error(result.output.ifBlank { "exit ${result.exitCode}" })
                            }
                        }.onFailure { error ->
                            status = resources.getString(
                                R.string.theme_protection_reboot_failed,
                                error.message ?: error::class.simpleName,
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.theme_protection_restart_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showThemeProtectionRestartDialog = false }) {
                    Text(stringResource(R.string.theme_protection_restart_cancel))
                }
            },
        )
    }

    if (showDeviceThemePicker) {
        DeviceThemePickerDialog(
            availableThemes = availableDeviceThemes,
            isLoading = isScanningDeviceThemes,
            onDismiss = { showDeviceThemePicker = false },
            onImportSelected = { selectedIds ->
                showDeviceThemePicker = false
                importSelectedDeviceThemes(selectedIds)
            },
        )
    }

    largeCatalogThemeCount?.let { count ->
        AlertDialog(
            onDismissRequest = { largeCatalogThemeCount = null },
            title = { Text(stringResource(R.string.catalog_large_warning_title)) },
            text = { Text(stringResource(R.string.catalog_large_warning_desc, count, MAX_SAFE_FULL_CATALOG_THEMES)) },
            confirmButton = {
                TextButton(onClick = {
                    largeCatalogThemeCount = null
                    refreshModernThemeManagerCatalog()
                }) { Text(stringResource(R.string.catalog_large_warning_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { largeCatalogThemeCount = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pendingBakArchive?.let { archive ->
        val installedVersionCode = themeManagerInspector.inspect().versionCode ?: 0L
        val versionsMatch = installedVersionCode == archive.backupVersionCode
        AlertDialog(
            onDismissRequest = {
                archive.discardStagedCopy()
                pendingBakArchive = null
            },
            title = { Text(stringResource(R.string.bak_restore_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.bak_restore_desc, archive.displayName, archive.entryCount))
                    Text(
                        stringResource(R.string.bak_restore_versions, archive.backupVersionCode, installedVersionCode),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (!versionsMatch) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = bakVersionMismatchAccepted,
                                onCheckedChange = { bakVersionMismatchAccepted = it },
                            )
                            Text(stringResource(R.string.bak_restore_mismatch_ack))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = versionsMatch || bakVersionMismatchAccepted,
                    onClick = {
                        if (bakImporting) return@TextButton
                        bakImporting = true
                        pendingBakArchive = null
                        scope.launch {
                            status = resources.getString(R.string.bak_restore_working)
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    check(privilegedRunner.isRootReadySilently()) {
                                        resources.getString(R.string.bak_restore_root_required)
                                    }
                                    bakImporter.restore(archive, installedVersionCode, bakVersionMismatchAccepted)
                                }
                            }.onSuccess {
                                // Restoring Theme Manager and mirroring it into Studio are separate
                                // operations.  A slow/unsupported catalog must not make a successful
                                // restore look failed or leave this confirmation flow spinning.
                                archive.discardStagedCopy()
                                reload(openThemesAfter = true)
                                status = resources.getString(R.string.bak_restore_success)
                                bakImporting = false

                                deviceImportRunning = true
                                themeDeviceImportStatus = resources.getString(R.string.device_import_working)
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { deviceThemeImporter.importAllThemes() }
                                    }.onSuccess { summary ->
                                        diagnostics.record(
                                            "bak_library_sync_completed",
                                            "BAK sonrası Temalar kitaplığı MTZ Studio ile eşitlendi",
                                            mapOf(
                                                "found" to summary.found,
                                                "added" to summary.added,
                                                "duplicates" to summary.duplicates,
                                                "failed" to summary.failed,
                                            ),
                                        )
                                        reload(openThemesAfter = true)
                                        themeDeviceImportStatus = resources.getString(
                                            R.string.device_theme_import_summary,
                                            summary.found,
                                            summary.added,
                                            summary.duplicates,
                                            summary.failed,
                                        )
                                    }.onFailure { syncError ->
                                        diagnostics.record(
                                            "bak_library_sync_failed",
                                            "BAK geri yüklendi ancak MTZ Studio kitaplığı eşitlenemedi",
                                            error = syncError,
                                        )
                                        themeDeviceImportStatus = resources.getString(
                                            R.string.device_import_failed,
                                            syncError.message ?: syncError::class.simpleName,
                                        )
                                    }
                                    deviceImportRunning = false
                                }
                            }.onFailure { error ->
                                archive.discardStagedCopy()
                                status = resources.getString(R.string.bak_restore_failed, error.message ?: error::class.simpleName)
                                diagnostics.record("bak_restore_failed", "BAK geri yükleme işlemi tamamlanamadı", error = error)
                                bakImporting = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.bak_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    archive.discardStagedCopy()
                    pendingBakArchive = null
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    operationError?.let { message ->
        AlertDialog(
            onDismissRequest = { operationError = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { operationError = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    pendingApplyTheme?.let { theme ->
        AlertDialog(
            onDismissRequest = { pendingApplyTheme = null },
            title = {
                Text(
                    stringResource(
                        if (rootAccessAvailable != true) R.string.rootless_apply_dialog_title
                        else R.string.apply_dialog_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (rootAccessAvailable != true) R.string.rootless_apply_dialog_desc
                        else R.string.apply_dialog_desc,
                        theme.archive.metadata?.name ?: theme.displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (themeOperationRunning) return@TextButton
                        themeOperationRunning = true
                        pendingApplyTheme = null
                        launchThemeOperation {
                            status = resources.getString(R.string.status_preparing_apply)
                            diagnostics.record("apply_requested", "Tema uygulama istendi", mapOf("theme" to theme.displayName, "themeId" to theme.id.value))
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    if (rootAccessAvailable != true) {
                                        themeApplyCoordinator.prepareRootlessManualImport(theme)
                                    } else {
                                        themeApplyCoordinator.prepare(theme, deviceThemeImporter.localIdFor(theme))
                                    }
                                }
                            }.onSuccess { prepared ->
                                if (prepared.protocol == ThemeApplyProtocol.ROOTLESS_MANUAL_IMPORT ||
                                    prepared.protocol == ThemeApplyProtocol.ROOTLESS_LEGACY_TESTER
                                ) {
                                    RootlessRestoreAssistant.remember(context, prepared)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        rootlessNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                if (prepared.protocol == ThemeApplyProtocol.MODERN_THEME_MANAGER_MANUAL_IMPORT ||
                                    prepared.protocol == ThemeApplyProtocol.ROOTLESS_MANUAL_IMPORT
                                ) {
                                    status = resources.getString(
                                        R.string.status_manual_import_ready,
                                        prepared.manualImportPath.orEmpty(),
                                    )
                                    Toast.makeText(
                                        context,
                                        resources.getString(
                                            R.string.manual_import_toast,
                                            prepared.manualImportPath.orEmpty().substringAfterLast('/'),
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                launchPreparedTheme(prepared)
                            }.onFailure { error ->
                                themeOperationRunning = false
                                diagnostics.record("apply_prepare_failed", "Tema uygulama hazırlanamadı", error = error)
                                status = resources.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
                                operationError = status
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (rootAccessAvailable != true) R.string.action_open_in_themes
                            else R.string.action_apply,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingApplyTheme = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

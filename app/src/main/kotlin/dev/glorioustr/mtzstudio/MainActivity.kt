package dev.glorioustr.mtzstudio

import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import dev.glorioustr.mtzstudio.shevery.PreferredPrivilegedCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

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
        val deviceThemeImporter = DeviceThemeImporter(
            context = applicationContext,
            library = library,
            composer = composer,
            commandRunner = privilegedRunner,
        )
        val diagnostics = LiveDiagnosticsRecorder(applicationContext)
        val appearanceStore = AppearanceStore(applicationContext)
        ThemeProtectionServiceClient.initialize(applicationContext)
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
                    deviceThemeImporter = deviceThemeImporter,
                    diagnostics = diagnostics,
                    documentDiagnostics = ::documentDiagnostics,
                    openInput = contentResolver::openInputStream,
                    openOutput = { uri -> contentResolver.openOutputStream(uri) },
                    shareMtz = { share(it, "application/zip", "Export MTZ") },
                    shareDiagnostics = { share(it, "text/plain", "Export diagnostics") },
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
    deviceThemeImporter: DeviceThemeImporter,
    diagnostics: LiveDiagnosticsRecorder,
    documentDiagnostics: (Uri) -> SelectedDocumentDiagnostics,
    openInput: (Uri) -> InputStream?,
    openOutput: (Uri) -> OutputStream?,
    shareMtz: (Path) -> Unit,
    shareDiagnostics: (Path) -> Unit,
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    contentStyle: AppContentStyle,
    onContentStyleChange: (AppContentStyle) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var themes by remember { mutableStateOf<List<LibraryTheme>>(emptyList()) }
    var status by remember { mutableStateOf(context.getString(R.string.status_loading_library)) }
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
    var pendingApplyTheme by remember { mutableStateOf<LibraryTheme?>(null) }
    var preparedApply by remember { mutableStateOf<PreparedThemeApply?>(null) }
    var backupStatus by remember { mutableStateOf(context.getString(R.string.status_no_backup_yet)) }
    val cloudAccountStore = remember { CloudAccountStore(context) }
    var cloudAccount by remember { mutableStateOf(cloudAccountStore.load()) }
    var customHomeWallpaperUri by rememberSaveable { mutableStateOf<String?>(null) }
    var customLockWallpaperUri by rememberSaveable { mutableStateOf<String?>(null) }
    var baseThemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var themeDeviceImportStatus by remember { mutableStateOf(context.getString(R.string.device_import_idle)) }
    var fontDeviceImportStatus by remember { mutableStateOf(context.getString(R.string.device_import_idle)) }
    var deviceImportRunning by remember { mutableStateOf(false) }
    var availableDeviceThemes by remember { mutableStateOf<List<DeviceThemeSummary>>(emptyList()) }
    var isScanningDeviceThemes by remember { mutableStateOf(false) }
    var showDeviceThemePicker by remember { mutableStateOf(false) }
    var showThemeProtectionRestartDialog by remember { mutableStateOf(false) }
    val studioState = remember { context.getSharedPreferences("studio-ui-state", 0) }
    var activeThemeId by rememberSaveable {
        mutableStateOf(studioState.getString("last-applied-theme-id", null))
    }

    val homeWallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            customHomeWallpaperUri = uri.toString()
            status = context.getString(R.string.custom_home_wallpaper_selected)
        }
    }

    val lockWallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            customLockWallpaperUri = uri.toString()
            status = context.getString(R.string.custom_lock_wallpaper_selected)
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
                backupStatus = context.getString(R.string.status_cloud_connected, accountName)
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

    val applyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val prepared = preparedApply
        preparedApply = null
        if (prepared != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(10_000)
                    runCatching { themeApplyCoordinator.cleanup(prepared) }
                }
            }
            status = context.getString(R.string.status_apply_success, prepared.themeName)
            activeThemeId = prepared.themeId
            studioState.edit().putString("last-applied-theme-id", prepared.themeId).apply()
        }
    }

    SheveryAuthorizationGate()

    fun reload(openThemesAfter: Boolean = false) {
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) { library.load() }
            themes = snapshot.themes
            status = when {
                snapshot.warnings.isNotEmpty() -> context.getString(R.string.status_library_warnings, snapshot.warnings.size)
                themes.isEmpty() -> context.getString(R.string.status_library_empty)
                else -> context.getString(R.string.status_library_ready)
            }
            if (openThemesAfter && snapshot.warnings.isEmpty()) destination = StudioDestination.THEMES
        }
    }

    fun deleteTheme(theme: LibraryTheme) {
        scope.launch {
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
                status = context.getString(R.string.status_theme_removed, theme.archive.metadata?.name ?: theme.displayName)
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
        scope.launch {
            status = context.getString(R.string.status_composing)
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
                            )
                        },
                        customHomeWallpaperBytes = homeBytes,
                        customLockWallpaperBytes = lockBytes,
                        generatedPreviewBytes = generatedPreview,
                    )
                    val result = composer.compose(request, library.newExportPath(compositionName))
                    library.recordComposition(result)
                    java.nio.file.Files.newInputStream(result.output).use { input ->
                        library.importTheme(
                            input = input,
                            suggestedName = compositionName.trim(),
                            includeInThemeGallery = true,
                        )
                    }
                    // Export copy to device public Downloads/MTZ Studio folder
                    MtzPublicExporter.exportToPublicDownloads(context, result.output, compositionName.trim())
                    result
                }
            }.onSuccess { result ->
                lastResult = result
                val snapshot = withContext(Dispatchers.IO) { library.load() }
                themes = snapshot.themes
                destination = StudioDestination.THEMES
                status = context.getString(R.string.status_compose_success, compositionName.trim())
            }.onFailure { error ->
                status = context.getString(R.string.status_compose_failed, error.message ?: error::class.simpleName)
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
                themeDeviceImportStatus = context.getString(R.string.device_import_failed, err.message ?: "Failed to scan")
            }
        }
    }

    fun importSelectedDeviceThemes(selectedIds: Set<String>) {
        if (deviceImportRunning || selectedIds.isEmpty()) return
        scope.launch {
            deviceImportRunning = true
            themeDeviceImportStatus = context.getString(R.string.device_import_working)
            runCatching {
                withContext(Dispatchers.IO) { deviceThemeImporter.importSelectedThemes(selectedIds) }
            }.onSuccess { summary ->
                reload(openThemesAfter = true)
                themeDeviceImportStatus = context.getString(
                    R.string.device_theme_import_summary,
                    summary.found,
                    summary.added,
                    summary.duplicates,
                    summary.failed,
                )
                if (summary.errors.isNotEmpty()) status = summary.errors.take(3).joinToString("\n")
            }.onFailure { error ->
                themeDeviceImportStatus = context.getString(
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
            if (fontOnly) fontDeviceImportStatus = context.getString(R.string.device_import_working)
            else themeDeviceImportStatus = context.getString(R.string.device_import_working)
            if (fontOnly) {
                runCatching {
                    withContext(Dispatchers.IO) { deviceThemeImporter.importActiveFont() }
                }.onSuccess { result ->
                    destination = StudioDestination.FONTS
                    reload()
                    val name = result.theme.archive.metadata?.name ?: result.theme.displayName
                    fontDeviceImportStatus = context.getString(
                        if (result.addedToLibrary) R.string.device_import_added else R.string.device_import_duplicate,
                        name,
                    )
                }.onFailure { error ->
                    fontDeviceImportStatus = context.getString(
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

    fun exportTheme(theme: LibraryTheme) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { library.exportTheme(theme) }
            }.onSuccess(shareMtz).onFailure { error ->
                status = context.getString(
                    R.string.status_export_failed,
                    error.message ?: error::class.simpleName,
                )
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            diagnostics.recordPickerResultReceived()
            scope.launch {
                status = context.getString(R.string.status_copying_verifying)
                var diagnosticSession: ImportDiagnosticSession? = null
                runCatching {
                    withContext(Dispatchers.IO) {
                        val document = documentDiagnostics(uri)
                        val session = diagnostics.beginImport(document)
                        diagnosticSession = session
                        openInput(uri)?.use { input ->
                            library.importTheme(input, document.displayName, session.observer)
                        } ?: run {
                            val openErr = context.getString(R.string.error_open_document)
                            session.failBeforeImport(openErr)
                            error(openErr)
                        }
                    }
                }.onSuccess {
                    reload(openThemesAfter = true)
                }.onFailure { error ->
                    diagnosticSession?.failBeforeImport(error.message ?: error::class.simpleName ?: "unknown error")
                    status = context.getString(R.string.status_import_rejected, error.message ?: error::class.simpleName)
                }
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                backupStatus = context.getString(R.string.status_backup_preparing)
                runCatching {
                    withContext(Dispatchers.IO) {
                        openOutput(uri)?.use { output -> backupManager.create(output) }
                            ?: error(context.getString(R.string.error_backup_target))
                    }
                }.onSuccess { summary ->
                    backupStatus = context.getString(R.string.status_backup_success, summary.themeCount, summary.fileCount)
                }.onFailure { error ->
                    backupStatus = context.getString(R.string.status_backup_failed, error.message ?: error::class.simpleName)
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                backupStatus = context.getString(R.string.status_restore_preparing)
                runCatching {
                    withContext(Dispatchers.IO) {
                        openInput(uri)?.use { input -> backupManager.restore(input) }
                            ?: error(context.getString(R.string.error_restore_target))
                    }
                }.onSuccess { summary ->
                    reload()
                    backupStatus = context.getString(R.string.status_restore_success, summary.themeCount, summary.fileCount)
                }.onFailure { error ->
                    backupStatus = context.getString(R.string.status_restore_failed, error.message ?: error::class.simpleName)
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        ThemeProtectionServiceClient.setCommandRunner { cmd ->
            runCatching { privilegedRunner.run(cmd, 3).output }.getOrNull()
        }
        ThemeProtectionServiceClient.refresh()
        reload()
    }
    BackHandler(enabled = destination != StudioDestination.HOME, onBack = ::navigateBack)

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
                            modifier = Modifier.semantics { contentDescription = context.getString(R.string.menu_title) },
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
                importing = diagnosticState.activeSessionId != null,
                themeManagerInspector = themeManagerInspector,
                themeManagerUpdater = themeManagerUpdater,
                openInput = openInput,
                onToggleImport = { importExpanded = !importExpanded },
                onAddMtz = {
                    diagnostics.recordPickerLaunched()
                    picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                onNavigate = { navigateTo(it) },
                modifier = contentModifier,
            )
            destination == StudioDestination.THEMES -> ThemesScreen(
                themes = themes,
                activeThemeId = activeThemeId,
                deviceImportStatus = themeDeviceImportStatus,
                deviceImportRunning = deviceImportRunning,
                onOpenDeviceThemePicker = ::openDeviceThemePicker,
                onApplyTheme = { pendingApplyTheme = it },
                onDeleteTheme = ::deleteTheme,
                modifier = contentModifier,
            )
            destination == StudioDestination.PERSONALIZE -> PersonalizeScreen(
                themes = themes,
                selections = selections,
                compositionName = compositionName,
                compositionMakerName = compositionMakerName,
                lastResult = lastResult,
                status = status,
                baseThemeId = baseThemeId,
                onSelectBaseTheme = { theme ->
                    baseThemeId = theme?.id?.value
                    if (theme != null) {
                        theme.archive.components.forEach { comp ->
                            if (comp.category.isPersonalizationOption()) {
                                selections[comp.category] = UiSelection(theme.id, comp.category, comp.rootPath)
                            }
                        }
                        val themeName = theme.archive.metadata?.name ?: theme.displayName
                        compositionName = if (themeName.endsWith(" Karmam", ignoreCase = true)) {
                            themeName
                        } else {
                            "$themeName Karmam"
                        }
                    } else {
                        selections.clear()
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
                    backupStatus = context.getString(R.string.status_cloud_connected, account.accountName)
                },
                onDisconnectCloud = {
                    cloudAccountStore.disconnect()
                    cloudAccount = cloudAccountStore.load()
                    backupStatus = context.getString(R.string.status_cloud_disconnected)
                },
                onBackupCloud = {
                    scope.launch {
                        backupStatus = context.getString(R.string.status_cloud_uploading, cloudAccount.provider.displayName)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                cloudAccountStore.openCloudBackupOutputStream().use { output ->
                                    backupManager.create(output)
                                }
                            }
                        }.onSuccess { summary ->
                            cloudAccountStore.recordBackup()
                            cloudAccount = cloudAccountStore.load()
                            backupStatus = context.getString(R.string.status_cloud_upload_success, cloudAccount.provider.displayName)
                        }.onFailure { error ->
                            backupStatus = context.getString(R.string.status_backup_failed, error.message ?: error::class.simpleName)
                        }
                    }
                },
                onRestoreCloud = {
                    scope.launch {
                        if (!cloudAccountStore.hasCloudBackup()) {
                            backupStatus = context.getString(R.string.status_cloud_no_backup, cloudAccount.provider.displayName)
                            return@launch
                        }
                        backupStatus = context.getString(R.string.status_cloud_downloading, cloudAccount.provider.displayName)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                cloudAccountStore.openCloudBackupInputStream()?.use { input ->
                                    backupManager.restore(input)
                                } ?: error(context.getString(R.string.status_cloud_no_backup, cloudAccount.provider.displayName))
                            }
                        }.onSuccess { summary ->
                            reload()
                            backupStatus = context.getString(R.string.status_restore_success, summary.themeCount, summary.fileCount)
                        }.onFailure { error ->
                            backupStatus = context.getString(R.string.status_restore_failed, error.message ?: error::class.simpleName)
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
                            status = context.getString(R.string.theme_protection_manager_missing)
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.theme_protection_manager_open_failed, result.detail),
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
                themes = themes,
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
                modifier = contentModifier,
            )
        }
    }

    if (appMenuExpanded) {
        StudioOverlayMenu(
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
                            status = context.getString(
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

    pendingApplyTheme?.let { theme ->
        AlertDialog(
            onDismissRequest = { pendingApplyTheme = null },
            title = { Text(stringResource(R.string.apply_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.apply_dialog_desc,
                        theme.archive.metadata?.name ?: theme.displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingApplyTheme = null
                        scope.launch {
                            status = context.getString(R.string.status_preparing_apply)
                            runCatching {
                                withContext(Dispatchers.IO) { themeApplyCoordinator.prepare(theme) }
                            }.onSuccess { prepared ->
                                preparedApply = prepared
                                applyLauncher.launch(prepared.intent)
                            }.onFailure { error ->
                                status = context.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingApplyTheme = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

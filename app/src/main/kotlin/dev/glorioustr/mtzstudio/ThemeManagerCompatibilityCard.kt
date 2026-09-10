package dev.glorioustr.mtzstudio

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.glorioustr.mtzstudio.tester.InstalledThemeManager
import dev.glorioustr.mtzstudio.tester.RootThemeManagerUpdater
import dev.glorioustr.mtzstudio.tester.ThemeManagerContract
import dev.glorioustr.mtzstudio.tester.ThemeManagerInspector
import dev.glorioustr.mtzstudio.tester.ThemeManagerCapabilityProbe
import dev.glorioustr.mtzstudio.tester.VerifiedThemeManagerApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
internal fun ThemeManagerCompatibilityCard(
    inspector: ThemeManagerInspector,
    updater: RootThemeManagerUpdater,
    openInput: (Uri) -> InputStream?,
    allowRootDowngrade: Boolean,
) {
    val recommendedApkName = "Xiaomi_Themes_3.0.5.6-global.apk"
    val recommendedDownloadUrl =
        "https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/download/v4.0.2/$recommendedApkName"
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<InstalledThemeManager?>(null) }
    var runtimeProfile by remember { mutableStateOf<dev.glorioustr.mtzstudio.tester.ThemeManagerRuntimeProfile?>(null) }
    var verifiedApk by remember { mutableStateOf<VerifiedThemeManagerApk?>(null) }
    var status by remember { mutableStateOf(resources.getString(R.string.tm_checking_version)) }
    var riskAccepted by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    val cyanAccent = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF006A78) else Color(0xFF00DAF3)

    fun openRecommendedDownload() {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(recommendedDownloadUrl))
                .setTitle(recommendedApkName)
                .setDescription("Xiaomi Themes ${ThemeManagerContract.RECOMMENDED_VERSION}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, recommendedApkName)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }.onSuccess {
            status = "Xiaomi Themes ${ThemeManagerContract.RECOMMENDED_VERSION} indiriliyor…"
        }.onFailure { error ->
            status = resources.getString(R.string.status_apply_failed, error.message ?: error::class.simpleName)
        }
    }

    fun refresh() {
        scope.launch {
            val detected = withContext(Dispatchers.IO) { inspector.inspect() }
            installed = detected
            val profile = withContext(Dispatchers.IO) { ThemeManagerCapabilityProbe(context).probe(detected) }
            runtimeProfile = profile
            status = if (profile.compatibleLocalMtzPath) {
                resources.getString(R.string.tm_recommended_active)
            } else {
                resources.getString(R.string.tm_recommendation_notice, ThemeManagerContract.RECOMMENDED_VERSION)
            }
        }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val current = installed ?: withContext(Dispatchers.IO) { inspector.inspect() }.also { installed = it }
                status = resources.getString(R.string.tm_verifying_apk)
                runCatching {
                    withContext(Dispatchers.IO) {
                        openInput(uri)?.use { updater.stageAndVerify(it, current) }
                            ?: error(resources.getString(R.string.tm_error_apk_open))
                    }
                }.onSuccess { apk ->
                    verifiedApk?.let { previous -> withContext(Dispatchers.IO) { updater.discard(previous) } }
                    verifiedApk = apk
                    riskAccepted = false
                    status = resources.getString(R.string.tm_apk_verified, apk.versionName, apk.sha256)
                }.onFailure { error ->
                    status = resources.getString(R.string.tm_apk_rejected, error.message ?: error::class.simpleName)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }
    DisposableEffect(verifiedApk) {
        val staged = verifiedApk
        onDispose { staged?.let { runCatching { updater.discard(it) } } }
    }

    StudioCard(Modifier.fillMaxWidth()) {
        val compatibleLocalMtzPath = runtimeProfile?.compatibleLocalMtzPath == true
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Color(0xFF00BCD4).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(24.dp))
                }
                Text(stringResource(R.string.tm_card_title), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        stringResource(
                            if (runtimeProfile == null) R.string.tm_profile_checking
                            else if (compatibleLocalMtzPath) R.string.tm_profile_active
                            else R.string.tm_profile_checking,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            installed?.let { current ->
                val applyActivityUnavailable = runtimeProfile != null && current.installed && !compatibleLocalMtzPath
                Text(
                    if (!current.installed) stringResource(R.string.tm_device_not_found)
                    else if (compatibleLocalMtzPath) stringResource(
                        R.string.tm_device_installed_compatible,
                        current.versionName ?: stringResource(R.string.tm_version_unknown),
                    ) else stringResource(
                        R.string.tm_device_installed_incompatible,
                        current.versionName ?: stringResource(R.string.tm_version_unknown),
                    ),
                    color = if (compatibleLocalMtzPath) cyanAccent else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (compatibleLocalMtzPath) {
                    Text(
                        stringResource(R.string.tm_panel_compatible_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(
                                if (runtimeProfile?.legacyTesterResolvable == true ||
                                    runtimeProfile?.modernLocalLibraryResolvable == true ||
                                    runtimeProfile?.publicMtzImportResolvable == true
                                ) {
                                    R.string.tm_panel_native_ready
                                } else {
                                    R.string.tm_panel_bridge_ready
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
                if (applyActivityUnavailable) {
                    Text(current.behavior.explanation, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.tm_rootless_import_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Compatibility requires a runtime-resolvable Xiaomi route. Legacy Global builds
                // use ApplyThemeForScreenshot; 10.8.7.6+ builds use the native local library.
                // The modern version family alone is not enough if its component is missing.
                if (applyActivityUnavailable) {
                    OutlinedButton(onClick = ::openRecommendedDownload) {
                        Text("Themes ${ThemeManagerContract.RECOMMENDED_VERSION} APK indir")
                    }
                }
            }
            if (runtimeProfile != null && !compatibleLocalMtzPath) Text(status, style = MaterialTheme.typography.bodySmall)

            val current = installed
            if (allowRootDowngrade && current != null && current.installed && runtimeProfile != null && !compatibleLocalMtzPath) {
                OutlinedButton(
                    onClick = {
                        apkPicker.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream",
                                "*/*",
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.tm_btn_select_apk_version, ThemeManagerContract.RECOMMENDED_VERSION)) }

                verifiedApk?.let { apk ->
                    Text(
                        stringResource(R.string.tm_verified_apk_info, apk.packageName, apk.versionName, apk.sha256),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = riskAccepted, onCheckedChange = { riskAccepted = it })
                        Text(
                            stringResource(R.string.tm_risk_checkbox),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        enabled = riskAccepted,
                        onClick = { showConfirmation = true },
                    ) { Text(stringResource(R.string.tm_btn_downgrade_to_version, ThemeManagerContract.RECOMMENDED_VERSION)) }
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(stringResource(R.string.tm_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.tm_dialog_text,
                        verifiedApk?.packageName ?: "",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        val apk = verifiedApk ?: return@TextButton
                        scope.launch {
                            status = resources.getString(R.string.tm_status_waiting_root)
                            runCatching {
                                withContext(Dispatchers.IO) { updater.installVerifiedDowngrade(apk) }
                            }.onSuccess { result ->
                                installed = result.installedAfter
                                status = buildString {
                                    append(result.message).append(" · ").append(result.authorizationSource)
                                    if (result.commandOutput.isNotBlank()) append(" · ").append(result.commandOutput)
                                }
                                if (result.success) {
                                    withContext(Dispatchers.IO) { updater.discard(apk) }
                                    verifiedApk = null
                                    riskAccepted = false
                                }
                            }.onFailure { error ->
                                status = resources.getString(R.string.tm_status_root_failed, error.message ?: error::class.simpleName)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.tm_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

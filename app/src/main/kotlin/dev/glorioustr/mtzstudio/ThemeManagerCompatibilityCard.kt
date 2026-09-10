package dev.glorioustr.mtzstudio

import android.content.Intent
import android.net.Uri
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
    // APKMirror's version page requires an extra tap.  This is its stable download-start route
    // for the verified universal 3.0.5.6-global package, so the browser immediately starts the
    // download without MTZ Studio ever handling or installing a third-party system APK itself.
    val recommendedDownloadUrl =
        "https://www.apkmirror.com/apk/xiaomi-inc/miui-theme-app/xiaomi-themes-3-0-5-6-global-release/" +
            "xiaomi-themes-3-0-5-6-global-android-apk-download/download/?key=e5010769a82e6509e696d27f7b61561b9b8db5fd"
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
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(recommendedDownloadUrl),
                ),
            )
        }
    }

    fun refresh() {
        scope.launch {
            val detected = withContext(Dispatchers.IO) { inspector.inspect() }
            installed = detected
            runtimeProfile = withContext(Dispatchers.IO) { ThemeManagerCapabilityProbe(context).probe(detected) }
            status = if (detected.isRecommended) {
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
                        stringResource(if (installed?.isRecommended == true) R.string.tm_profile_active else R.string.tm_profile_checking),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            installed?.let { current ->
                val rootlessImportUnavailable = runtimeProfile?.let {
                    current.installed && !it.legacyTesterResolvable && !it.publicMtzImportResolvable
                } == true
                val needsRecommendedVersion = current.installed && !current.isRecommended
                Text(
                    if (current.installed && current.isRecommended) {
                        stringResource(R.string.tm_panel_version_approved, current.versionName ?: ThemeManagerContract.RECOMMENDED_VERSION)
                    } else if (current.installed) {
                        stringResource(R.string.tm_device_installed_incompatible, current.versionName ?: stringResource(R.string.tm_version_unknown))
                    } else stringResource(R.string.tm_device_not_found),
                    color = if (current.isRecommended) cyanAccent else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (current.isRecommended) {
                    Text(
                        stringResource(R.string.tm_panel_compatible_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(
                                if (runtimeProfile?.legacyTesterResolvable == true || runtimeProfile?.publicMtzImportResolvable == true) {
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
                if (needsRecommendedVersion) {
                    Text(current.behavior.explanation, style = MaterialTheme.typography.bodySmall)
                }
                if (rootlessImportUnavailable) {
                    Text(
                        stringResource(R.string.tm_rootless_import_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // All modes receive an explicit recovery route.  The version test tells users
                // why a Xiaomi internal apply screen cannot be opened; the capability probe
                // catches builds that advertise no usable external MTZ import surface at all.
                if (needsRecommendedVersion || rootlessImportUnavailable) {
                    OutlinedButton(onClick = ::openRecommendedDownload) {
                        Text("Themes ${ThemeManagerContract.RECOMMENDED_VERSION} APK indir")
                    }
                }
            }
            if (installed?.isRecommended != true) Text(status, style = MaterialTheme.typography.bodySmall)

            val current = installed
            if (allowRootDowngrade && current != null && current.installed && !current.isRecommended) {
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

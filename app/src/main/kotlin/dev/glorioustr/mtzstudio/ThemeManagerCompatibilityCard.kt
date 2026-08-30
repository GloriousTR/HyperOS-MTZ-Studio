package dev.glorioustr.mtzstudio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.glorioustr.mtzstudio.tester.InstalledThemeManager
import dev.glorioustr.mtzstudio.tester.RootThemeManagerUpdater
import dev.glorioustr.mtzstudio.tester.ThemeManagerContract
import dev.glorioustr.mtzstudio.tester.ThemeManagerInspector
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
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<InstalledThemeManager?>(null) }
    var verifiedApk by remember { mutableStateOf<VerifiedThemeManagerApk?>(null) }
    var status by remember { mutableStateOf(resources.getString(R.string.tm_checking_version)) }
    var riskAccepted by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            val detected = withContext(Dispatchers.IO) { inspector.inspect() }
            installed = detected
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tm_card_title), fontWeight = FontWeight.Bold)
            installed?.let { current ->
                Text(
                    if (current.installed) {
                        if (current.isRecommended) {
                            stringResource(
                                R.string.tm_device_installed_compatible,
                                current.versionName ?: ThemeManagerContract.RECOMMENDED_VERSION,
                            )
                        } else {
                            stringResource(
                                R.string.tm_device_installed_incompatible,
                                current.versionName ?: stringResource(R.string.tm_version_unknown),
                            )
                        }
                    } else {
                        stringResource(R.string.tm_device_not_found)
                    },
                )
                if (!current.isRecommended) {
                    Text(current.behavior.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (installed?.isRecommended != true) Text(status, style = MaterialTheme.typography.bodySmall)

            val current = installed
            if (current != null && current.installed && !current.isRecommended) {
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

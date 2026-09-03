package dev.glorioustr.mtzstudio

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.glorioustr.mtzstudio.shevery.SheveryAccess
import dev.glorioustr.mtzstudio.shevery.SheveryAuthorizationStatus
import dev.glorioustr.mtzstudio.shevery.PreferredPrivilegedCommandRunner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SheveryAuthorizationGate(runner: PreferredPrivilegedCommandRunner, onAuthorized: () -> Unit) {
    val context = LocalContext.current
    val access = remember { SheveryAccess(context.applicationContext) }
    var status by remember { mutableStateOf(SheveryAuthorizationStatus.SERVICE_NOT_RUNNING) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var dismissedForSession by rememberSaveable { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val failure by runner.authorizationFailure.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnAuthorized by rememberUpdatedState(onAuthorized)
    var checking by remember { mutableStateOf(false) }
    var permissionRequestFailed by remember { mutableStateOf(false) }

    fun verifyAccess() {
        if (checking) return
        checking = true
        scope.launch {
            val ready = withContext(Dispatchers.IO) {
                runner.accessModeSilently() != StudioAccessMode.STANDARD
            }
            checking = false
            if (ready) {
                runner.dismissAuthorizationFailure()
                showPermissionDialog = false
                currentOnAuthorized()
            }
        }
    }

    // Permission results and binder arrival can happen long after startup.
    DisposableEffect(lifecycle, runner) {
        val received = Shizuku.OnBinderReceivedListener { refreshToken++ }
        val dead = Shizuku.OnBinderDeadListener { refreshToken++ }
        val permission = Shizuku.OnRequestPermissionResultListener { request, result ->
            if (request == SHEVERY_PERMISSION_REQUEST) {
                refreshToken++
                if (result == PackageManager.PERMISSION_GRANTED) verifyAccess()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        lifecycle.addObserver(observer)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshToken) {
        status = access.status()
        if (status == SheveryAuthorizationStatus.PERMISSION_REQUIRED && !dismissedForSession) {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog || failure != null) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                dismissedForSession = true
                runner.dismissAuthorizationFailure()
            },
            title = { Text(stringResource(if (failure != null) R.string.privileged_dialog_title else R.string.shevery_dialog_title)) },
            text = {
                Text(if (permissionRequestFailed) stringResource(R.string.privileged_permission_request_failed)
                else failure ?: stringResource(R.string.shevery_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissedForSession = true
                        permissionRequestFailed = false
                        if (access.status() == SheveryAuthorizationStatus.PERMISSION_REQUIRED) {
                            runCatching { access.requestPermission(SHEVERY_PERMISSION_REQUEST) }
                                .onFailure { error ->
                                    permissionRequestFailed = true
                                    LiveDiagnosticsRecorder.get(context).record(
                                        "compatible_permission_request_failed", "Shizuku uyumlu izin isteği gönderilemedi",
                                        error = error,
                                    )
                                }
                        } else {
                            verifyAccess()
                        }
                        refreshToken++
                    },
                    enabled = !checking,
                ) { Text(stringResource(if (status == SheveryAuthorizationStatus.PERMISSION_REQUIRED) R.string.shevery_btn_grant else R.string.privileged_btn_retry)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        dismissedForSession = true
                        runner.dismissAuthorizationFailure()
                    },
                ) { Text(stringResource(R.string.shevery_btn_skip)) }
            },
        )
    }
}

private const val SHEVERY_PERMISSION_REQUEST = 52046

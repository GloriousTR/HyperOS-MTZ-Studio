package dev.glorioustr.mtzstudio

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun SheveryAuthorizationGate() {
    val context = LocalContext.current
    val access = remember { SheveryAccess(context.applicationContext) }
    var status by remember { mutableStateOf(SheveryAuthorizationStatus.SERVICE_NOT_RUNNING) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var dismissedForSession by rememberSaveable { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshToken) {
        repeat(AUTHORIZATION_CHECK_ATTEMPTS) {
            val detected = access.status()
            status = if (detected == SheveryAuthorizationStatus.ROOT_READY) {
                val verified = withContext(Dispatchers.IO) { access.verifyRootService() }
                if (verified) detected else SheveryAuthorizationStatus.SERVICE_NOT_RUNNING
            } else {
                detected
            }
            when (status) {
                SheveryAuthorizationStatus.PERMISSION_REQUIRED -> {
                    if (!dismissedForSession) showPermissionDialog = true
                }
                SheveryAuthorizationStatus.ROOT_READY -> {
                    showPermissionDialog = false
                    return@LaunchedEffect
                }
                else -> Unit
            }
            delay(AUTHORIZATION_RECHECK_MILLIS)
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                dismissedForSession = true
            },
            title = { Text(stringResource(R.string.shevery_dialog_title)) },
            text = {
                Text(stringResource(R.string.shevery_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        dismissedForSession = true
                        access.requestPermission(SHEVERY_PERMISSION_REQUEST)
                        refreshToken++
                    },
                ) { Text(stringResource(R.string.shevery_btn_grant)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        dismissedForSession = true
                    },
                ) { Text(stringResource(R.string.shevery_btn_skip)) }
            },
        )
    }
}

private const val SHEVERY_PERMISSION_REQUEST = 52046
private const val AUTHORIZATION_CHECK_ATTEMPTS = 20
private const val AUTHORIZATION_RECHECK_MILLIS = 500L

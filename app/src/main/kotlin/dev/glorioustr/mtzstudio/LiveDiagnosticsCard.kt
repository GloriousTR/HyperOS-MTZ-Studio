package dev.glorioustr.mtzstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

@Composable
internal fun LiveDiagnosticsCard(
    recorder: LiveDiagnosticsRecorder,
    shareDiagnostics: (Path) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by recorder.state.collectAsState()
    val scope = rememberCoroutineScope()
    var actionStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        StudioCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.diag_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.diag_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.diag_phase, LiveDiagnosticsRecorder.phaseLabel(state.phase, context)),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (state.activeSessionId != null) {
                    val source = state.sourceBytes
                    if (source != null && source > 0) {
                        LinearProgressIndicator(
                            progress = { (state.bytesCopied.toFloat() / source).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${formatDiagnosticBytes(state.bytesCopied)} / ${formatDiagnosticBytes(source)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.diag_copied, formatDiagnosticBytes(state.bytesCopied)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                state.recoveredSession?.let {
                    Text(
                        stringResource(R.string.diag_interrupted_session, it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.orphanStagingCount > 0) {
                    Text(
                        stringResource(
                            R.string.diag_orphan_staging,
                            state.orphanStagingCount,
                            formatDiagnosticBytes(state.orphanStagingBytes),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { recorder.createExport() } }
                                .onSuccess(shareDiagnostics)
                                .onFailure { actionStatus = resources.getString(R.string.diag_export_failed, it.message ?: "") }
                        }
                    },
                ) { Text(stringResource(R.string.btn_export_diagnostics)) }

                actionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.recentEvents.isNotEmpty()) {
                    Text(stringResource(R.string.diag_recent_events), fontWeight = FontWeight.SemiBold)
                    state.recentEvents.asReversed().forEach { event ->
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(event, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatDiagnosticBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

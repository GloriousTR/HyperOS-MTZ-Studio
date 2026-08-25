package dev.glorioustr.mtzstudio

import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Path

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = ThemeLibrary(applicationContext)
        val composer = MtzComposer()
        setContent {
            MaterialTheme {
                StudioScreen(
                    library = library,
                    composer = composer,
                    displayName = ::displayName,
                    openInput = contentResolver::openInputStream,
                    share = ::share,
                )
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        val cursor: Cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun share(path: Path) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", path.toFile())
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("MTZ export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export MTZ"))
    }
}

private data class UiSelection(
    val themeId: ThemeId,
    val category: ComponentCategory,
    val rootPath: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StudioScreen(
    library: ThemeLibrary,
    composer: MtzComposer,
    displayName: (Uri) -> String?,
    openInput: (Uri) -> InputStream?,
    share: (Path) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var themes by remember { mutableStateOf<List<LibraryTheme>>(emptyList()) }
    var status by remember { mutableStateOf("Loading private library…") }
    var compositionName by remember { mutableStateOf("My HyperOS Mix") }
    var lastResult by remember { mutableStateOf<CompositionResult?>(null) }
    val selections = remember { mutableStateMapOf<ComponentCategory, UiSelection>() }

    fun reload() {
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) { library.load() }
            themes = snapshot.themes
            status = when {
                snapshot.warnings.isNotEmpty() -> "Loaded with ${snapshot.warnings.size} private-library warning(s)"
                themes.isEmpty() -> "Choose MTZ files you own to begin"
                else -> "${themes.size} verified source theme(s)"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Copying and validating selected MTZ…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        openInput(uri)?.use { input ->
                            library.importTheme(input, displayName(uri))
                        } ?: error("The selected document could not be opened")
                    }
                }.onSuccess {
                    reload()
                }.onFailure { error ->
                    status = "Import rejected: ${error.message ?: error::class.simpleName}"
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("HyperOS MTZ Studio") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Local MTZ inspection and composition. No permanent install, rights changes, DRM bypass, hooks, or root.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Text("Add MTZ")
                }
                Text(status, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }

            items(themes, key = { it.id.value }) { theme ->
                ThemeCard(theme, selections)
            }

            item {
                HorizontalDivider()
                OutlinedTextField(
                    value = compositionName,
                    onValueChange = { compositionName = it },
                    label = { Text("Output theme name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                val sourceCount = selections.values.map(UiSelection::themeId).distinct().size
                Text(
                    "${selections.size} component(s) from $sourceCount source theme(s)",
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = compositionName.isNotBlank() && selections.size >= 2 && sourceCount >= 2,
                        onClick = {
                            scope.launch {
                                status = "Composing and reopening output…"
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val byId = themes.associateBy { it.id }
                                        val request = CompositionRequest(
                                            metadata = CompositionMetadata(compositionName.trim()),
                                            selections = selections.values.map { selected ->
                                                val theme = byId.getValue(selected.themeId)
                                                ComponentSelection(
                                                    source = CompositionSource(theme.id, theme.displayName, theme.archive),
                                                    category = selected.category,
                                                    rootPath = selected.rootPath,
                                                )
                                            },
                                        )
                                        composer.compose(request, library.newExportPath(compositionName)).also(library::recordComposition)
                                    }
                                }.onSuccess { result ->
                                    lastResult = result
                                    status = "Verified output SHA-256: ${result.outputSha256}"
                                }.onFailure { error ->
                                    status = "Composition failed: ${error.message ?: error::class.simpleName}"
                                }
                            }
                        },
                    ) { Text("Compose & verify") }
                    if (lastResult != null) {
                        Button(onClick = { share(lastResult!!.output) }) { Text("Share") }
                    }
                }
                lastResult?.let { result ->
                    Text(
                        "Output: ${result.output.fileName}\nSHA-256: ${result.outputSha256}",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: LibraryTheme, selections: MutableMap<ComponentCategory, UiSelection>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(theme.archive.metadata?.name ?: theme.displayName, fontWeight = FontWeight.Bold)
            Text(theme.displayName, style = MaterialTheme.typography.bodySmall)
            Text("SHA-256 ${theme.archive.sha256}", style = MaterialTheme.typography.labelSmall)
            Text(
                "${theme.archive.entries.size} entries · ${theme.archive.expandedBytes} expanded bytes",
                style = MaterialTheme.typography.labelSmall,
            )
            if (theme.archive.rightsEntries.isNotEmpty()) {
                Text(
                    "Rights-related entries detected and report-only (${theme.archive.rightsEntries.size}); they will not be composed.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            theme.archive.components.forEach { component ->
                val selected = selections[component.category]
                val checked = selected?.themeId == theme.id && selected.rootPath == component.rootPath
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                selections[component.category] = UiSelection(theme.id, component.category, component.rootPath)
                            } else if (checked) {
                                selections.remove(component.category)
                            }
                        },
                    )
                    Text("${component.category.label}: ${component.rootPath} (${component.entryPaths.size})")
                }
            }
        }
    }
}

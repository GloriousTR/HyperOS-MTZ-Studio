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
import androidx.compose.material3.Card
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
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<InstalledThemeManager?>(null) }
    var verifiedApk by remember { mutableStateOf<VerifiedThemeManagerApk?>(null) }
    var status by remember { mutableStateOf("Tema Yöneticisi sürümü denetleniyor…") }
    var riskAccepted by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            installed = withContext(Dispatchers.IO) { inspector.inspect() }
            status = if (installed!!.isRecommended) {
                "Önerilen sürüm etkin. Bağımsız yerel MTZ import davranışı bekleniyor."
            } else {
                "Bağımsız yerel MTZ importu için ${ThemeManagerContract.RECOMMENDED_VERSION} önerilir."
            }
        }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val current = installed ?: withContext(Dispatchers.IO) { inspector.inspect() }.also { installed = it }
                status = "APK paket, sürüm, SHA-256 ve Xiaomi imzası doğrulanıyor…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        openInput(uri)?.use { updater.stageAndVerify(it, current) }
                            ?: error("Seçilen APK açılamadı")
                    }
                }.onSuccess { apk ->
                    verifiedApk?.let { previous -> withContext(Dispatchers.IO) { updater.discard(previous) } }
                    verifiedApk = apk
                    riskAccepted = false
                    status = "APK doğrulandı: ${apk.versionName} · SHA-256 ${apk.sha256}"
                }.onFailure { error ->
                    status = "APK reddedildi: ${error.message ?: error::class.simpleName}"
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(verifiedApk) {
        val staged = verifiedApk
        onDispose { staged?.let { runCatching { updater.discard(it) } } }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Tema Yöneticisi uyumluluğu", fontWeight = FontWeight.Bold)
            installed?.let { current ->
                Text(
                    if (current.installed) {
                        "Cihaz: ${current.packageName} · ${current.versionName ?: "sürüm bilinmiyor"}"
                    } else {
                        "Desteklenen Xiaomi Tema Yöneticisi paketi bulunamadı"
                    },
                )
                Text(current.behavior.explanation, style = MaterialTheme.typography.bodySmall)
            }
            Text(status, style = MaterialTheme.typography.bodySmall)

            CompatibilityMatrix()

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
                ) { Text("2.15.5.46 APK seç") }

                verifiedApk?.let { apk ->
                    Text(
                        "Doğrulanan APK: ${apk.packageName} ${apk.versionName}\nSHA-256: ${apk.sha256}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = riskAccepted, onCheckedChange = { riskAccepted = it })
                        Text(
                            "Root ile sürüm düşürmenin Tema Yöneticisini yeniden başlatabileceğini ve ROM tarafından reddedilebileceğini anlıyorum.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        enabled = riskAccepted,
                        onClick = { showConfirmation = true },
                    ) { Text("Root ile 2.15.5.46 sürümüne geç") }
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Tema Yöneticisi sürümü değiştirilsin mi?") },
            text = {
                Text(
                    "Yalnızca doğrulanan ${verifiedApk?.packageName} paketi hedeflenecek. " +
                        "Komut root üzerinden `pm install -r -d` çalıştırır; sistem dosyası yazmaz ve doğrulamayı devre dışı bırakmaz. " +
                        "İşlem başarısız olabilir; önemli tema ayarlarınızı önceden yedekleyin.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        val apk = verifiedApk ?: return@TextButton
                        scope.launch {
                            status = "Root izni ve paket yöneticisi sonucu bekleniyor…"
                            runCatching {
                                withContext(Dispatchers.IO) { updater.installVerifiedDowngrade(apk) }
                            }.onSuccess { result ->
                                installed = result.installedAfter
                                status = buildString {
                                    append(result.message)
                                    if (result.commandOutput.isNotBlank()) append(" · ").append(result.commandOutput)
                                }
                                if (result.success) {
                                    withContext(Dispatchers.IO) { updater.discard(apk) }
                                    verifiedApk = null
                                    riskAccepted = false
                                }
                            }.onFailure { error ->
                                status = "Root işlemi başarısız: ${error.message ?: error::class.simpleName}"
                            }
                        }
                    },
                ) { Text("Onayla ve çalıştır") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text("İptal") }
            },
        )
    }
}

@Composable
private fun CompatibilityMatrix() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Bilinen Global sürüm davranışları", fontWeight = FontWeight.SemiBold)
        Text("2.15.5.46 · Bağımsız yerel tema importu (önerilen)", style = MaterialTheme.typography.bodySmall)
        Text("3.0.5.14 · ‘Varsayılan’ üzerine geçici/kompozit uygulama", style = MaterialTheme.typography.bodySmall)
        Text("3.0.6.8 · Tester aktivitesi kaldırılmış", style = MaterialTheme.typography.bodySmall)
    }
}

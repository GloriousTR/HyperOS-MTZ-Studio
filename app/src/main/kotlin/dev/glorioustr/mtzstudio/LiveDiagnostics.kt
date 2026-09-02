package dev.glorioustr.mtzstudio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import dev.glorioustr.mtzstudio.shevery.PreferredPrivilegedCommandRunner
import dev.glorioustr.mtzstudio.tester.ThemeManagerContract
import dev.glorioustr.mtzstudio.tester.ThemeManagerInspector
import dev.glorioustr.mtzstudio.library.ImportEvent
import dev.glorioustr.mtzstudio.library.ImportObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SelectedDocumentDiagnostics(
    val displayName: String?,
    val sourceBytes: Long?,
    val scheme: String?,
    val authority: String?,
)

data class DiagnosticUiState(
    val activeSessionId: String? = null,
    val phase: String = "Hazır",
    val bytesCopied: Long = 0,
    val sourceBytes: Long? = null,
    val rootEnabled: Boolean = false,
    val rootStatus: String = "Root boot tanılaması kapalı",
    val recoveredSession: String? = null,
    val orphanStagingCount: Int = 0,
    val orphanStagingBytes: Long = 0,
    val recentEvents: List<String> = emptyList(),
)

class ImportDiagnosticSession internal constructor(
    val id: String,
    val observer: ImportObserver,
    private val recorder: LiveDiagnosticsRecorder,
) {
    fun failBeforeImport(message: String) = recorder.failBeforeImport(id, message)
}

@SuppressLint("ApplySharedPref", "UseKtx")
class LiveDiagnosticsRecorder(private val context: Context) {
    private val diagnosticsRoot = context.filesDir.toPath().resolve("diagnostics")
    private val journal = diagnosticsRoot.resolve("events.jsonl")
    private val previousJournal = diagnosticsRoot.resolve("events.previous.jsonl")
    private val activeFile = diagnosticsRoot.resolve("active-import.properties")
    private val libraryRoot = context.filesDir.toPath().resolve("mtz-library")
    private val exportsRoot = context.filesDir.toPath().resolve("exports")
    private val preferences = context.getSharedPreferences("live-diagnostics", Context.MODE_PRIVATE)
    private val bootId = readSmallFile(File("/proc/sys/kernel/random/boot_id").toPath()) ?: "unknown"
    private val mutableState = MutableStateFlow(
        DiagnosticUiState(rootEnabled = preferences.getBoolean(KEY_ROOT_ENABLED, false)),
    )
    private var activeSession: ActiveSession? = null
    private val nativeSteps = java.util.LinkedHashSet<String>()

    val state: StateFlow<DiagnosticUiState> = mutableState.asStateFlow()

    /** Diagnostics must never turn a recoverable operation into an application crash. */
    @Synchronized
    fun record(event: String, message: String, details: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        runCatching {
            val bounded = details.mapValues { (_, value) -> value?.toString()?.let(::redactUris)?.take(6000) }.toMutableMap()
            if (error != null) bounded["exception"] = redactUris(error.stackTraceToString()).take(6000)
            appendEvent(null, event, redactUris(message), bounded, critical = true)
            if (activeSession == null) mutableState.value = mutableState.value.copy(phase = redactUris(message).take(500))
        }.onFailure { android.util.Log.e("MtzDiagnostics", "Unable to write diagnostic event", it) }
    }

    @Synchronized
    fun recordNativeStep(step: String) {
        val bounded = step.take(1500)
        if (!nativeSteps.add(bounded)) return
        if (nativeSteps.size > 200) nativeSteps.remove(nativeSteps.first())
        val hostTime = bounded.substringBefore(" · ").toLongOrNull()
        record("native_bridge_step", if (hostTime != null) bounded.substringAfter(" · ") else bounded,
            if (hostTime != null) mapOf("hostTimestamp" to Instant.ofEpochMilli(hostTime).toString()) else emptyMap())
    }

    fun nativeStepReceiver(): android.os.ResultReceiver = object : android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
            resultData?.getString("step")?.let(::recordNativeStep)
        }
    }

    init {
        Files.createDirectories(diagnosticsRoot)
        Files.createDirectories(exportsRoot)
        rotateJournalIfNeeded()
        recoverInterruptedSession()
        inspectOrphanStaging()
        refreshRecentEvents()
        record("app_process_started", "Uygulama kaydı başladı", mapOf(
            "version" to BuildConfig.VERSION_NAME, "android" to Build.VERSION.RELEASE,
        ))
        val previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record("app_uncaught_exception", "Uygulamada beklenmeyen hata", mapOf("thread" to thread.name), error)
            previousExceptionHandler?.uncaughtException(thread, error)
        }
        val pending = context.getSharedPreferences("studio-ui-state", 0)
        if (pending.contains("pending-operation")) {
            record("previous_operation_unresolved", "Önceki işlem için tamamlanma sonucu kaydedilmemiş", mapOf(
                "operation" to pending.getString("pending-operation", null),
                "theme" to pending.getString("pending-theme-name", null),
            ))
        }
        if (mutableState.value.rootEnabled) {
            mutableState.value = mutableState.value.copy(rootStatus = "Root boot tanılaması etkin")
        }
    }

    @Synchronized
    fun recordPickerLaunched() {
        appendEvent(null, "picker_launched", "MTZ dosya seçici açıldı", critical = true)
    }

    @Synchronized
    fun recordPickerResultReceived() {
        appendEvent(null, "picker_result_received", "Dosya seçici sonucu uygulamaya döndü", critical = true)
    }

    @Synchronized
    fun beginImport(document: SelectedDocumentDiagnostics): ImportDiagnosticSession {
        check(activeSession == null) { "Başka bir import tanılama oturumu hâlâ etkin" }
        val id = "${Instant.now().toEpochMilli()}-${UUID.randomUUID().toString().take(8)}"
        val session = ActiveSession(
            id = id,
            bootId = bootId,
            startedAt = Instant.now().toString(),
            phase = "selected",
            displayName = document.displayName?.take(180),
            sourceBytes = document.sourceBytes,
            bytesCopied = 0,
            stagingName = null,
        )
        activeSession = session
        writeActive(session)
        appendEvent(
            id,
            "document_selected",
            "Dosya seçildi",
            details = mapOf(
                "displayName" to session.displayName,
                "sourceBytes" to session.sourceBytes,
                "scheme" to document.scheme,
                "authority" to document.authority,
            ),
            critical = true,
        )
        updateState(session, "Dosya seçildi")
        return ImportDiagnosticSession(id, ImportObserver { onImportEvent(id, it) }, this)
    }

    @Synchronized
    internal fun failBeforeImport(sessionId: String, message: String) {
        val session = activeSession?.takeIf { it.id == sessionId } ?: return
        session.phase = "failed_before_copy"
        appendEvent(sessionId, "import_failed", message.take(500), critical = true)
        finishSession(session, "Import başlatılamadı: ${message.take(180)}")
    }

    @Synchronized
    private fun onImportEvent(sessionId: String, event: ImportEvent) {
        val session = activeSession?.takeIf { it.id == sessionId }
        when (event) {
            is ImportEvent.StagingCreated -> {
                session?.phase = "staging_created"
                session?.stagingName = event.fileName
                appendEvent(sessionId, "staging_created", "Geçici import alanı oluşturuldu", mapOf("fileName" to event.fileName), true)
            }
            ImportEvent.CopyStarted -> {
                session?.phase = "copying"
                appendEvent(sessionId, "copy_started", "MTZ özel depoya kopyalanıyor", critical = true)
            }
            is ImportEvent.CopyProgress -> {
                session?.bytesCopied = event.bytesCopied
                appendEvent(
                    sessionId,
                    "copy_progress",
                    "${formatBytes(event.bytesCopied)} kopyalandı",
                    mapOf("bytesCopied" to event.bytesCopied),
                    critical = true,
                )
            }
            is ImportEvent.CopyCompleted -> {
                session?.phase = "copy_completed"
                session?.bytesCopied = event.bytesCopied
                appendEvent(
                    sessionId,
                    "copy_completed",
                    "Kopyalama tamamlandı",
                    mapOf("bytesCopied" to event.bytesCopied),
                    critical = true,
                )
            }
            ImportEvent.ValidationStarted -> {
                session?.phase = "validating"
                appendEvent(sessionId, "validation_started", "ZIP/MTZ doğrulaması başladı", critical = true)
            }
            is ImportEvent.ValidationCompleted -> {
                session?.phase = "validated"
                appendEvent(
                    sessionId,
                    "validation_completed",
                    "MTZ doğrulandı",
                    mapOf("sha256" to event.sha256, "entryCount" to event.entryCount),
                    critical = true,
                )
            }
            is ImportEvent.CommitStarted -> {
                session?.phase = "committing"
                appendEvent(sessionId, "commit_started", "Tema özel kitaplığa kaydediliyor", mapOf("themeId" to event.themeId), true)
            }
            is ImportEvent.CommitCompleted -> {
                session?.phase = "completed"
                appendEvent(
                    sessionId,
                    "commit_completed",
                    "Import başarıyla tamamlandı",
                    mapOf("themeId" to event.themeId, "sha256" to event.sha256),
                    critical = true,
                )
                if (session != null) finishSession(session, "Import tamamlandı")
            }
            is ImportEvent.Failed -> {
                session?.phase = "failed_${event.stage}"
                appendEvent(
                    sessionId,
                    "import_failed",
                    event.message.take(500),
                    mapOf("stage" to event.stage),
                    critical = true,
                )
                if (session != null) finishSession(session, "${event.stage} aşamasında hata")
            }
            is ImportEvent.StagingCleaned -> appendEvent(
                sessionId,
                "staging_cleanup",
                if (event.removed) "Geçici dosya temizlendi" else "Temizlenecek geçici dosya yoktu",
                critical = true,
            )
        }
        if (session != null && activeSession === session) {
            writeActive(session)
            updateState(session, phaseLabel(session.phase))
        }
    }

    fun enableRootDiagnostics(): Result<Unit> = runCatching {
        val result = runRootCommand("id -u", 20)
        check(result.exitCode == 0 && result.output.lineSequence().firstOrNull()?.trim() == "0") {
            "Root kabuğu UID 0 vermedi: ${result.output.take(160)}"
        }
        preferences.edit().putBoolean(KEY_ROOT_ENABLED, true).commit()
        synchronized(this) {
            mutableState.value = mutableState.value.copy(
                rootEnabled = true,
                rootStatus = "Root izni doğrulandı; boot kaydı alınıyor",
            )
            appendEvent(null, "root_diagnostics_enabled", "Root boot tanılaması etkinleştirildi", critical = true)
        }
        captureRootBootSnapshot()
    }.onFailure { error ->
        synchronized(this) {
            mutableState.value = mutableState.value.copy(rootStatus = "Root izni alınamadı: ${error.message}")
            appendEvent(null, "root_diagnostics_failed", error.message ?: "Root izni alınamadı", critical = true)
        }
    }

    @Synchronized
    fun disableRootDiagnostics() {
        preferences.edit().putBoolean(KEY_ROOT_ENABLED, false).commit()
        mutableState.value = mutableState.value.copy(rootEnabled = false, rootStatus = "Root boot tanılaması kapalı")
        appendEvent(null, "root_diagnostics_disabled", "Root boot tanılaması kapatıldı", critical = true)
    }

    fun captureRootBootSnapshotIfEnabled() {
        if (preferences.getBoolean(KEY_ROOT_ENABLED, false)) captureRootBootSnapshot()
    }

    private fun captureRootBootSnapshot() {
        val result = runRootCommand(BOOT_SNAPSHOT_COMMAND, 25)
        if (result.exitCode != 0) error("Boot kaydı alınamadı: ${result.output.take(200)}")
        val target = diagnosticsRoot.resolve("boot-${safeFilePart(bootId)}.txt")
        FileOutputStream(target.toFile()).use { output ->
            output.write(result.output.take(MAX_ROOT_OUTPUT_CHARS).toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
        synchronized(this) {
            mutableState.value = mutableState.value.copy(rootStatus = "Root boot kaydı hazır")
            appendEvent(null, "root_boot_snapshot", "Salt-okunur boot/pstore özeti kaydedildi", critical = true)
        }
    }

    @Synchronized
    fun createExport(): Path {
        Files.createDirectories(exportsRoot)
        val target = exportsRoot.resolve("mtz-diagnostics-${Instant.now().toEpochMilli()}.txt")
        FileOutputStream(target.toFile()).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("HyperOS MTZ Studio Live Diagnostics")
            writer.appendLine("appVersion=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            writer.appendLine("generatedAt=${Instant.now()}")
            writer.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            writer.appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            writer.appendLine("bootId=$bootId")
            writer.appendLine("rootDiagnostics=${mutableState.value.rootEnabled}")
            writer.appendLine("rawUriRecorded=false")
            writer.appendLine()
            writer.appendLine("[Orphan staging files]")
            orphanStagingFiles().forEach { path ->
                writer.appendLine("${path.fileName} size=${runCatching { Files.size(path) }.getOrDefault(-1)}")
            }
            writer.appendLine()
            Files.list(diagnosticsRoot).use { files ->
                files.filter { it.fileName.toString().startsWith("boot-") && it.fileName.toString().endsWith(".txt") }
                    .sorted()
                    .forEach { path ->
                        writer.appendLine("[${path.fileName}]")
                        writer.appendLine(readUtf8(path).take(MAX_ROOT_OUTPUT_CHARS))
                    }
            }
            listOf(previousJournal, journal).filter(Files::isRegularFile).forEach { path ->
                writer.appendLine("[${path.fileName}]")
                Files.newBufferedReader(path).useLines { lines -> lines.forEach(writer::appendLine) }
            }
        }
        return target
    }

    /**
     * Copies only the installed Themes base APK after an explicit user request.  The output stays
     * in this app's private export directory until the user chooses a share target.  It is never
     * uploaded, installed, or sent automatically.
     */
    fun exportInstalledThemeManagerApk(): Path {
        val packageName = ThemeManagerContract.PACKAGE_NAME
        val applicationInfo = installedApplicationInfo(packageName)
            ?: throw IllegalStateException("Xiaomi Temalar paketi bulunamadı")
        val sourcePath = applicationInfo.sourceDir?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Xiaomi Temalar temel APK yolu bulunamadı")
        val installed = ThemeManagerInspector(context).inspect()
        if (!installed.installed) throw IllegalStateException("Xiaomi Temalar paketi bulunamadı")

        Files.createDirectories(exportsRoot)
        val version = safeFilePart(installed.versionName ?: "unknown")
        val target = exportsRoot.resolve("xiaomi-themes-$version-base.apk")
        val temporary = exportsRoot.resolve(".${target.fileName}.${UUID.randomUUID()}.part")
        record("themes_apk_export_started", "Xiaomi Temalar APK dışa aktarımı başlatıldı", mapOf(
            "package" to packageName,
            "version" to installed.versionName,
            "splitCount" to (applicationInfo.splitSourceDirs?.size ?: 0),
        ))
        try {
            val command = """
                set -eu
                cp ${shellQuote(sourcePath)} ${shellQuote(temporary.toString())}
                chmod 0644 ${shellQuote(temporary.toString())}
                mv ${shellQuote(temporary.toString())} ${shellQuote(target.toString())}
            """.trimIndent()
            val result = PreferredPrivilegedCommandRunner(context).run(command, 90)
            check(result.exitCode == 0) { "APK kopyalanamadı: ${result.output.take(240)}" }
            check(Files.isRegularFile(target) && Files.size(target) > 0) { "Dışa aktarılan APK okunamadı" }
            val archive = ThemeManagerInspector(context).inspectArchive(target.toString())
            check(archive.packageName == packageName) { "Dışa aktarılan dosya Xiaomi Temalar paketi değil" }
            check(archive.versionName == installed.versionName && archive.versionCode == installed.versionCode) {
                "Dışa aktarılan APK sürümü kurulu paketle eşleşmiyor"
            }
            val sha256 = sha256(target)
            record("themes_apk_export_completed", "Xiaomi Temalar temel APK’sı doğrulanarak hazırlandı", mapOf(
                "version" to archive.versionName,
                "versionCode" to archive.versionCode,
                "sha256" to sha256,
                "bytes" to Files.size(target),
                "splitCount" to (applicationInfo.splitSourceDirs?.size ?: 0),
            ))
            return target
        } catch (error: Throwable) {
            Files.deleteIfExists(temporary)
            Files.deleteIfExists(target)
            record("themes_apk_export_failed", "Xiaomi Temalar APK dışa aktarılamadı", error = error)
            throw error
        }
    }

    private fun installedApplicationInfo(packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
    }.getOrNull()

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").let { digest ->
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\\"'\\\"'") + "'"

    @Synchronized
    private fun recoverInterruptedSession() {
        if (!Files.isRegularFile(activeFile)) return
        val properties = Properties().apply { Files.newInputStream(activeFile).use(::load) }
        val id = properties.getProperty("id", "unknown")
        val priorBootId = properties.getProperty("bootId", "unknown")
        val phase = properties.getProperty("phase", "unknown")
        val bytes = properties.getProperty("bytesCopied", "0").toLongOrNull() ?: 0
        appendEvent(
            id,
            "interrupted_session_detected",
            "Önceki import tamamlanmadan kesildi",
            mapOf(
                "lastPhase" to phase,
                "bytesCopied" to bytes,
                "bootChanged" to (priorBootId != bootId),
                "priorBootId" to priorBootId,
            ),
            critical = true,
        )
        val recovered = diagnosticsRoot.resolve("interrupted-${safeFilePart(id)}.properties")
        Files.move(activeFile, recovered, StandardCopyOption.REPLACE_EXISTING)
        mutableState.value = mutableState.value.copy(
            recoveredSession = "$id · son aşama: $phase · ${formatBytes(bytes)}",
            phase = "Kesilmiş oturum algılandı",
        )
    }

    @Synchronized
    private fun inspectOrphanStaging() {
        val staging = orphanStagingFiles()
        val totalBytes = staging.sumOf { runCatching { Files.size(it) }.getOrDefault(0) }
        if (staging.isNotEmpty()) {
            appendEvent(
                null,
                "orphan_staging_detected",
                "${staging.size} yarım import dosyası korundu",
                mapOf("count" to staging.size, "bytes" to totalBytes),
                critical = true,
            )
        }
        mutableState.value = mutableState.value.copy(
            orphanStagingCount = staging.size,
            orphanStagingBytes = totalBytes,
        )
    }

    private fun orphanStagingFiles(): List<Path> {
        if (!Files.isDirectory(libraryRoot)) return emptyList()
        val staging = mutableListOf<Path>()
        Files.list(libraryRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().matches(ORPHAN_PATTERN) }
                .forEach(staging::add)
        }
        return staging.sorted()
    }

    private fun finishSession(session: ActiveSession, label: String) {
        val finished = diagnosticsRoot.resolve("last-${safeFilePart(session.id)}.properties")
        writeProperties(finished, session.toProperties())
        Files.deleteIfExists(activeFile)
        activeSession = null
        mutableState.value = mutableState.value.copy(
            activeSessionId = null,
            phase = label,
            bytesCopied = session.bytesCopied,
            sourceBytes = session.sourceBytes,
        )
    }

    private fun updateState(session: ActiveSession, label: String) {
        mutableState.value = mutableState.value.copy(
            activeSessionId = session.id,
            phase = label,
            bytesCopied = session.bytesCopied,
            sourceBytes = session.sourceBytes,
        )
    }

    private fun writeActive(session: ActiveSession) = writeProperties(activeFile, session.toProperties())

    private fun writeProperties(target: Path, properties: Properties) {
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        FileOutputStream(temporary.toFile()).use { output ->
            properties.store(output, "HyperOS MTZ Studio Live Diagnostics")
            output.fd.sync()
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun appendEvent(
        sessionId: String?,
        event: String,
        message: String,
        details: Map<String, Any?> = emptyMap(),
        critical: Boolean,
    ) {
        rotateJournalIfNeeded()
        val json = JSONObject().apply {
            put("timestamp", Instant.now().toString())
            put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
            put("bootId", bootId)
            put("sessionId", sessionId ?: JSONObject.NULL)
            put("event", event)
            put("message", message.take(500))
            put("details", JSONObject(details.filterValues { it != null }))
        }
        FileOutputStream(journal.toFile(), true).use { output ->
            output.write((json.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            if (critical) output.fd.sync()
        }
        mutableState.value = mutableState.value.copy(
            recentEvents = (mutableState.value.recentEvents + renderEvent(json)).takeLast(200),
        )
    }

    private fun rotateJournalIfNeeded() {
        if (!Files.isRegularFile(journal) || Files.size(journal) < MAX_JOURNAL_BYTES) return
        Files.move(journal, previousJournal, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun refreshRecentEvents() {
        if (!Files.isRegularFile(journal)) return
        val lines = listOf(previousJournal, journal).filter(Files::isRegularFile)
            .flatMap { Files.readAllLines(it).takeLast(200) }.takeLast(200).mapNotNull { line ->
            runCatching {
                renderEvent(JSONObject(line))
            }.getOrNull()
        }
        mutableState.value = mutableState.value.copy(recentEvents = lines)
    }

    private fun renderEvent(json: JSONObject): String {
        val time = Instant.parse(json.getString("timestamp")).atZone(ZoneId.systemDefault()).format(EVENT_TIME_FORMAT)
        val details = json.optJSONObject("details")
        val detailText = details?.keys()?.asSequence()?.joinToString("\n") { key -> "$key: ${details.optString(key)}" }.orEmpty()
        return "$time · ${json.getString("message")}\n[${json.getString("event")}]" +
            if (detailText.isBlank()) "" else "\n$detailText"
    }

    private fun redactUris(text: String): String = text.replace(Regex("(?:content|file)://[^\\s\"<>]+"), "[URI]")

    private fun runRootCommand(command: String, timeoutSeconds: Long): CommandResult {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("Root komutu ${timeoutSeconds} saniyede tamamlanmadı")
        }
        return CommandResult(process.exitValue(), process.inputStream.bufferedReader().readText().take(MAX_ROOT_OUTPUT_CHARS))
    }

    private data class CommandResult(val exitCode: Int, val output: String)

    private data class ActiveSession(
        val id: String,
        val bootId: String,
        val startedAt: String,
        var phase: String,
        val displayName: String?,
        val sourceBytes: Long?,
        var bytesCopied: Long,
        var stagingName: String?,
    ) {
        fun toProperties() = Properties().apply {
            setProperty("id", id)
            setProperty("bootId", bootId)
            setProperty("startedAt", startedAt)
            setProperty("phase", phase)
            setProperty("displayName", displayName.orEmpty())
            setProperty("sourceBytes", sourceBytes?.toString().orEmpty())
            setProperty("bytesCopied", bytesCopied.toString())
            setProperty("stagingName", stagingName.orEmpty())
        }
    }

    companion object {
        @Volatile private var instance: LiveDiagnosticsRecorder? = null
        fun get(context: Context): LiveDiagnosticsRecorder = instance ?: synchronized(this) {
            instance ?: LiveDiagnosticsRecorder(context.applicationContext).also { instance = it }
        }
        const val KEY_ROOT_ENABLED = "root-enabled"
        const val MAX_JOURNAL_BYTES = 2L * 1024 * 1024
        const val MAX_ROOT_OUTPUT_CHARS = 64 * 1024
        val ORPHAN_PATTERN = Regex("^\\.import-[A-Za-z0-9-]+\\.tmp$")
        val EVENT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val BOOT_SNAPSHOT_COMMAND = """
            echo "ro.boot.bootreason=${'$'}(/system/bin/getprop ro.boot.bootreason)"
            echo "sys.boot.reason=${'$'}(/system/bin/getprop sys.boot.reason)"
            echo "sys.boot.reason.last=${'$'}(/system/bin/getprop sys.boot.reason.last)"
            for file in /sys/fs/pstore/console-ramoops-0 /sys/fs/pstore/pmsg-ramoops-0; do
              if [ -r "${'$'}file" ]; then
                echo "[${'$'}file]"
                /system/bin/strings "${'$'}file" | /system/bin/grep -Eai 'sys[.]powerctl|reboot: Restarting system|ShutdownThread|PowerManagerService.*reboot|reboot,shell|kernel panic|watchdog.*(bite|reset|reboot|timeout)|thermal.*shutdown' | /system/bin/tail -n 80
              fi
            done
        """.trimIndent()

        fun readSmallFile(path: Path): String? = runCatching {
            readUtf8(path).trim().takeIf(String::isNotBlank)
        }.getOrNull()

        fun readUtf8(path: Path): String = String(Files.readAllBytes(path), StandardCharsets.UTF_8)

        fun safeFilePart(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "-").take(100)

        fun phaseLabel(phase: String, context: Context? = null): String {
            if (context != null) {
                val resId = when (phase) {
                    "staging_created" -> R.string.diag_phase_staging_created
                    "copying" -> R.string.diag_phase_copying
                    "copy_completed" -> R.string.diag_phase_copy_completed
                    "validating" -> R.string.diag_phase_validating
                    "validated" -> R.string.diag_phase_validated
                    "committing" -> R.string.diag_phase_committing
                    else -> 0
                }
                if (resId != 0) return context.getString(resId)
            }
            return when (phase) {
                "staging_created" -> "Geçici alan hazır"
                "copying" -> "MTZ kopyalanıyor"
                "copy_completed" -> "Kopyalama tamamlandı"
                "validating" -> "ZIP/MTZ doğrulanıyor"
                "validated" -> "MTZ doğrulandı"
                "committing" -> "Özel kitaplığa kaydediliyor"
                else -> phase
            }
        }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

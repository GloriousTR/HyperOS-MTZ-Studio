package dev.glorioustr.mtzstudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ThemeTranslationProgress(
    val themeId: String? = null,
    val themeName: String = "",
    val processed: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (!running) 1f else if (total <= 0) 0f else (processed.toFloat() / total).coerceIn(0f, 1f)
}

internal object ThemeTranslationProgressStore {
    private val mutableState = MutableStateFlow(ThemeTranslationProgress())
    val state: StateFlow<ThemeTranslationProgress> = mutableState.asStateFlow()

    fun update(progress: ThemeTranslationProgress) {
        mutableState.value = progress
    }
}

internal class ThemeTranslationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val themeId = intent?.getStringExtra(EXTRA_THEME_ID) ?: return START_NOT_STICKY
        if (runningJob?.isActive == true) return START_NOT_STICKY
        val themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        val initial = ThemeTranslationProgress(themeId, themeName, running = true)
        ThemeTranslationProgressStore.update(initial)
        startForeground(NOTIFICATION_ID, notification(initial))
        runningJob = scope.launch {
            runCatching {
                val library = ThemeLibrary(applicationContext)
                val theme = library.load().themes.firstOrNull { it.id.value == themeId }
                    ?: error("Theme is no longer in the library")
                val translated = ThemeLanguageTool(applicationContext, library)
                    .translateTextToSystemLanguage(theme) { processed, total ->
                        val progress = ThemeTranslationProgress(
                            themeId = themeId,
                            themeName = themeName,
                            processed = processed,
                            total = total,
                            running = true,
                        )
                        ThemeTranslationProgressStore.update(progress)
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(progress))
                    }
                MtzPublicExporter.exportToPublicDownloads(
                    applicationContext,
                    translated.archive.source,
                    translated.displayName,
                )
                ThemeTranslationProgress(
                    themeId = themeId,
                    themeName = themeName,
                    processed = 1,
                    total = 1,
                    completed = true,
                )
            }.getOrElse { error ->
                ThemeTranslationProgress(
                    themeId = themeId,
                    themeName = themeName,
                    completed = true,
                    error = error.message ?: error::class.simpleName,
                )
            }.also { result ->
                ThemeTranslationProgressStore.update(result)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, completionNotification(result))
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(progress: ThemeTranslationProgress) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.theme_language_tool_title))
        .setContentText(
            if (progress.total > 0) "${progress.processed}/${progress.total} · ${(progress.fraction * 100).toInt()}%"
            else getString(R.string.theme_language_tool_working),
        )
        .setProgress(progress.total.coerceAtLeast(0), progress.processed.coerceAtLeast(0), progress.total <= 0)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun completionNotification(progress: ThemeTranslationProgress) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.theme_language_tool_title))
        .setContentText(
            progress.error?.let { getString(R.string.theme_language_tool_failed, it) }
                ?: getString(R.string.theme_language_tool_complete),
        )
        .setAutoCancel(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.theme_language_tool_title), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"
        private const val CHANNEL_ID = "theme_translation"
        private const val NOTIFICATION_ID = 4401

        fun start(context: Context, themeId: String, themeName: String) {
            val intent = Intent(context, ThemeTranslationService::class.java)
                .putExtra(EXTRA_THEME_ID, themeId)
                .putExtra(EXTRA_THEME_NAME, themeName)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}

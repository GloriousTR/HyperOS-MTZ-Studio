package dev.glorioustr.mtzstudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Rootless compatibility guard derived from verified HyperOS behaviour: it does not poll or
 * repeatedly apply a theme. It only consumes the two ordered Xiaomi validation broadcasts that
 * are responsible for reverting a locally imported theme on affected ROMs.
 */
class ThemePersistenceGuardService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in GUARDED_ACTIONS && isOrderedBroadcast) {
                abortBroadcast()
                LiveDiagnosticsRecorder.get(context).record(
                    "rootless_theme_check_blocked",
                    "Xiaomi yerel tema doğrulama yayını durduruldu",
                    mapOf("action" to intent.action),
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Theme persistence", NotificationManager.IMPORTANCE_LOW))
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Yerel tema koruması etkin")
                .setContentIntent(pending)
                .setOngoing(true)
                .setSilent(true)
                .build(),
        )
        val filter = IntentFilter().apply {
            priority = 1_000
            GUARDED_ACTIONS.forEach(::addAction)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        LiveDiagnosticsRecorder.get(this).record("theme_guard_started", "Rootsuz yerel tema koruması başlatıldı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        LiveDiagnosticsRecorder.get(this).record("theme_guard_stopped", "Rootsuz yerel tema koruması durdu")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "theme-persistence"
        private const val NOTIFICATION_ID = 23102
        private val GUARDED_ACTIONS = setOf("miui.intent.action.CHECK_TIME_UP", "miui.intent.action.CHECK_THEME_UPDATE")

        fun start(context: Context) {
            val intent = Intent(context, ThemePersistenceGuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            context.getSharedPreferences("theme-persistence", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        }

        /** Shizuku and root routes do not need this best-effort Standard-mode receiver. */
        fun disable(context: Context) {
            context.stopService(Intent(context, ThemePersistenceGuardService::class.java))
            context.getSharedPreferences("theme-persistence", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        }
    }
}

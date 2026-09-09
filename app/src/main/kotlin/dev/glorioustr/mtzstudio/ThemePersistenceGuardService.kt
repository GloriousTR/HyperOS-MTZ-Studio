package dev.glorioustr.mtzstudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.glorioustr.mtzstudio.shevery.SheveryAccess
import dev.glorioustr.mtzstudio.shevery.SheveryAuthorizationStatus
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches the actual Xiaomi theme component files while Shizuku is available. A BAK restore only
 * imports a theme into Xiaomi's private library; persistence is armed separately after the user
 * returns from the apply flow. When a component changes, the exact previously resolved Xiaomi
 * apply intent is offered again instead of guessing a version-specific activity.
 */
class ThemePersistenceGuardService : Service() {
    private val checking = AtomicBoolean(false)
    private var scheduler: ScheduledExecutorService? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in GUARDED_ACTIONS && isOrderedBroadcast) {
                abortBroadcast()
                diagnostics.record(
                    "rootless_theme_check_blocked",
                    "Xiaomi yerel tema doğrulama yayını durduruldu",
                    mapOf("action" to intent.action),
                )
            }
            if (intent.action == Intent.ACTION_SCREEN_ON) scheduleImmediateCheck()
        }
    }

    private val diagnostics by lazy { LiveDiagnosticsRecorder.get(this) }
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIFICATION_ID, monitoringNotification())
        val filter = IntentFilter().apply {
            priority = 1_000
            GUARDED_ACTIONS.forEach(::addAction)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        scheduler = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "mtz-theme-watch").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay(::checkTheme, INITIAL_CHECK_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS)
        }
        diagnostics.record("theme_guard_started", "Shizuku tema bileşeni izleyicisi başlatıldı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            getSystemService(NotificationManager::class.java).cancel(REAPPLY_NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_ARM) {
            val themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
            val applyIntentUri = intent.getStringExtra(EXTRA_APPLY_INTENT).orEmpty()
            if (themeName.isNotBlank() && applyIntentUri.isNotBlank()) {
                prefs.edit()
                    .putBoolean(KEY_ENABLED, true)
                    .putString(KEY_THEME_NAME, themeName)
                    .putString(KEY_APPLY_INTENT, applyIntentUri)
                    .remove(KEY_BASELINE)
                    .remove(KEY_LAST_ALERTED_FINGERPRINT)
                    .apply()
                diagnostics.record("theme_watch_armed", "Tema bileşeni izleme kaydı oluşturuldu", mapOf("theme" to themeName))
                scheduler?.schedule(::captureBaseline, BASELINE_DELAY_SECONDS, TimeUnit.SECONDS)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduler?.shutdownNow()
        scheduler = null
        runCatching { unregisterReceiver(receiver) }
        diagnostics.record("theme_guard_stopped", "Tema bileşeni izleyicisi durdu")
        super.onDestroy()
    }

    private fun scheduleImmediateCheck() {
        scheduler?.schedule(::checkTheme, 3, TimeUnit.SECONDS)
    }

    private fun captureBaseline() {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val fingerprint = readFingerprint() ?: return
        prefs.edit().putString(KEY_BASELINE, fingerprint).apply()
        diagnostics.record(
            "theme_watch_baseline_saved",
            "Uygulanan temanın bileşen imzası kaydedildi",
            mapOf("theme" to prefs.getString(KEY_THEME_NAME, ""), "fingerprint" to fingerprint.take(16)),
        )
    }

    private fun checkTheme() {
        if (!prefs.getBoolean(KEY_ENABLED, false) || !checking.compareAndSet(false, true)) return
        try {
            val baseline = prefs.getString(KEY_BASELINE, null)
            if (baseline.isNullOrBlank()) {
                captureBaseline()
                return
            }
            val current = readFingerprint() ?: return
            if (current == baseline) {
                if (prefs.contains(KEY_LAST_ALERTED_FINGERPRINT)) {
                    prefs.edit().remove(KEY_LAST_ALERTED_FINGERPRINT).apply()
                }
                return
            }
            if (prefs.getString(KEY_LAST_ALERTED_FINGERPRINT, null) == current) return
            prefs.edit().putString(KEY_LAST_ALERTED_FINGERPRINT, current).apply()
            diagnostics.record(
                "theme_revert_detected",
                "Aktif tema bileşenleri kaydedilen temadan farklılaştı",
                mapOf(
                    "theme" to prefs.getString(KEY_THEME_NAME, ""),
                    "expected" to baseline.take(16),
                    "actual" to current.take(16),
                ),
            )
            showReapplyNotification()
        } finally {
            checking.set(false)
        }
    }

    private fun readFingerprint(): String? {
        val shevery = SheveryAccess(applicationContext)
        if (shevery.status() != SheveryAuthorizationStatus.ADB_READY) {
            diagnostics.record("theme_watch_shizuku_unavailable", "Tema kontrolü için Shizuku bağlantısı hazır değil")
            return null
        }
        return runCatching {
            val result = shevery.executeShell(FINGERPRINT_COMMAND, 20)
            check(result.exitCode == 0 && result.output.isNotBlank()) { "Tema bileşenleri okunamadı" }
            result.output.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .sorted()
                .joinToString("|")
                .sha256()
        }.onFailure {
            diagnostics.record("theme_watch_check_failed", "Tema bileşeni kontrolü tamamlanamadı", error = it)
        }.getOrNull()
    }

    private fun showReapplyNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            diagnostics.record("theme_watch_notification_unavailable", "Tema geri dönüş bildirimi için bildirim izni yok")
            return
        }
        val uri = prefs.getString(KEY_APPLY_INTENT, null) ?: return
        val target = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
        if (target.resolveActivity(packageManager) == null) {
            diagnostics.record("theme_watch_target_unavailable", "Kaydedilen Xiaomi Temalar uygulama yolu artık kullanılamıyor")
            return
        }
        val pending = PendingIntent.getActivity(
            this,
            REAPPLY_NOTIFICATION_ID,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val name = prefs.getString(KEY_THEME_NAME, "") ?: ""
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.rootless_restore_notification_text, name))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.rootless_restore_notification_text, name)))
            .setContentIntent(pending)
            .addAction(0, "Korumayı durdur", disablePendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(REAPPLY_NOTIFICATION_ID, notification)
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Theme monitor", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Theme restore alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun monitoringNotification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Shizuku tema izleyici etkin")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .addAction(0, "Korumayı durdur", disablePendingIntent())
        .setSilent(true)
        .build()

    private fun disablePendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, ThemePersistenceGuardService::class.java).setAction(ACTION_DISABLE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun String.sha256(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS = "theme-persistence"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_THEME_NAME = "theme-name"
        private const val KEY_APPLY_INTENT = "apply-intent"
        private const val KEY_BASELINE = "component-baseline"
        private const val KEY_LAST_ALERTED_FINGERPRINT = "last-alerted-fingerprint"
        private const val CHANNEL = "theme-persistence"
        private const val ALERT_CHANNEL = "theme-persistence-alert"
        private const val NOTIFICATION_ID = 23102
        private const val REAPPLY_NOTIFICATION_ID = 23103
        private const val ACTION_ARM = "dev.glorioustr.mtzstudio.action.ARM_THEME_WATCH"
        private const val ACTION_DISABLE = "dev.glorioustr.mtzstudio.action.DISABLE_THEME_WATCH"
        private const val EXTRA_THEME_NAME = "theme-name"
        private const val EXTRA_APPLY_INTENT = "apply-intent"
        private const val INITIAL_CHECK_SECONDS = 20L
        private const val BASELINE_DELAY_SECONDS = 8L
        private const val CHECK_INTERVAL_SECONDS = 180L
        private val GUARDED_ACTIONS = setOf("miui.intent.action.CHECK_TIME_UP", "miui.intent.action.CHECK_THEME_UPDATE")
        private const val FINGERPRINT_COMMAND =
            "for f in description.xml icons lockscreen framework-res framework-miui-res com.android.systemui com.miui.home; do " +
                "p=/data/system/theme/\u0024f; if [ -f \u0022\u0024p\u0022 ]; then /system/bin/toybox sha256sum \u0022\u0024p\u0022; " +
                "else echo MISSING:\u0024f; fi; done"

        fun start(context: Context) {
            val intent = Intent(context, ThemePersistenceGuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun arm(context: Context, prepared: PreparedThemeApply) {
            val intent = Intent(context, ThemePersistenceGuardService::class.java).apply {
                action = ACTION_ARM
                putExtra(EXTRA_THEME_NAME, prepared.themeName)
                putExtra(EXTRA_APPLY_INTENT, prepared.intent.toUri(Intent.URI_INTENT_SCHEME))
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun resumeIfArmed(context: Context) {
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)) return
            start(context)
        }

        /** Stops active checking without forgetting the protected theme while Shizuku is offline. */
        fun pause(context: Context) {
            context.stopService(Intent(context, ThemePersistenceGuardService::class.java))
        }

        fun disable(context: Context) {
            context.stopService(Intent(context, ThemePersistenceGuardService::class.java))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        }
    }
}

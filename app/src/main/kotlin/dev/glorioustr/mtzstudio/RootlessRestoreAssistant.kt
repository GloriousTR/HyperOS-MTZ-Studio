package dev.glorioustr.mtzstudio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.glorioustr.mtzstudio.tester.ThemeManagerBehavior
import dev.glorioustr.mtzstudio.tester.ThemeManagerContract

/**
 * Stores a user-approved rootless hand-off and, after a restart, offers a visible one-tap return
 * to Xiaomi Themes. It never starts Themes from the background and never claims the theme was
 * applied: the final import/apply decision stays in Xiaomi Themes.
 */
object RootlessRestoreAssistant {
    private const val PREFS = "rootless-restore-assistant"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_THEME_NAME = "theme-name"
    private const val KEY_THEME_PATH = "theme-path"
    private const val CHANNEL_ID = "rootless-restore"
    private const val NOTIFICATION_ID = 23101
    private const val THEME_MANAGER_PACKAGE = "com.android.thememanager"
    private const val LEGACY_TESTER_COMPONENT = "com.android.thememanager.ApplyThemeForScreenshot"
    private const val MODERN_LOCAL_ACTIVITY =
        "com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity"

    fun remember(context: Context, prepared: PreparedThemeApply) {
        val path = prepared.manualImportPath.orEmpty()
        if (path.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_THEME_NAME, prepared.themeName)
            .putString(KEY_THEME_PATH, path)
            .apply()
        LiveDiagnosticsRecorder.get(context).record(
            "rootless_restore_saved",
            "Rootsuz yeniden uygulama asistanı son seçilen MTZ'yi kaydetti",
            mapOf("theme" to prepared.themeName, "file" to path.substringAfterLast('/')),
        )
    }

    fun notifyAfterRestart(context: Context, trigger: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_NAME, null) ?: return
        val path = prefs.getString(KEY_THEME_PATH, null) ?: return
        if (!prefs.getBoolean(KEY_ENABLED, false) || path.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            LiveDiagnosticsRecorder.get(context).record(
                "rootless_restore_notification_unavailable",
                "Rootsuz yeniden uygulama bildirimi için bildirim izni verilmemiş",
                mapOf("trigger" to trigger),
            )
            return
        }
        val intent = themeManagerIntent(context, path)
        if (intent.resolveActivity(context.packageManager) == null) {
            LiveDiagnosticsRecorder.get(context).record(
                "rootless_restore_themes_unavailable",
                "Rootsuz yeniden uygulama için Xiaomi Temalar ekranı bulunamadı",
                mapOf("trigger" to trigger),
            )
            return
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.rootless_restore_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.rootless_restore_notification_title))
            .setContentText(context.getString(R.string.rootless_restore_notification_text, name))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                context.getString(R.string.rootless_restore_notification_text, name),
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        LiveDiagnosticsRecorder.get(context).record(
            "rootless_restore_notification_shown",
            "Rootsuz yeniden uygulama bildirimi gösterildi",
            mapOf("trigger" to trigger, "theme" to name),
        )
    }

    private fun themeManagerIntent(context: Context, path: String): Intent {
        val version = runCatching {
            context.packageManager.getPackageInfo(THEME_MANAGER_PACKAGE, 0).versionName
        }.getOrNull()
        return if (ThemeManagerContract.behavior(version) == ThemeManagerBehavior.LOCAL_THEME_IMPORT) {
            Intent(ThemeManagerContract.LEGACY_TESTER_ACTION).apply {
                component = ComponentName(THEME_MANAGER_PACKAGE, LEGACY_TESTER_COMPONENT)
                putExtra("theme_file_path", path)
                putExtra("api_called_from", context.packageName)
                putExtra("theme_apply_flags", -1L)
                putExtra("theme_remove_flags", -1L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent().apply {
                component = ComponentName(THEME_MANAGER_PACKAGE, MODERN_LOCAL_ACTIVITY)
                putExtra("REQUEST_RESOURCE_CODE", "theme")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

class RootlessRestoreBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        RootlessRestoreAssistant.notifyAfterRestart(context.applicationContext, action)
    }
}

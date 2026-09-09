package dev.glorioustr.mtzstudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.glorioustr.mtzstudio.library.StudioBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Files

internal enum class CloudTransferOperation { NONE, BACKUP, RESTORE }

internal data class CloudTransferState(
    val operation: CloudTransferOperation = CloudTransferOperation.NONE,
    val running: Boolean = false,
    val completed: Boolean = false,
    val themeCount: Int = 0,
    val fileCount: Int = 0,
    val error: String? = null,
    val completionId: Long = 0,
)

internal object CloudTransferStore {
    private val mutableState = MutableStateFlow(CloudTransferState())
    val state: StateFlow<CloudTransferState> = mutableState.asStateFlow()
    fun update(state: CloudTransferState) { mutableState.value = state }
}

internal class CloudTransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.section_cloud_storage), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val operation = when (intent?.action) {
            ACTION_BACKUP -> CloudTransferOperation.BACKUP
            ACTION_RESTORE -> CloudTransferOperation.RESTORE
            else -> return START_NOT_STICKY
        }
        if (CloudTransferStore.state.value.running) return START_NOT_STICKY
        val started = CloudTransferState(operation = operation, running = true)
        CloudTransferStore.update(started)
        startForeground(NOTIFICATION_ID, progressNotification(operation))
        scope.launch {
            val result = runCatching { execute(operation) }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    CloudTransferState(
                        operation = operation,
                        completed = true,
                        error = error.message ?: error::class.simpleName,
                        completionId = System.currentTimeMillis(),
                    )
                },
            )
            CloudTransferStore.update(result)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, completionNotification(result))
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_REDELIVER_INTENT
    }

    private fun execute(operation: CloudTransferOperation): CloudTransferState {
        val cloud = CloudAccountStore(applicationContext)
        val manager = StudioBackupManager(applicationContext)
        val temp = cacheDir.toPath().resolve("cloud-transfer-${System.currentTimeMillis()}.zip")
        return try {
            val summary = when (operation) {
                CloudTransferOperation.BACKUP -> {
                    val created = Files.newOutputStream(temp).use(manager::create)
                    cloud.uploadBackup(temp)
                    cloud.recordBackup()
                    created
                }
                CloudTransferOperation.RESTORE -> {
                    check(cloud.downloadBackup(temp)) { "No cloud backup was found in the selected location" }
                    Files.newInputStream(temp).use(manager::restore)
                }
                CloudTransferOperation.NONE -> error("No cloud operation selected")
            }
            CloudTransferState(
                operation = operation,
                completed = true,
                themeCount = summary.themeCount,
                fileCount = summary.fileCount,
                completionId = System.currentTimeMillis(),
            )
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(operation: CloudTransferOperation) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.section_cloud_storage))
        .setContentText(getString(if (operation == CloudTransferOperation.BACKUP) R.string.status_backup_preparing else R.string.status_restore_preparing))
        .setProgress(0, 0, true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun completionNotification(state: CloudTransferState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.section_cloud_storage))
        .setContentText(
            state.error ?: getString(
                if (state.operation == CloudTransferOperation.BACKUP) R.string.status_cloud_upload_success else R.string.status_restore_success,
                *if (state.operation == CloudTransferOperation.BACKUP) arrayOf(CloudAccountStore(applicationContext).load().provider.displayName)
                else arrayOf(state.themeCount, state.fileCount),
            ),
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

    companion object {
        private const val ACTION_BACKUP = "dev.glorioustr.mtzstudio.action.CLOUD_BACKUP"
        private const val ACTION_RESTORE = "dev.glorioustr.mtzstudio.action.CLOUD_RESTORE"
        private const val CHANNEL_ID = "cloud_transfer"
        private const val NOTIFICATION_ID = 4402

        fun startBackup(context: Context) = start(context, ACTION_BACKUP)
        fun startRestore(context: Context) = start(context, ACTION_RESTORE)

        private fun start(context: Context, action: String) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, CloudTransferService::class.java).setAction(action),
            )
        }
    }
}

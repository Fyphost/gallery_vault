package com.vaultgallery.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vaultgallery.app.data.local.entity.MediaType
import com.vaultgallery.app.media.MediaImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Encrypts a batch of media items in the background so large imports survive the UI
 * being closed. Runs as a foreground service (data sync) with a neutral, disguise-
 * friendly notification.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importer: MediaImporter
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS) ?: return Result.failure()
        val types = inputData.getStringArray(KEY_TYPES) ?: return Result.failure()
        val albumId = inputData.getLong(KEY_ALBUM_ID, -1L).takeIf { it >= 0 }
        val deleteOriginal = inputData.getBoolean(KEY_DELETE_ORIGINAL, false)

        setForeground(createForegroundInfo(0, uris.size))

        var done = 0
        var failures = 0
        uris.forEachIndexed { index, uriStr ->
            val type = MediaType.valueOf(types.getOrElse(index) { MediaType.IMAGE.name })
            val result = importer.import(Uri.parse(uriStr), type, albumId, deleteOriginal)
            if (result.isFailure) failures++
            done++
            setProgress(workDataOf(KEY_PROGRESS to done, KEY_TOTAL to uris.size))
            setForeground(createForegroundInfo(done, uris.size))
        }
        return if (failures == uris.size && uris.isNotEmpty()) Result.failure() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0)

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        // Neutral copy that doesn't reveal a vault is in use.
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Optimizing storage")
            .setContentText("$done / $total")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_TYPES = "types"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_DELETE_ORIGINAL = "delete_original"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"

        private const val CHANNEL_ID = "vault_sync"
        private const val NOTIF_ID = 4242
        const val UNIQUE_WORK = "vault_import_work"
    }
}

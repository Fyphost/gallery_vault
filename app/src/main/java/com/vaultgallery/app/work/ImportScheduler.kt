package com.vaultgallery.app.work

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vaultgallery.app.data.local.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and enqueues [ImportWorker] requests for selected device media. */
@Singleton
class ImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue(
        items: List<Pair<Uri, MediaType>>,
        albumId: Long?,
        deleteOriginal: Boolean
    ) {
        if (items.isEmpty()) return
        val data = workDataOf(
            ImportWorker.KEY_URIS to items.map { it.first.toString() }.toTypedArray(),
            ImportWorker.KEY_TYPES to items.map { it.second.name }.toTypedArray(),
            ImportWorker.KEY_ALBUM_ID to (albumId ?: -1L),
            ImportWorker.KEY_DELETE_ORIGINAL to deleteOriginal
        )
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ImportWorker.UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

package com.vaultgallery.app.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.vaultgallery.app.data.local.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A device media file discovered via MediaStore, eligible for import. */
data class DeviceMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val type: MediaType,
    val sizeBytes: Long,
    val dateAdded: Long,
    val durationMs: Long,
    /** Coarse origin bucket (Camera / Downloads / SD card / Internal). */
    val bucket: String
)

/**
 * Reads images and videos from MediaStore. On Android 10+ (API 29) the EXTERNAL
 * collection spans every mounted volume (internal storage, SD card, etc.). On
 * Android 8/9 we use the classic EXTERNAL_CONTENT_URI and the bucket column is
 * only available from API 29, so it is queried conditionally.
 */
@Singleton
class MediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val supportsBucket = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun queryImages(): List<DeviceMedia> = query(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        type = MediaType.IMAGE
    )

    fun queryVideos(): List<DeviceMedia> = query(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        type = MediaType.VIDEO
    )

    private fun query(collection: Uri, type: MediaType): List<DeviceMedia> {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        if (supportsBucket) projection += MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        if (type == MediaType.VIDEO) projection += MediaStore.Video.Media.DURATION

        val result = mutableListOf<DeviceMedia>()
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection.toTypedArray(), null, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketCol = if (supportsBucket)
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) else -1
                val durationCol = if (type == MediaType.VIDEO)
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    result += DeviceMedia(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        displayName = cursor.getString(nameCol) ?: "unknown",
                        mimeType = cursor.getString(mimeCol) ?: "application/octet-stream",
                        type = type,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAdded = cursor.getLong(dateCol) * 1000L,
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Storage" else "Storage"
                    )
                }
            }
        return result
    }
}

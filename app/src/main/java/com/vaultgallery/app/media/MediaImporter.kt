package com.vaultgallery.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.vaultgallery.app.data.local.entity.MediaType
import com.vaultgallery.app.data.repository.VaultRepository
import com.vaultgallery.app.security.crypto.CryptoEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts a device media item into the vault:
 *  1. Stream-encrypt the original bytes into app-private storage (AES-256-GCM).
 *  2. Generate and encrypt a small JPEG thumbnail for fast grid rendering.
 *  3. Persist metadata via [VaultRepository].
 *  4. Optionally delete the plaintext source from the device gallery.
 */
@Singleton
class MediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoEngine: CryptoEngine,
    private val repository: VaultRepository
) {
    private val mediaDir: File
        get() = File(context.filesDir, "vault_media").apply { if (!exists()) mkdirs() }
    private val thumbDir: File
        get() = File(context.filesDir, "vault_thumbs").apply { if (!exists()) mkdirs() }

    suspend fun import(
        uri: Uri,
        type: MediaType,
        albumId: Long?,
        deleteOriginal: Boolean
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val (displayName, mime, size) = readBasics(uri, type)
            val token = UUID.randomUUID().toString()
            val encFile = File(mediaDir, "$token.enc")

            // 1. Encrypt the media bytes (streamed).
            context.contentResolver.openInputStream(uri)?.use { input ->
                encFile.outputStream().use { out -> cryptoEngine.encryptFile(input, out) }
            } ?: error("Unable to open source stream")

            // 2. Metadata + thumbnail.
            var width = 0
            var height = 0
            var durationMs = 0L
            var thumbPath: String? = null
            val thumbBytes: ByteArray? = when (type) {
                MediaType.IMAGE -> {
                    val dims = readImageDimensions(uri)
                    width = dims.first; height = dims.second
                    makeImageThumbnail(uri)
                }
                MediaType.VIDEO -> {
                    val meta = readVideoMetadata(uri)
                    width = meta.width; height = meta.height; durationMs = meta.durationMs
                    meta.thumbnail
                }
            }
            if (thumbBytes != null) {
                val tFile = File(thumbDir, "$token.tenc")
                tFile.outputStream().use { out ->
                    cryptoEngine.encryptFile(ByteArrayInputStream(thumbBytes), out)
                }
                thumbPath = tFile.absolutePath
            }

            // 3. Persist.
            val id = repository.addImportedItem(
                displayName = displayName,
                type = type,
                mimeType = mime,
                encryptedPath = encFile.absolutePath,
                thumbnailPath = thumbPath,
                sizeBytes = size,
                widthPx = width,
                heightPx = height,
                durationMs = durationMs,
                originalUri = uri.toString(),
                albumId = albumId
            )

            // 4. Remove the plaintext source so it leaves the device gallery.
            if (deleteOriginal) {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            id
        }
    }

    private fun readBasics(uri: Uri, type: MediaType): Triple<String, String, Long> {
        var name = "media_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
        val mime = context.contentResolver.getType(uri)
            ?: if (type == MediaType.VIDEO) "video/*" else "image/*"
        return Triple(name, mime, size)
    }

    private fun readImageDimensions(uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        return opts.outWidth to opts.outHeight
    }

    private fun makeImageThumbnail(uri: Uri): ByteArray? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        bmp.toJpegBytes().also { bmp.recycle() }
    }.getOrNull()

    private data class VideoMeta(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val thumbnail: ByteArray?
    )

    private fun readVideoMetadata(uri: Uri): VideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(1_000_000)?.let { it.toJpegBytes().also { _ -> it.recycle() } }
            VideoMeta(w, h, dur, frame)
        } catch (_: Exception) {
            VideoMeta(0, 0, 0L, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun Bitmap.toJpegBytes(quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}

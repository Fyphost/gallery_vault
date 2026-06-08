package com.vaultgallery.app.data.repository

import com.vaultgallery.app.data.local.dao.AlbumDao
import com.vaultgallery.app.data.local.dao.IntruderDao
import com.vaultgallery.app.data.local.dao.MediaDao
import com.vaultgallery.app.data.local.entity.AlbumEntity
import com.vaultgallery.app.data.local.entity.IntruderLogEntity
import com.vaultgallery.app.data.local.entity.MediaItemEntity
import com.vaultgallery.app.data.local.entity.MediaType
import com.vaultgallery.app.data.prefs.SortOrder
import com.vaultgallery.app.domain.model.Album
import com.vaultgallery.app.domain.model.IntruderEvent
import com.vaultgallery.app.domain.model.MediaItem
import com.vaultgallery.app.security.VaultSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for vault content. Coordinates Room metadata with the
 * encrypted file blobs and respects the active [VaultSession] scope so the decoy
 * vault and the real vault never leak into one another.
 */
@Singleton
class VaultRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val albumDao: AlbumDao,
    private val intruderDao: IntruderDao,
    private val session: VaultSession
) {
    private val decoy: Boolean get() = session.isDecoy

    fun observeMedia(
        type: MediaType?,
        query: String,
        sortOrder: SortOrder
    ): Flow<List<MediaItem>> =
        mediaDao.observeMedia(decoy, type, query).map { list ->
            list.sortedWith(comparatorFor(sortOrder)).map { it.toDomain() }
        }

    fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAlbumsWithCount(decoy).map { rows ->
            rows.map { Album(it.album.id, it.album.name, it.album.createdAt, it.album.coverItemId, it.itemCount) }
        }

    fun observeAlbumItems(albumId: Long, sortOrder: SortOrder): Flow<List<MediaItem>> =
        mediaDao.observeByAlbum(albumId, decoy).map { list ->
            list.sortedWith(comparatorFor(sortOrder)).map { it.toDomain() }
        }

    fun observeFavorites(): Flow<List<MediaItem>> =
        mediaDao.observeFavorites(decoy).map { it.map(MediaItemEntity::toDomain) }

    suspend fun getItem(id: Long): MediaItem? = mediaDao.getById(id)?.toDomain()

    /** Persists metadata for a newly encrypted file. Called by the import pipeline. */
    suspend fun addImportedItem(
        displayName: String,
        type: MediaType,
        mimeType: String,
        encryptedPath: String,
        thumbnailPath: String?,
        sizeBytes: Long,
        widthPx: Int,
        heightPx: Int,
        durationMs: Long,
        originalUri: String?,
        albumId: Long?
    ): Long = mediaDao.insert(
        MediaItemEntity(
            displayName = displayName,
            type = type,
            mimeType = mimeType,
            encryptedPath = encryptedPath,
            thumbnailPath = thumbnailPath,
            sizeBytes = sizeBytes,
            widthPx = widthPx,
            heightPx = heightPx,
            durationMs = durationMs,
            originalUri = originalUri,
            albumId = albumId,
            addedAt = System.currentTimeMillis(),
            isDecoy = decoy
        )
    )

    suspend fun savePlaybackPosition(id: Long, positionMs: Long) =
        mediaDao.updatePlaybackPosition(id, positionMs)

    suspend fun setFavorite(id: Long, favorite: Boolean) = mediaDao.setFavorite(id, favorite)

    suspend fun moveToAlbum(ids: List<Long>, albumId: Long?) = mediaDao.moveToAlbum(ids, albumId)

    /** Deletes metadata AND securely removes the encrypted blobs from disk. */
    suspend fun deleteItems(ids: List<Long>) {
        ids.forEach { id ->
            mediaDao.getById(id)?.let { item ->
                File(item.encryptedPath).delete()
                item.thumbnailPath?.let { File(it).delete() }
            }
        }
        mediaDao.deleteByIds(ids)
    }

    suspend fun createAlbum(name: String): Long =
        albumDao.insert(AlbumEntity(name = name, createdAt = System.currentTimeMillis(), isDecoy = decoy))

    suspend fun renameAlbum(id: Long, name: String) {
        albumDao.getById(id)?.let { albumDao.update(it.copy(name = name)) }
    }

    suspend fun deleteAlbum(id: Long) = albumDao.delete(id)

    // ---- Intruder log ----
    fun observeIntruderEvents(): Flow<List<IntruderEvent>> =
        intruderDao.observeAll().map { list ->
            list.map { IntruderEvent(it.id, it.capturedAt, it.encryptedPath, it.enteredValueLength, it.unlockMethod) }
        }

    suspend fun logIntruder(encryptedPath: String, enteredLength: Int, method: String) =
        intruderDao.insert(
            IntruderLogEntity(
                capturedAt = System.currentTimeMillis(),
                encryptedPath = encryptedPath,
                enteredValueLength = enteredLength,
                unlockMethod = method
            )
        )

    private fun comparatorFor(order: SortOrder): Comparator<MediaItemEntity> = when (order) {
        SortOrder.DATE_DESC -> compareByDescending { it.addedAt }
        SortOrder.DATE_ASC -> compareBy { it.addedAt }
        SortOrder.NAME -> compareBy { it.displayName.lowercase() }
        SortOrder.SIZE -> compareByDescending { it.sizeBytes }
    }
}

private fun MediaItemEntity.toDomain() = MediaItem(
    id = id,
    displayName = displayName,
    type = type,
    mimeType = mimeType,
    encryptedPath = encryptedPath,
    thumbnailPath = thumbnailPath,
    sizeBytes = sizeBytes,
    widthPx = widthPx,
    heightPx = heightPx,
    durationMs = durationMs,
    lastPositionMs = lastPositionMs,
    albumId = albumId,
    addedAt = addedAt,
    isFavorite = isFavorite
)

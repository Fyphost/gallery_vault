package com.vaultgallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultgallery.app.data.local.entity.MediaItemEntity
import com.vaultgallery.app.data.local.entity.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItemEntity): Long

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("UPDATE media_items SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long)

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE media_items SET albumId = :albumId WHERE id IN (:ids)")
    suspend fun moveToAlbum(ids: List<Long>, albumId: Long?)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItemEntity?

    /**
     * Main feed query with decoy isolation, optional type filter and search.
     * Sorting is applied in the repository layer because Room can't easily switch
     * ORDER BY columns dynamically without raw queries.
     */
    @Query(
        """
        SELECT * FROM media_items
        WHERE isDecoy = :decoy
          AND (:type IS NULL OR type = :type)
          AND (:query = '' OR displayName LIKE '%' || :query || '%')
        """
    )
    fun observeMedia(decoy: Boolean, type: MediaType?, query: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE albumId = :albumId AND isDecoy = :decoy")
    fun observeByAlbum(albumId: Long, decoy: Boolean): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isDecoy = :decoy")
    fun observeFavorites(decoy: Boolean): Flow<List<MediaItemEntity>>
}

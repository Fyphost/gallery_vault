package com.vaultgallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultgallery.app.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

/** Album row plus a denormalized item count for the album grid. */
data class AlbumWithCount(
    @Embedded val album: AlbumEntity,
    val itemCount: Int
)

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: Long): AlbumEntity?

    @Query(
        """
        SELECT a.*, (
            SELECT COUNT(*) FROM media_items m WHERE m.albumId = a.id
        ) AS itemCount
        FROM albums a
        WHERE a.isDecoy = :decoy
        ORDER BY a.createdAt DESC
        """
    )
    fun observeAlbumsWithCount(decoy: Boolean): Flow<List<AlbumWithCount>>
}

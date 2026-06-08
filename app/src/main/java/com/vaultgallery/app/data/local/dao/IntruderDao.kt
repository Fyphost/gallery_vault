package com.vaultgallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vaultgallery.app.data.local.entity.IntruderLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderDao {
    @Insert
    suspend fun insert(log: IntruderLogEntity): Long

    @Query("SELECT * FROM intruder_logs ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<IntruderLogEntity>>

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM intruder_logs")
    suspend fun clear()
}

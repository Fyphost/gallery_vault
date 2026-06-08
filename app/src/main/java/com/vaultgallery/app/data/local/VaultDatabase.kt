package com.vaultgallery.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.vaultgallery.app.data.local.dao.AlbumDao
import com.vaultgallery.app.data.local.dao.IntruderDao
import com.vaultgallery.app.data.local.dao.MediaDao
import com.vaultgallery.app.data.local.entity.AlbumEntity
import com.vaultgallery.app.data.local.entity.IntruderLogEntity
import com.vaultgallery.app.data.local.entity.MediaItemEntity
import com.vaultgallery.app.data.local.entity.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(type: MediaType): String = type.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)
}

@Database(
    entities = [MediaItemEntity::class, AlbumEntity::class, IntruderLogEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun intruderDao(): IntruderDao

    companion object {
        const val NAME = "vault.db"
    }
}

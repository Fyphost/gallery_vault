package com.vaultgallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Media kind discriminator stored in the DB. */
enum class MediaType { IMAGE, VIDEO }

/**
 * Metadata for one encrypted item. The actual bytes live in an encrypted file on
 * internal storage referenced by [encryptedPath]; only metadata is in Room.
 */
@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("albumId"), Index("addedAt"), Index("type")]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val type: MediaType,
    val mimeType: String,
    /** Absolute path of the AES-256 encrypted blob inside app-private storage. */
    val encryptedPath: String,
    /** Path of the encrypted thumbnail blob (nullable until generated). */
    val thumbnailPath: String?,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    /** Video duration in ms (0 for images). */
    val durationMs: Long,
    /** Last playback position in ms for resume support. */
    val lastPositionMs: Long = 0,
    val albumId: Long? = null,
    val addedAt: Long,
    /** Original source uri string, kept so we can optionally restore/export. */
    val originalUri: String?,
    val isFavorite: Boolean = false,
    /** Whether this item belongs to the decoy vault rather than the real one. */
    val isDecoy: Boolean = false
)

@Entity(tableName = "albums", indices = [Index(value = ["name"], unique = false)])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val coverItemId: Long? = null,
    val isDecoy: Boolean = false
)

/** A captured intruder selfie with context about the failed unlock attempt. */
@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    /** Encrypted selfie file path. */
    val encryptedPath: String,
    val enteredValueLength: Int,
    val unlockMethod: String
)

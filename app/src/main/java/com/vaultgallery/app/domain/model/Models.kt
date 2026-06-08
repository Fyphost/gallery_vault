package com.vaultgallery.app.domain.model

import com.vaultgallery.app.data.local.entity.MediaType

/** UI/domain representation of a vault media item. */
data class MediaItem(
    val id: Long,
    val displayName: String,
    val type: MediaType,
    val mimeType: String,
    val encryptedPath: String,
    val thumbnailPath: String?,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val durationMs: Long,
    val lastPositionMs: Long,
    val albumId: Long?,
    val addedAt: Long,
    val isFavorite: Boolean
) {
    val isVideo: Boolean get() = type == MediaType.VIDEO
}

data class Album(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val coverItemId: Long?,
    val itemCount: Int
)

data class IntruderEvent(
    val id: Long,
    val capturedAt: Long,
    val encryptedPath: String,
    val enteredValueLength: Int,
    val unlockMethod: String
)

/** Which logical vault is currently active. */
enum class VaultScope { REAL, DECOY }

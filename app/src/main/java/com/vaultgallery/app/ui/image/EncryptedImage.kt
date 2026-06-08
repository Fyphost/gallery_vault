package com.vaultgallery.app.ui.image

/**
 * Coil model pointing at an encrypted thumbnail (or full image) blob on disk.
 * Resolved by [EncryptedImageFetcher], which decrypts in-memory before decoding.
 */
data class EncryptedImage(val encryptedPath: String)

package com.vaultgallery.app.media

import androidx.core.content.FileProvider

/**
 * Dedicated FileProvider subclass so its authority is unique to this app. Used only
 * to hand short-lived, decrypted temp files to an external player. Temp files are
 * deleted as soon as playback finishes.
 */
class SecureFileProvider : FileProvider()

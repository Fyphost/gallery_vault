package com.vaultgallery.app.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.vaultgallery.app.domain.model.MediaItem
import com.vaultgallery.app.security.crypto.CryptoEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares an encrypted vault item for playback.
 *
 * GCM streams don't support random seeking, so for a smooth player experience we
 * decrypt the item into a private cache file ([file_paths.xml] -> vault_temp) that
 * is wiped as soon as playback ends or the vault locks. This keeps full seek,
 * hardware-decode and external-player support while never leaving plaintext in a
 * user-visible location.
 */
@Singleton
class PlaybackPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoEngine: CryptoEngine
) {
    private val tempDir: File
        get() = File(context.cacheDir, "vault_temp").apply { if (!exists()) mkdirs() }

    /** Decrypts [item] to a temp file and returns it. */
    suspend fun decryptToTemp(item: MediaItem): File = withContext(Dispatchers.IO) {
        val ext = item.displayName.substringAfterLast('.', "dat")
        val out = File(tempDir, "play_${item.id}.$ext")
        if (!out.exists()) {
            File(item.encryptedPath).inputStream().use { encIn ->
                out.outputStream().use { plainOut ->
                    cryptoEngine.decryptFile(encIn, plainOut)
                }
            }
        }
        out
    }

    /** Content URI usable by an external player, granted read permission. */
    fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Wipes all decrypted temp files (call on player exit / vault lock). */
    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }
}

package com.vaultgallery.app.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.vaultgallery.app.security.crypto.CryptoEngine
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * A Media3 [DataSource] that streams an AES-256 encrypted vault file straight into
 * ExoPlayer, decrypting on the fly. This avoids decrypting the entire file to a temp
 * file before playback (which made large videos slow to start) and keeps plaintext
 * off disk entirely for the built-in player.
 *
 * The encrypted file is addressed with the [SCHEME] custom URI scheme, e.g.
 * `vaultenc:///data/.../vault_media/abc.enc`, so this source is only used for vault
 * blobs (subtitles and other URIs use the default sources).
 *
 * GCM is not randomly seekable, so a non-zero start position is handled by decrypting
 * from the beginning and discarding bytes up to the requested offset. Forward
 * playback (the common case) starts immediately.
 */
@UnstableApi
class EncryptedDataSource(
    private val cryptoEngine: CryptoEngine
) : BaseDataSource(/* isNetwork = */ false) {

    private var uri: Uri? = null
    private var input: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val path = dataSpec.uri.path ?: throw IOException("EncryptedDataSource: missing path")
        val file = File(path)
        val stream = cryptoEngine.openDecryptingStream(file)

        // Skip to the requested start position (re-decrypt from start; GCM can't seek).
        var toSkip = dataSpec.position
        val skipBuffer = ByteArray(64 * 1024)
        while (toSkip > 0) {
            val read = stream.read(skipBuffer, 0, min(toSkip, skipBuffer.size.toLong()).toInt())
            if (read == -1) break
            toSkip -= read
        }

        input = stream
        opened = true
        transferStarted(dataSpec)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            C.LENGTH_UNSET.toLong()
        }
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val stream = input ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            min(bytesRemaining, length.toLong()).toInt()
        }
        if (toRead == 0) return C.RESULT_END_OF_INPUT
        val read = stream.read(buffer, offset, toRead)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            input?.close()
        } catch (e: IOException) {
            throw e
        } finally {
            input = null
            uri = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val cryptoEngine: CryptoEngine) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedDataSource(cryptoEngine)
    }

    companion object {
        const val SCHEME = "vaultenc"

        /** Builds a [Uri] this source understands from an encrypted file path. */
        fun uriFor(encryptedPath: String): Uri =
            Uri.Builder().scheme(SCHEME).path(encryptedPath).build()
    }
}

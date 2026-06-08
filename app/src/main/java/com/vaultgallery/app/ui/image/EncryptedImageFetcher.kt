package com.vaultgallery.app.ui.image

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.vaultgallery.app.security.crypto.CryptoEngine
import okio.Buffer
import okio.buffer
import okio.source
import java.io.File

/**
 * Coil [Fetcher] that streams an AES-256 encrypted blob through [CryptoEngine] and
 * feeds the decrypted bytes to Coil's decoders. Plaintext only ever exists in
 * memory for the lifetime of the decode.
 */
class EncryptedImageFetcher(
    private val data: EncryptedImage,
    private val cryptoEngine: CryptoEngine,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val file = File(data.encryptedPath)
        val buffer = Buffer()
        cryptoEngine.openDecryptingStream(file).source().buffer().use { src ->
            buffer.writeAll(src)
        }
        return SourceResult(
            // Context-based ImageSource factory (stable across Coil 2.x). The decrypted
            // bytes live only in this in-memory Buffer.
            source = ImageSource(source = buffer, context = context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val cryptoEngine: CryptoEngine,
        private val context: Context
    ) : Fetcher.Factory<EncryptedImage> {
        override fun create(data: EncryptedImage, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedImageFetcher(data, cryptoEngine, context)
    }
}

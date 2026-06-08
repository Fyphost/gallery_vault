package com.vaultgallery.app.ui.image

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
    private val cryptoEngine: CryptoEngine
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val file = File(data.encryptedPath)
        val buffer = Buffer()
        cryptoEngine.openDecryptingStream(file).source().buffer().use { src ->
            buffer.writeAll(src)
        }
        return SourceResult(
            source = ImageSource(buffer, fileSystem = okio.FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val cryptoEngine: CryptoEngine) : Fetcher.Factory<EncryptedImage> {
        override fun create(data: EncryptedImage, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedImageFetcher(data, cryptoEngine)
    }
}

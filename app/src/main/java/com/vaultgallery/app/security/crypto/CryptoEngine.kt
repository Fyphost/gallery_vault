package com.vaultgallery.app.security.crypto

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM streaming encryption for media files.
 *
 * File layout on disk:
 *   [1 byte IV length][IV bytes][GCM ciphertext + auth tag]
 *
 * GCM gives us confidentiality *and* integrity (tampering is detected on decrypt).
 * We stream through fixed-size buffers so multi-GB videos never load fully into RAM.
 */
@Singleton
class CryptoEngine @Inject constructor(
    private val keystoreManager: KeystoreManager
) {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val BUFFER_SIZE = 64 * 1024
    }

    /**
     * Encrypts [source] into [dest]. Returns the IV used (also persisted in the
     * file header so decryption is self-contained).
     */
    fun encryptFile(source: InputStream, dest: OutputStream) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keystoreManager.getOrCreateMasterKey())
        }
        val iv = cipher.iv
        // Header: IV length + IV
        dest.write(iv.size)
        dest.write(iv)
        CipherOutputStream(dest, cipher).use { cipherOut ->
            source.copyTo(cipherOut, BUFFER_SIZE)
        }
    }

    /** Decrypts a vault file previously produced by [encryptFile] into [dest]. */
    fun decryptFile(source: InputStream, dest: OutputStream) {
        val ivLength = source.read()
        require(ivLength in 1..32) { "Corrupt vault file: invalid IV length" }
        val iv = ByteArray(ivLength)
        var read = 0
        while (read < ivLength) {
            val r = source.read(iv, read, ivLength - read)
            if (r == -1) error("Corrupt vault file: truncated IV")
            read += r
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                keystoreManager.getOrCreateMasterKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
        }
        CipherInputStream(source, cipher).use { cipherIn ->
            cipherIn.copyTo(dest, BUFFER_SIZE)
        }
    }

    /**
     * Returns a decrypting [InputStream] for [encryptedFile] so the player /
     * thumbnailer can read media without writing plaintext to disk.
     */
    fun openDecryptingStream(encryptedFile: File): InputStream {
        val raw = encryptedFile.inputStream()
        val ivLength = raw.read()
        require(ivLength in 1..32) { "Corrupt vault file: invalid IV length" }
        val iv = ByteArray(ivLength)
        var read = 0
        while (read < ivLength) {
            val r = raw.read(iv, read, ivLength - read)
            if (r == -1) error("Corrupt vault file: truncated IV")
            read += r
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                keystoreManager.getOrCreateMasterKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
        }
        return CipherInputStream(raw, cipher)
    }
}

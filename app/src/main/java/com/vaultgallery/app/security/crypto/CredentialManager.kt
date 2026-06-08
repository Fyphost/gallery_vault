package com.vaultgallery.app.security.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

/**
 * Stores and verifies the real vault PIN/password and the optional fake-vault
 * (decoy) PIN. Secrets are never stored in plaintext: we keep a PBKDF2 hash and a
 * per-credential random salt inside [EncryptedSharedPreferences].
 */
@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "vault_credentials"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 256

        private const val K_REAL_HASH = "real_hash"
        private const val K_REAL_SALT = "real_salt"
        private const val K_FAKE_HASH = "fake_hash"
        private const val K_FAKE_SALT = "fake_salt"
        private const val K_FAILED_ATTEMPTS = "failed_attempts"
    }

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isVaultConfigured: Boolean get() = prefs.contains(K_REAL_HASH)
    val isFakeVaultConfigured: Boolean get() = prefs.contains(K_FAKE_HASH)

    fun setRealCredential(secret: String) = store(secret, K_REAL_HASH, K_REAL_SALT)
    fun setFakeCredential(secret: String) = store(secret, K_FAKE_HASH, K_FAKE_SALT)

    fun clearFakeCredential() {
        prefs.edit().remove(K_FAKE_HASH).remove(K_FAKE_SALT).apply()
    }

    /** Result of a credential check, distinguishing the real vault from the decoy. */
    enum class Match { REAL, FAKE, NONE }

    fun verify(secret: String): Match {
        if (matches(secret, K_REAL_HASH, K_REAL_SALT)) return Match.REAL
        if (matches(secret, K_FAKE_HASH, K_FAKE_SALT)) return Match.FAKE
        return Match.NONE
    }

    var failedAttempts: Int
        get() = prefs.getInt(K_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(K_FAILED_ATTEMPTS, value).apply()

    fun resetFailedAttempts() { failedAttempts = 0 }

    private fun store(secret: String, hashKey: String, saltKey: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(secret, salt)
        prefs.edit()
            .putString(hashKey, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(saltKey, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
    }

    private fun matches(secret: String, hashKey: String, saltKey: String): Boolean {
        val storedHash = prefs.getString(hashKey, null) ?: return false
        val storedSalt = prefs.getString(saltKey, null) ?: return false
        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val candidate = pbkdf2(secret, salt)
        // Constant-time comparison to avoid timing side channels.
        return constantTimeEquals(
            candidate,
            Base64.decode(storedHash, Base64.NO_WRAP)
        )
    }

    private fun pbkdf2(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}

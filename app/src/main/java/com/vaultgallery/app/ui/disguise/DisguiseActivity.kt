package com.vaultgallery.app.ui.disguise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.vaultgallery.app.MainActivity
import com.vaultgallery.app.domain.model.VaultScope
import com.vaultgallery.app.security.VaultSession
import com.vaultgallery.app.security.crypto.CredentialManager
import com.vaultgallery.app.ui.LocaleManager
import com.vaultgallery.app.ui.theme.VaultTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The only LAUNCHER activity. To anyone browsing the home screen this is just a
 * calculator. The vault opens only when the user types their secret PIN and presses
 * "=" — a sequence indistinguishable from normal calculator use.
 *
 * A separate decoy PIN opens the fake vault instead, giving plausible deniability
 * under coercion.
 */
@AndroidEntryPoint
class DisguiseActivity : FragmentActivity() {

    @Inject lateinit var credentialManager: CredentialManager
    @Inject lateinit var session: VaultSession

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultTheme {
                CalculatorScreen(
                    onSecretEntered = { entered -> handleSecret(entered) }
                )
            }
        }
    }

    /**
     * Called when the user presses "=" with a pure-numeric expression. Verification
     * (Keystore + PBKDF2) runs off the main thread so the calculator stays snappy;
     * if the entry matches a configured credential we open the corresponding vault.
     * Otherwise nothing happens and the calculator just shows the computed result.
     */
    private fun handleSecret(entered: String) {
        lifecycleScope.launch {
            val matched = withContext(Dispatchers.Default) {
                if (!credentialManager.isVaultConfigured) {
                    if (entered == FIRST_RUN_OPENER) VaultScope.REAL else null
                } else when (credentialManager.verify(entered)) {
                    CredentialManager.Match.REAL -> VaultScope.REAL
                    CredentialManager.Match.FAKE -> VaultScope.DECOY
                    CredentialManager.Match.NONE -> null
                }
            }
            matched?.let { launchVault(it) }
        }
    }

    private fun launchVault(scope: VaultScope) {
        session.unlock(scope)
        startActivity(Intent(this, MainActivity::class.java))
    }

    companion object {
        // Documented opener so a brand-new install can reach onboarding: "0000="
        const val FIRST_RUN_OPENER = "0000"
    }
}

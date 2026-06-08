package com.vaultgallery.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.ui.LocaleManager
import com.vaultgallery.app.ui.VaultApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Real vault host. Not exported and never a launcher — only reachable after the
 * disguise calculator validates a credential. Hosts the entire Compose UI graph.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var preferences: VaultPreferences

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockManager.register()
        applyScreenshotProtection()
        enableEdgeToEdge()
        setContent {
            VaultApp()
        }
    }

    /** Honors the "Block screenshots" setting by toggling FLAG_SECURE live. */
    private fun applyScreenshotProtection() {
        lifecycleScope.launch {
            preferences.settings.collectLatest { settings ->
                if (settings.blockScreenshots) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}

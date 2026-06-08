package com.vaultgallery.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.prefs.ThemeMode
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.data.prefs.VaultSettings
import com.vaultgallery.app.security.crypto.CredentialManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: VaultPreferences,
    private val credentialManager: CredentialManager
) : ViewModel() {

    val settings: StateFlow<VaultSettings?> =
        preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }
    fun setDynamicColor(value: Boolean) = launch { preferences.setDynamicColor(value) }
    fun setBiometric(value: Boolean) = launch { preferences.setBiometric(value) }
    fun setAutoLock(value: Boolean) = launch { preferences.setAutoLock(value) }
    fun setBlockScreenshots(value: Boolean) = launch { preferences.setBlockScreenshots(value) }
    fun setIntruderSelfie(value: Boolean) = launch { preferences.setIntruderSelfie(value) }
    fun setUseExternalPlayer(value: Boolean) = launch { preferences.setUseExternalPlayer(value) }
    fun setLanguage(tag: String) = launch { preferences.setLanguage(tag) }

    fun setDecoyEnabled(value: Boolean, decoyPin: String?) = launch {
        preferences.setDecoyEnabled(value)
        if (value && !decoyPin.isNullOrBlank()) {
            credentialManager.setFakeCredential(decoyPin)
        } else if (!value) {
            credentialManager.clearFakeCredential()
        }
    }

    fun changePin(newPin: String) = launch { credentialManager.setRealCredential(newPin) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

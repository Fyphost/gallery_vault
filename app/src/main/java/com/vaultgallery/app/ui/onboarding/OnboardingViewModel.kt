package com.vaultgallery.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.security.VaultSession
import com.vaultgallery.app.domain.model.VaultScope
import com.vaultgallery.app.security.crypto.CredentialManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val credentialManager: CredentialManager,
    private val preferences: VaultPreferences,
    private val session: VaultSession
) : ViewModel() {

    /** Persists the chosen PIN, marks onboarding complete and unlocks the session. */
    fun completeSetup(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            credentialManager.setRealCredential(pin)
            preferences.setOnboarded(true)
            session.unlock(VaultScope.REAL)
            onDone()
        }
    }
}

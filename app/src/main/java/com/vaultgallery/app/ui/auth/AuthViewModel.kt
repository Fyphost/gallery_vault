package com.vaultgallery.app.ui.auth

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.domain.model.VaultScope
import com.vaultgallery.app.security.IntruderCaptureManager
import com.vaultgallery.app.security.VaultSession
import com.vaultgallery.app.security.crypto.CredentialManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val pin: String = "",
    val error: String? = null,
    val isConfigured: Boolean = false,
    val biometricEnabled: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val credentialManager: CredentialManager,
    private val session: VaultSession,
    private val preferences: VaultPreferences,
    private val intruderCaptureManager: IntruderCaptureManager
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(isConfigured = credentialManager.isVaultConfigured)
        viewModelScope.launch {
            val s = preferences.settings.first()
            _state.value = _state.value.copy(biometricEnabled = s.biometricEnabled)
        }
    }

    fun onPinChange(value: String) {
        _state.value = _state.value.copy(pin = value.filter { it.isDigit() }, error = null)
    }

    /** Verifies the typed PIN; on intruder threshold, fires a silent selfie. */
    fun submitPin(activity: LifecycleOwner, onUnlocked: () -> Unit) {
        val pin = _state.value.pin
        when (credentialManager.verify(pin)) {
            CredentialManager.Match.REAL -> unlock(VaultScope.REAL, onUnlocked)
            CredentialManager.Match.FAKE -> unlock(VaultScope.DECOY, onUnlocked)
            CredentialManager.Match.NONE -> handleFailure(activity, pin.length, "PIN")
        }
    }

    fun onBiometricSuccess(onUnlocked: () -> Unit) {
        credentialManager.resetFailedAttempts()
        session.unlock(VaultScope.REAL)
        onUnlocked()
    }

    private fun unlock(scope: VaultScope, onUnlocked: () -> Unit) {
        credentialManager.resetFailedAttempts()
        session.unlock(scope)
        _state.value = _state.value.copy(pin = "", error = null)
        onUnlocked()
    }

    private fun handleFailure(activity: LifecycleOwner, length: Int, method: String) {
        credentialManager.failedAttempts += 1
        _state.value = _state.value.copy(error = "wrong", pin = "")
        viewModelScope.launch {
            val settings = preferences.settings.first()
            if (settings.intruderSelfie &&
                credentialManager.failedAttempts >= settings.intruderThreshold
            ) {
                intruderCaptureManager.captureIntruder(activity, length, method)
            }
        }
    }
}

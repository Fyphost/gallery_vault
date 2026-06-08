package com.vaultgallery.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.data.prefs.VaultSettings
import com.vaultgallery.app.security.VaultSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    preferences: VaultPreferences,
    session: VaultSession
) : ViewModel() {

    val settings: StateFlow<VaultSettings?> =
        preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val unlocked: StateFlow<Boolean> = session.unlocked
}

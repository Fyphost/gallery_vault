package com.vaultgallery.app.security

import com.vaultgallery.app.domain.model.VaultScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory unlock state for the current process. Reset to locked whenever the app
 * goes to the background (when auto-lock is enabled) or after an inactivity timeout.
 */
@Singleton
class VaultSession @Inject constructor() {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _scope = MutableStateFlow(VaultScope.REAL)
    val scope: StateFlow<VaultScope> = _scope.asStateFlow()

    var lastInteractionAt: Long = 0L
        private set

    fun unlock(scope: VaultScope) {
        _scope.value = scope
        _unlocked.value = true
        touch()
    }

    fun lock() {
        _unlocked.value = false
    }

    fun touch() {
        lastInteractionAt = System.currentTimeMillis()
    }

    val isDecoy: Boolean get() = _scope.value == VaultScope.DECOY
}

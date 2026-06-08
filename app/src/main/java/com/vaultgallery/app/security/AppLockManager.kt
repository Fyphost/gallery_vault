package com.vaultgallery.app.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.media.PlaybackPreparer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the whole-process lifecycle and locks the vault when the app is sent to
 * the background, implementing "auto-lock when minimized". Registered once from
 * [com.vaultgallery.app.MainActivity].
 */
@Singleton
class AppLockManager @Inject constructor(
    private val session: VaultSession,
    private val preferences: VaultPreferences,
    private val playbackPreparer: PlaybackPreparer
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob())
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        // App moved to background. Wipe any decrypted playback temp files now (the
        // external player, if any, has already received its copy by this point).
        playbackPreparer.clearTemp()
        scope.launch {
            val autoLock = preferences.settings.first().autoLock
            if (autoLock) session.lock()
        }
    }
}

package com.vaultgallery.app.ui.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.data.repository.VaultRepository
import com.vaultgallery.app.domain.model.MediaItem
import com.vaultgallery.app.media.ExternalPlayerLauncher
import com.vaultgallery.app.media.PlaybackPreparer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    val localFile: File? = null,
    val resumePositionMs: Long = 0,
    val subtitleUri: Uri? = null,
    val error: String? = null
)

/** Event the screen acts on (launching an external player needs Activity context). */
data class LaunchExternalEvent(
    val uri: Uri,
    val mimeType: String,
    val packageName: String,
    val closeAfter: Boolean
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val preparer: PlaybackPreparer,
    private val preferences: VaultPreferences,
    private val externalLauncher: ExternalPlayerLauncher
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle["itemId"])

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val externalEvents = Channel<LaunchExternalEvent>(Channel.BUFFERED)
    val externalEventFlow = externalEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val item = repository.getItem(itemId)
            if (item == null) {
                _state.value = PlayerUiState(loading = false, error = "Item not found")
                return@launch
            }
            val settings = preferences.settings.first()
            val temp = preparer.decryptToTemp(item)

            if (settings.useExternalPlayer &&
                externalLauncher.isInstalled(settings.externalPlayerPackage)
            ) {
                // Seamless handoff: decrypt, then launch external player.
                externalEvents.send(
                    LaunchExternalEvent(
                        uri = preparer.contentUriFor(temp),
                        mimeType = item.mimeType,
                        packageName = settings.externalPlayerPackage,
                        closeAfter = true
                    )
                )
                _state.value = PlayerUiState(loading = false, item = item, localFile = temp)
            } else {
                _state.value = PlayerUiState(
                    loading = false,
                    item = item,
                    localFile = temp,
                    resumePositionMs = item.lastPositionMs
                )
            }
        }
    }

    fun setSubtitle(uri: Uri?) {
        _state.value = _state.value.copy(subtitleUri = uri)
    }

    /** User tapped "open externally": decrypt-on-demand handoff via system chooser. */
    fun requestExternalPlay() {
        val s = _state.value
        val item = s.item ?: return
        val file = s.localFile ?: return
        viewModelScope.launch {
            val settings = preferences.settings.first()
            val pkg = if (externalLauncher.isInstalled(settings.externalPlayerPackage))
                settings.externalPlayerPackage else ""
            externalEvents.send(
                LaunchExternalEvent(
                    uri = preparer.contentUriFor(file),
                    mimeType = item.mimeType,
                    packageName = pkg,
                    closeAfter = false
                )
            )
        }
    }

    /** Persists the resume position so the next open continues where we left off. */
    fun savePosition(positionMs: Long) {
        viewModelScope.launch { repository.savePlaybackPosition(itemId, positionMs) }
    }

    override fun onCleared() {
        super.onCleared()
        // Securely remove the decrypted temp file when leaving the player.
        preparer.clearTemp()
    }
}

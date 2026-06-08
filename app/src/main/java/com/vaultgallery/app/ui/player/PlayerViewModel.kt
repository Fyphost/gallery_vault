package com.vaultgallery.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.data.repository.VaultRepository
import com.vaultgallery.app.domain.model.MediaItem
import com.vaultgallery.app.media.EncryptedDataSource
import com.vaultgallery.app.media.ExternalPlayerLauncher
import com.vaultgallery.app.media.PlaybackPreparer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vaultgallery.app.security.crypto.CryptoEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    val usingExternalPlayer: Boolean = false,
    val error: String? = null
)

/** Event the screen acts on (launching an external player needs Activity context). */
data class LaunchExternalEvent(
    val uri: Uri,
    val mimeType: String,
    val packageName: String,
    val closeAfter: Boolean
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val preparer: PlaybackPreparer,
    private val preferences: VaultPreferences,
    private val externalLauncher: ExternalPlayerLauncher,
    private val cryptoEngine: CryptoEngine
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle["itemId"])

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val externalEvents = Channel<LaunchExternalEvent>(Channel.BUFFERED)
    val externalEventFlow = externalEvents.receiveAsFlow()

    /** The built-in player instance. Null when handing off to an external player. */
    var player: ExoPlayer? = null
        private set

    private var currentItem: MediaItem? = null
    private var subtitleUri: Uri? = null

    init {
        viewModelScope.launch {
            val item = repository.getItem(itemId)
            if (item == null) {
                _state.value = PlayerUiState(loading = false, error = "Item not found")
                return@launch
            }
            currentItem = item
            val settings = preferences.settings.first()

            if (settings.useExternalPlayer &&
                externalLauncher.isInstalled(settings.externalPlayerPackage)
            ) {
                // External handoff: decrypt to a temp file the other app can read.
                val temp = preparer.decryptToTemp(item)
                externalEvents.send(
                    LaunchExternalEvent(
                        uri = preparer.contentUriFor(temp),
                        mimeType = item.mimeType,
                        packageName = settings.externalPlayerPackage,
                        closeAfter = true
                    )
                )
                _state.value = PlayerUiState(loading = false, item = item, usingExternalPlayer = true)
            } else {
                // Built-in player: stream-decrypt straight into ExoPlayer (instant start).
                val exo = ExoPlayer.Builder(context).build().apply {
                    setMediaSource(buildMediaSource(item, null))
                    prepare()
                    if (item.lastPositionMs > 0) seekTo(item.lastPositionMs)
                    playWhenReady = true
                }
                player = exo
                _state.value = PlayerUiState(loading = false, item = item)
            }
        }
    }

    private fun buildMediaSource(item: MediaItem, subtitle: Uri?): MediaSource {
        val vaultUri = EncryptedDataSource.uriFor(item.encryptedPath)
        val encFactory = EncryptedDataSource.Factory(cryptoEngine)
        val videoSource = ProgressiveMediaSource.Factory(encFactory)
            .createMediaSource(ExoMediaItem.fromUri(vaultUri))
        if (subtitle == null) return videoSource

        val subConfig = ExoMediaItem.SubtitleConfiguration.Builder(subtitle)
            .setMimeType(subtitleMime(subtitle.toString()))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val subSource = SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context))
            .createMediaSource(subConfig, C.TIME_UNSET)
        return MergingMediaSource(videoSource, subSource)
    }

    fun setSubtitle(uri: Uri?) {
        subtitleUri = uri
        val item = currentItem ?: return
        val exo = player ?: return
        val position = exo.currentPosition
        exo.setMediaSource(buildMediaSource(item, uri))
        exo.prepare()
        exo.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }

    /** User tapped "open externally": decrypt-on-demand handoff via system chooser. */
    fun requestExternalPlay() {
        val item = currentItem ?: return
        viewModelScope.launch {
            val settings = preferences.settings.first()
            val pkg = if (externalLauncher.isInstalled(settings.externalPlayerPackage))
                settings.externalPlayerPackage else ""
            val temp = preparer.decryptToTemp(item)
            externalEvents.send(
                LaunchExternalEvent(
                    uri = preparer.contentUriFor(temp),
                    mimeType = item.mimeType,
                    packageName = pkg,
                    closeAfter = false
                )
            )
        }
    }

    /** Persists the resume position so the next open continues where we left off. */
    fun savePosition() {
        val position = player?.currentPosition ?: return
        viewModelScope.launch { repository.savePlaybackPosition(itemId, position) }
    }

    override fun onCleared() {
        super.onCleared()
        savePosition()
        player?.release()
        player = null
        // NOTE: temp files (only created for the external player) are wiped when the
        // vault locks (AppLockManager), so the external app has time to read them.
    }

    private fun subtitleMime(path: String): String = when {
        path.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
        path.endsWith(".ass", true) || path.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

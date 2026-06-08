package com.vaultgallery.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultgallery.app.media.ExternalPlayerLauncher
import kotlin.math.abs

/**
 * High-performance video player on Media3 ExoPlayer. The player itself (and its
 * streaming-decrypt media source) lives in [PlayerViewModel]; this screen renders it
 * and the gesture / control overlays:
 *  - left-half vertical drag = brightness, right-half = volume, horizontal = seek
 *  - 0.5x..3x speed, picture-in-picture, external subtitles, external-player handoff.
 */
@UnstableApi
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Hand off to an external player when requested (auto or via the button).
    LaunchedEffect(Unit) {
        viewModel.externalEventFlow.collect { event ->
            ExternalPlayerLauncher(context).play(event.uri, event.mimeType, event.packageName)
            if (event.closeAfter) onBack()
        }
    }

    // Save resume position on pause; release is handled in the ViewModel's onCleared.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.savePosition()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.savePosition()
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.setSubtitle(uri) }

    var speedMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        val player = viewModel.player
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        setShowSubtitleButton(true)
                    }
                },
                update = { it.player = player }
            )

            // Gesture layer: brightness (left), volume (right), horizontal = seek.
            GestureOverlay(
                onBrightnessDelta = { delta -> activity?.adjustBrightness(delta) },
                onVolumeDelta = { delta -> context.adjustVolume(delta) },
                onSeekDelta = { deltaMs ->
                    val target = (player.currentPosition + deltaMs)
                        .coerceIn(0, player.duration.coerceAtLeast(0))
                    player.seekTo(target)
                }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                }
                IconButton(onClick = { enterPip(activity) }) {
                    Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { speedMenu = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s}x") },
                                onClick = {
                                    viewModel.setPlaybackSpeed(s)
                                    speedMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.requestExternalPlay() }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open externally", tint = Color.White)
                }
            }
        } else if (state.loading || state.usingExternalPlayer) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else {
            Text(
                text = state.error ?: "Unable to play",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun GestureOverlay(
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onSeekDelta: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val width = size.width
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val normalized = -dragAmount / size.height
                    if (change.position.x < width / 2f) onBrightnessDelta(normalized)
                    else onVolumeDelta(normalized)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (abs(dragAmount) > 0) onSeekDelta((dragAmount * 200).toLong())
                }
            }
    )
}

private fun Activity.adjustBrightness(delta: Float) {
    val lp = window.attributes
    val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
    lp.screenBrightness = (current + delta).coerceIn(0.01f, 1f)
    window.attributes = lp
}

private fun Context.adjustVolume(delta: Float) {
    val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val target = (current + (delta * max)).toInt().coerceIn(0, max)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

private fun enterPip(activity: Activity?) {
    if (activity == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    ) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
}

package com.vaultgallery.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs

/**
 * High-performance video player built on Media3 ExoPlayer (hardware-accelerated by
 * default). Supports:
 *  - Gesture controls: left-half vertical = brightness, right-half vertical = volume,
 *    horizontal drag = seek.
 *  - Playback speed 0.5x..3x.
 *  - Picture-in-picture.
 *  - External subtitle files (SRT / ASS / VTT).
 *  - Resume from last position + position save.
 *  - One-tap handoff to an external player (XPlayer).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    // If configured to use an external player, hand off and close the built-in one.
    LaunchedEffect(Unit) {
        viewModel.externalEventFlow.collect { event ->
            val launcher = com.vaultgallery.app.media.ExternalPlayerLauncher(context)
            launcher.play(event.uri, event.mimeType, event.packageName)
            if (event.closeAfter) onBack()
        }
    }

    // Build / rebuild the media source when the decrypted file or subtitle changes.
    LaunchedEffect(state.localFile, state.subtitleUri) {
        val file = state.localFile ?: return@LaunchedEffect
        val builder = ExoMediaItem.Builder().setUri(Uri.fromFile(file))
        state.subtitleUri?.let { sub ->
            builder.setSubtitleConfigurations(
                listOf(
                    ExoMediaItem.SubtitleConfiguration.Builder(sub)
                        .setMimeType(subtitleMime(sub.toString()))
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        if (state.resumePositionMs > 0) exoPlayer.seekTo(state.resumePositionMs)
    }

    // Save position + release on dispose; pause/resume with lifecycle.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.savePosition(exoPlayer.currentPosition)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.savePosition(exoPlayer.currentPosition)
            exoPlayer.release()
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.setSubtitle(uri) }

    var speedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowSubtitleButton(true)
                }
            }
        )

        // Gesture layer: brightness (left), volume (right), seek (horizontal).
        GestureOverlay(
            onBrightnessDelta = { delta -> activity?.adjustBrightness(delta) },
            onVolumeDelta = { delta -> context.adjustVolume(delta) },
            onSeekDelta = { deltaMs ->
                val target = (exoPlayer.currentPosition + deltaMs)
                    .coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
                exoPlayer.seekTo(target)
            }
        )

        // Top-right action bar overlay.
        androidx.compose.foundation.layout.Row(
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
                                currentSpeed = s
                                exoPlayer.playbackParameters = PlaybackParameters(s)
                                speedMenu = false
                            }
                        )
                    }
                }
            }
            state.item?.let {
                IconButton(onClick = { viewModel.requestExternalPlay() }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open externally", tint = Color.White)
                }
            }
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
                    // Drag up should increase, so invert dragAmount.
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

private fun subtitleMime(path: String): String = when {
    path.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
    path.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
    path.endsWith(".ass", true) || path.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
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

package nl.streamfix.ui.screens.player

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val player = remember { ExoPlayer.Builder(context).build() }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    val tracks = rememberTracks(player)
    val cast = rememberCastController(player)

    // Markeer dat we in de speler zitten zodat MainActivity PiP kan starten.
    DisposableEffect(Unit) {
        PlayerActive.inPlayer = true
        onDispose {
            PlayerActive.inPlayer = false
            cast.release()
            player.release()
        }
    }

    // Auto-retry met exponentiele backoff (briefing: herstel binnen ~10s).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryAttempt = 0
                    showError = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showError = true
                val backoffMs =
                    (1000L shl retryAttempt.coerceAtMost(3)).coerceAtMost(8000L)
                retryAttempt++
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(backoffMs)
                    cast.retryLocal()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Wissel de bron als het kanaal verandert (vorige/volgende).
    LaunchedEffect(state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        retryJob?.cancel()
        retryAttempt = 0
        showError = false
        cast.load(
            url,
            state.title,
            0L,
            isLive = true,
            castUri = state.castStreamUrl ?: url,
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            leftSide = offset.x < size.width / 2f
                        },
                    ) { _, dragAmount ->
                        // Omhoog (negatief) = meer.
                        val step = -dragAmount / size.height
                        if (leftSide) adjustBrightness(activity, step)
                        else adjustVolume(context, step)
                    }
                }
                .pointerInput(Unit) {
                    var totalDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onDragEnd = {
                            val threshold = size.width / 4f
                            if (totalDx <= -threshold) viewModel.next()
                            else if (totalDx >= threshold) viewModel.previous()
                        },
                    ) { _, dragAmount -> totalDx += dragAmount }
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = cast.current },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Terug",
                    tint = Color.White,
                )
            }
            Text(
                text = state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (cast.castAvailable) {
                CastButton()
            }
            IconButton(onClick = { showTracks = true }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Audio en ondertitels",
                    tint = Color.White,
                )
            }
        }

        if (showError) {
            PlayerErrorOverlay(onRetry = {
                retryJob?.cancel()
                retryAttempt = 0
                cast.retryLocal()
                showError = false
            })
        }

        if (showTracks) {
            TrackSelectorDialog(
                player = player,
                tracks = tracks,
                onDismiss = { showTracks = false },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = viewModel::previous, enabled = state.hasPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Vorig kanaal",
                    tint = Color.White,
                )
            }
            IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Volgend kanaal",
                    tint = Color.White,
                )
            }
        }
    }
}

private fun adjustBrightness(activity: Activity?, step: Float) {
    activity ?: return
    val lp = activity.window.attributes
    val current = if (lp.screenBrightness in 0f..1f) lp.screenBrightness else 0.5f
    lp.screenBrightness = (current + step).coerceIn(0.05f, 1f)
    activity.window.attributes = lp
}

private fun adjustVolume(context: android.content.Context, step: Float) {
    val am = context.getSystemService(android.content.Context.AUDIO_SERVICE)
        as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val target = (cur + (step * max).toInt())
    if (abs(step) > 0.01f) {
        am.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            target.coerceIn(0, max),
            0,
        )
    }
}

/** Gedeelde vlag zodat MainActivity weet wanneer PiP zinvol is. */
object PlayerActive {
    @Volatile
    var inPlayer: Boolean = false
}

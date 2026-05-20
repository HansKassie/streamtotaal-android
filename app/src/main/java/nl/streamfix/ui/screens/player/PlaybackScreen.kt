package nl.streamfix.ui.screens.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import nl.streamfix.R
import nl.streamfix.ui.LocalIsTv
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PlaybackScreen(
    onBack: () -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var pendingResumeMs by remember { mutableStateOf<Long?>(null) }
    val tracks = rememberTracks(player)
    val cast = rememberCastController(player)

    DisposableEffect(Unit) {
        PlayerActive.inPlayer = true
        onDispose {
            PlayerActive.inPlayer = false
            viewModel.savePosition(cast.positionMs)
            cast.release()
            player.release()
        }
    }

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

    LaunchedEffect(state.ready, state.streamUrl) {
        if (!state.ready || state.streamUrl.isBlank()) return@LaunchedEffect
        retryJob?.cancel()
        retryAttempt = 0
        showError = false
        val resume = state.startPositionMs > 0L
        cast.load(state.streamUrl, state.title, 0L, autoPlay = !resume)
        if (resume) {
            // Eerst vragen: verder kijken of opnieuw.
            pendingResumeMs = state.startPositionMs
        }
    }

    // Positie periodiek bewaren zodat resume ook na een proceskill werkt.
    LaunchedEffect(state.streamUrl) {
        while (isActive) {
            delay(10_000)
            if (cast.current.isPlaying) viewModel.savePosition(cast.positionMs)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // Afstandsbediening: speler-view zelf focusbaar zodat de
                    // Media3-bediening (play/pauze/spoelen) met D-pad werkt.
                    isFocusable = true
                    if (isTv) {
                        setControllerAutoShow(false)
                        setControllerShowTimeoutMs(4000)
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { vis ->
                                chromeVisible = vis == android.view.View.VISIBLE
                            },
                        )
                    }
                    post { requestFocus() }
                }
            },
            update = { if (it.player !== cast.current) it.player = cast.current },
        )
        if (!isTv || chromeVisible) Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
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
                    contentDescription = stringResource(R.string.player_audio_subtitles),
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

        pendingResumeMs?.let { resumeMs ->
            ResumeDialog(
                positionMs = resumeMs,
                onResume = {
                    cast.seekToAndPlay(resumeMs)
                    pendingResumeMs = null
                },
                onRestart = {
                    cast.seekToAndPlay(0)
                    pendingResumeMs = null
                },
            )
        }
    }
}

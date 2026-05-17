package nl.streamfix.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun EpisodePlayerScreen(
    onBack: () -> Unit,
    viewModel: EpisodePlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val player = remember { ExoPlayer.Builder(context).build() }
    var countdown by remember { mutableStateOf<Int?>(null) }
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
                if (playbackState == Player.STATE_ENDED &&
                    viewModel.state.value.hasNext
                ) {
                    countdown = 10
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

    // Nieuwe aflevering: bron verversen + hervatten op opgeslagen positie.
    LaunchedEffect(state.mediaKey, state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        countdown = null
        retryJob?.cancel()
        retryAttempt = 0
        showError = false
        val resume = state.startPositionMs > 0L
        cast.load(url, state.title, 0L, autoPlay = !resume)
        if (resume) {
            pendingResumeMs = state.startPositionMs
        }
    }

    // Positie periodiek bewaren zodat resume ook na een proceskill werkt.
    LaunchedEffect(state.mediaKey) {
        while (isActive) {
            delay(10_000)
            if (cast.current.isPlaying) viewModel.savePosition(cast.positionMs)
        }
    }

    LaunchedEffect(countdown) {
        val c = countdown ?: return@LaunchedEffect
        if (c <= 0) {
            countdown = null
            viewModel.next()
            return@LaunchedEffect
        }
        delay(1000)
        countdown = c - 1
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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

        countdown?.let { c ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Volgende aflevering over $c s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { countdown = null }) {
                        Text("Annuleren")
                    }
                    Button(onClick = {
                        countdown = null
                        viewModel.next()
                    }) {
                        Text("Nu")
                    }
                }
            }
        }
    }
}

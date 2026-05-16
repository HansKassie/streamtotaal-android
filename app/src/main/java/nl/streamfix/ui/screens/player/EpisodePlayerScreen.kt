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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
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

    DisposableEffect(Unit) {
        PlayerActive.inPlayer = true
        onDispose {
            PlayerActive.inPlayer = false
            viewModel.savePosition(player.currentPosition)
            player.release()
        }
    }

    DisposableEffect(player) {
        var attempt = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) attempt = 0
                if (playbackState == Player.STATE_ENDED &&
                    viewModel.state.value.hasNext
                ) {
                    countdown = 10
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val backoffMs = (1000L shl attempt.coerceAtMost(3)).coerceAtMost(8000L)
                attempt++
                scope.launch {
                    delay(backoffMs)
                    player.prepare()
                    player.playWhenReady = true
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Nieuw kanaal/aflevering: bron verversen + hervatten op opgeslagen positie.
    LaunchedEffect(state.mediaKey, state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        countdown = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (state.startPositionMs > 0L) player.seekTo(state.startPositionMs)
        player.playWhenReady = true
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
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
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

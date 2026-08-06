package nl.streamfix.ui.screens.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import nl.streamfix.R
import nl.streamfix.ui.LocalIsTv
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
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

    val player = rememberStreamFixExoPlayer(
        preferDutchSubtitles = viewModel.prefersDutchSubtitles,
    )
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var pendingResumeMs by remember { mutableStateOf<Long?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var pausedForExit by remember { mutableStateOf(false) }
    var stoppedExplicitly by remember { mutableStateOf(false) }
    val tracks = rememberTracks(player)
    val cast = rememberCastController(player)
    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    // Zolang de knoppenbalk de focus heeft zijn links en rechts nodig om
    // tussen die knoppen te bewegen, en spoelt de speler dus niet.
    val topBarFocused = remember { mutableStateOf(false) }
    PauseLocalWhenBackgrounded(cast, isLive = false)

    // Overlays die in het spelervenster zelf liggen en dus met de
    // afstandsbediening bereikbaar moeten blijven.
    fun overlayVisible(): Boolean = state.sourceUnavailable || showError

    fun continuePlayback() {
        showExitConfirm = false
        if (pausedForExit) cast.resumeLocal(isLive = false)
        pausedForExit = false
    }

    fun stopAndLeave() {
        // Eerst bewaren, dan pas stoppen: na het stoppen geeft een
        // Chromecast geen bruikbare positie meer terug.
        viewModel.savePosition(cast.positionMs)
        stoppedExplicitly = true
        cast.stopPlayback()
        onBack()
    }

    fun requestExit() {
        if (state.sourceUnavailable || !viewModel.requiresExitConfirmation) {
            onBack()
            return
        }
        if (showExitConfirm) return
        pausedForExit = cast.pauseLocal()
        showExitConfirm = true
    }

    BackHandler(enabled = !showTracks && pendingResumeMs == null) {
        when {
            showExitConfirm -> continuePlayback()
            showError -> showError = false
            else -> requestExit()
        }
    }

    DisposableEffect(isTv) {
        PlayerActive.inPlayer = true
        if (isTv) {
            PlayerActive.onTvKeyEvent = { event ->
                // Foutoverlays liggen in hetzelfde venster als de speler, dus
                // de router moet ze met rust laten; anders slikt de speler de
                // OK-toets op en is de knop niet te bedienen. Dialoogvensters
                // hebben een eigen venster en komen hier sowieso niet langs.
                if (overlayVisible()) {
                    false
                } else {
                    handleTvPlaybackKey(
                        event = event,
                        topBarFocused = topBarFocused.value,
                        cast = cast,
                        playerView = playerView.value,
                    )
                }
            }
        }
        onDispose {
            PlayerActive.inPlayer = false
            PlayerActive.onTvKeyEvent = null
            // Bij "Stoppen" is de positie al bewaard voordat de ontvanger
            // stopte; nu nog eens opslaan zou daar een 0 overheen zetten.
            if (!stoppedExplicitly) viewModel.savePosition(cast.positionMs)
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
                    if (isTv) applyTvControls { chromeVisible = it }
                    post { requestFocus() }
                    playerView.value = this
                }
            },
            update = { if (it.player !== cast.current) it.player = cast.current },
        )
        // Blijft ook staan zolang hij de focus heeft, anders verdwijnt hij
        // onder je handen zodra de bediening na een paar seconden weggaat.
        if (!isTv || chromeVisible || topBarFocused.value) Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .focusGroup()
                .onFocusChanged { topBarFocused.value = it.hasFocus },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::requestExit) {
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

        if (state.sourceUnavailable) {
            // Geen actieve provider of onbekend brontype: melding in plaats
            // van een zwart scherm dat blijft bufferen. Opnieuw proberen
            // heeft hier geen zin, dus de knop gaat terug.
            PlayerErrorOverlay(
                messageRes = R.string.player_media_unavailable,
                actionRes = R.string.common_back,
                onRetry = onBack,
            )
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

        if (showExitConfirm) {
            ExitPlaybackDialog(
                onContinue = ::continuePlayback,
                onStop = ::stopAndLeave,
            )
        }
    }
}

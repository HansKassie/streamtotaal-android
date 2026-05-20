package nl.streamfix.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import nl.streamfix.R

/**
 * Gedeelde ExoPlayer-factory voor live/VOD/series-spelers met een ruimere
 * LoadControl. Helpt korte netwerk-/provider-dips opvangen voordat ze
 * zichtbaar worden als rebuffer-spinner. NIET bedoeld om structureel te
 * lage provider-throughput te maskeren.
 *
 * Afwegingen t.o.v. Media3-defaults (50s/50s/2.5s/5s):
 *  - minBuffer 30s laat zappen sneller herstarten met buffer.
 *  - maxBuffer 90s absorbeert dips; ~150-200 MB RAM-piek op HD-bitrates.
 *  - bufferForPlayback 2.5s houdt eerste-frame-snelheid identiek.
 *  - rebuffer-restart 7.5s geeft net iets meer vulling om niet meteen
 *    weer te haperen na een hiccup.
 *  - HTTP read-timeout 15s voorkomt dat trage IPTV-panels meteen falen.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberStreamFixExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    return remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 7_500,
            )
            .build()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setReadTimeoutMs(15_000)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}

/** ms naar "u:mm:ss" of "mm:ss". */
fun formatPosition(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

/** Houdt de actuele tracks bij zodat de keuzedialoog meebeweegt. */
@Composable
fun rememberTracks(player: Player): Tracks {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return tracks
}

private data class TrackOption(
    val label: String,
    val selected: Boolean,
    val apply: () -> Unit,
)

private fun Tracks.optionsFor(
    player: Player,
    trackType: Int,
    fallbackLabel: (Int) -> String,
): List<TrackOption> {
    val groups = groups.filter { it.type == trackType && it.isSupported }
    val options = mutableListOf<TrackOption>()
    groups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val name = format.label
                ?: format.language?.uppercase()
                ?: fallbackLabel(options.size + 1)
            options.add(
                TrackOption(
                    label = name,
                    selected = group.isTrackSelected(i),
                    apply = {
                        player.trackSelectionParameters =
                            player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(trackType, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        listOf(i),
                                    ),
                                )
                                .build()
                    },
                ),
            )
        }
    }
    return options
}

/** Audiospoor- en ondertitelkeuze op basis van de actuele tracks. */
@Composable
fun TrackSelectorDialog(
    player: Player,
    tracks: Tracks,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val audio = remember(tracks) {
        tracks.optionsFor(player, C.TRACK_TYPE_AUDIO) { i ->
            context.getString(R.string.player_track_label_fallback, i)
        }
    }
    val subs = remember(tracks) {
        tracks.optionsFor(player, C.TRACK_TYPE_TEXT) { i ->
            context.getString(R.string.player_track_label_fallback, i)
        }
    }
    val subsDisabled = player.trackSelectionParameters
        .disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_close))
            }
        },
        title = { Text(stringResource(R.string.player_audio_subtitles)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.player_audio_track),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (audio.isEmpty()) {
                    Text(
                        stringResource(R.string.player_no_choice_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                audio.forEach { opt ->
                    OptionRow(opt.label, opt.selected) {
                        opt.apply()
                        onDismiss()
                    }
                }

                Text(
                    stringResource(R.string.player_subtitles),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OptionRow(
                    stringResource(R.string.player_subtitles_off),
                    selected = subsDisabled || subs.none { it.selected },
                ) {
                    player.trackSelectionParameters =
                        player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    onDismiss()
                }
                subs.forEach { opt ->
                    OptionRow(opt.label, opt.selected && !subsDisabled) {
                        opt.apply()
                        onDismiss()
                    }
                }
            }
        },
    )
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Zichtbare foutmelding met handmatige retry over de speler heen. */
@Composable
fun PlayerErrorOverlay(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.player_connection_problem),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.player_retry_now))
            }
        }
    }
}

/** "Verder kijken vanaf X of opnieuw"-keuze voor films en series. */
@Composable
fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onRestart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text(stringResource(R.string.player_resume_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.player_resume_dialog_body,
                    formatPosition(positionMs),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(
                    stringResource(
                        R.string.player_resume_from,
                        formatPosition(positionMs),
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onRestart) {
                Text(stringResource(R.string.player_restart))
            }
        },
    )
}

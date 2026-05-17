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
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

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
): List<TrackOption> {
    val groups = groups.filter { it.type == trackType && it.isSupported }
    val options = mutableListOf<TrackOption>()
    groups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val name = format.label
                ?: format.language?.uppercase()
                ?: "Spoor ${options.size + 1}"
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
    val audio = remember(tracks) { tracks.optionsFor(player, C.TRACK_TYPE_AUDIO) }
    val subs = remember(tracks) { tracks.optionsFor(player, C.TRACK_TYPE_TEXT) }
    val subsDisabled = player.trackSelectionParameters
        .disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Sluiten") }
        },
        title = { Text("Audio en ondertitels") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Audiospoor",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (audio.isEmpty()) {
                    Text(
                        "Geen keuze beschikbaar",
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
                    "Ondertitels",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OptionRow("Uit", selected = subsDisabled || subs.none { it.selected }) {
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
                text = "Verbindingsprobleem. We proberen het automatisch " +
                    "opnieuw.",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onRetry) { Text("Nu opnieuw proberen") }
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
        title = { Text("Verder kijken?") },
        text = {
            Text("Je was gebleven bij ${formatPosition(positionMs)}.")
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("Verder vanaf ${formatPosition(positionMs)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onRestart) { Text("Vanaf begin") }
        },
    )
}

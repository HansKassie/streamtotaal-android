package nl.streamfix.ui.screens.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import nl.streamfix.ui.screens.player.mimeFor

/**
 * Klein, op zichzelf staand live-voorbeeld. Eigen lichte ExoPlayer (NIET via
 * CastController, zodat een cast-sessie niet in de war raakt). Niet-focusbaar
 * en zonder bediening; bedoeld om mee te lopen met de zenderlijst/gids.
 */
@Composable
fun LivePreviewPlayer(
    streamUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNullOrBlank()) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(mimeFor(streamUrl))
                    .build(),
            )
            player.prepare()
            player.playWhenReady = true
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                setUseController(false)
                isFocusable = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { if (it.player !== player) it.player = player },
    )
}

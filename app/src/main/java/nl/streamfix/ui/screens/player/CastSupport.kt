package nl.streamfix.ui.screens.player

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/** CastContext, of null als Google Play Services / Cast niet beschikbaar is. */
@Composable
fun rememberCastContext(): CastContext? {
    val context = LocalContext.current
    return remember {
        runCatching {
            CastContext.getSharedInstance(context.applicationContext)
        }.getOrNull()
    }
}

/** De officiele cast-knop (toont de apparaatkiezer). */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // MediaRouteButton vereist een AppCompat-thema met opake kleuren.
            // Het app-thema is android:Theme.Material, dus we wrappen met een
            // eigen AppCompat-Cast-thema (anders crasht MediaRouterThemeHelper
            // op een transparante colorPrimary).
            val themed = ContextThemeWrapper(
                ctx,
                nl.streamfix.R.style.Theme_StreamFix_Cast,
            )
            MediaRouteButton(themed).also { button ->
                CastButtonFactory.setUpMediaRouteButton(themed, button)
            }
        },
    )
}

internal fun mimeFor(url: String): String {
    val u = url.substringBefore('?').lowercase()
    return when {
        u.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
        u.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        u.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
        u.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
        u.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
        else -> MimeTypes.VIDEO_MP4
    }
}

/**
 * Houdt een lokale ExoPlayer en (indien beschikbaar) een CastPlayer bij en
 * wisselt automatisch zodra een cast-sessie start of stopt. [current] is de
 * speler die de PlayerView moet tonen.
 */
class CastController(
    private val exo: ExoPlayer,
    castContext: CastContext?,
) {
    private val castPlayer: CastPlayer? =
        castContext?.let { CastPlayer(it) }

    val castAvailable: Boolean get() = castPlayer != null

    var current: Player by mutableStateOf(exo)
        private set

    // Lokale (speler) en cast-variant kunnen verschillen: live cast verplicht
    // HLS, want Chromecast speelt geen rauwe .ts.
    private var localItem: MediaItem? = null
    private var castItem: MediaItem? = null
    private var lastIsLive = false

    private fun buildItem(uri: String, title: String) = MediaItem.Builder()
        .setUri(uri)
        .setMimeType(mimeFor(uri))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

    init {
        castPlayer?.setSessionAvailabilityListener(
            object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    val cp = castPlayer ?: return
                    val item = castItem ?: return
                    // Live: geen lokale positie meegeven, anders seekt de
                    // ontvanger naar een onzinnige plek en stopt direct.
                    val pos = if (lastIsLive) C.TIME_UNSET
                    else exo.currentPosition
                    exo.playWhenReady = false
                    current = cp
                    cp.setMediaItem(item, pos)
                    cp.playWhenReady = true
                }

                override fun onCastSessionUnavailable() {
                    val pos = if (lastIsLive) C.TIME_UNSET
                    else (castPlayer?.currentPosition ?: 0L)
                    current = exo
                    localItem?.let {
                        exo.setMediaItem(it, pos)
                        exo.prepare()
                        exo.playWhenReady = true
                    }
                }
            },
        )
    }

    fun load(
        uri: String,
        title: String,
        startMs: Long,
        autoPlay: Boolean = true,
        isLive: Boolean = false,
        castUri: String = uri,
    ) {
        localItem = buildItem(uri, title)
        castItem = buildItem(castUri, title)
        lastIsLive = isLive
        if (current === castPlayer && castPlayer != null) {
            val pos = if (isLive) C.TIME_UNSET else startMs
            castPlayer.setMediaItem(castItem!!, pos)
            castPlayer.prepare()
            castPlayer.playWhenReady = autoPlay
        } else {
            exo.setMediaItem(localItem!!, startMs)
            exo.prepare()
            exo.playWhenReady = autoPlay
        }
    }

    fun seekToAndPlay(ms: Long) {
        current.seekTo(ms)
        current.playWhenReady = true
    }

    /** Lokaal opnieuw proberen; tijdens casten regelt de ontvanger dit zelf. */
    fun retryLocal() {
        if (current === exo) {
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    val positionMs: Long get() = current.currentPosition

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
    }
}

@Composable
fun rememberCastController(exo: ExoPlayer): CastController {
    val castContext = rememberCastContext()
    return remember(exo, castContext) { CastController(exo, castContext) }
}

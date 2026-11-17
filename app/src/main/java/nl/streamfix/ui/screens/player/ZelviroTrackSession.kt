package nl.streamfix.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import nl.streamfix.data.local.LanguagePreferences
import java.util.Locale

private val languageCodes by lazy {
    buildMap {
        for (code in Locale.getISOLanguages()) {
            put(code, code)
            runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()?.let { put(it, code) }
        }
        putAll(mapOf("dut" to "nl", "ger" to "de", "fre" to "fr", "chi" to "zh",
            "cze" to "cs", "gre" to "el", "rum" to "ro", "iw" to "he", "in" to "id",
            "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "geo" to "ka",
            "ice" to "is", "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa",
            "slo" to "sk", "tib" to "bo", "wel" to "cy", "scc" to "sr", "scr" to "hr"))
    }
}

internal fun trackLanguage(tag: String?): String? =
    languageCodes[tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)]

internal fun preferredSubtitleLanguage(choice: String, app: String, selectedAudio: String?): String? =
    when (choice) {
        "off" -> null
        "auto" -> app.takeIf { trackLanguage(selectedAudio)?.let { it != app } == true }
        else -> trackLanguage(choice)
    }

internal fun preferredSubtitleIndex(choice: String, app: String, selectedAudio: String?, tracks: List<String?>): Int? {
    val target = preferredSubtitleLanguage(choice, app, selectedAudio) ?: return null
    return tracks.indexOfFirst { trackLanguage(it) == target }.takeIf { it >= 0 }
}

internal fun preferredAudioIndex(choice: String, languages: List<String?>, defaults: List<Boolean>): Int? {
    if (languages.isEmpty()) return null
    val preferred = trackLanguage(choice)?.let { code -> languages.indexOfFirst { trackLanguage(it) == code } } ?: -1
    return if (preferred >= 0) preferred else defaults.indexOfFirst { it }.coerceAtLeast(0)
}

internal class SessionTrackChoices<T> {
    var audio: T? = null
    var subtitles: T? = null
        private set
    var subtitlesOff = false
        private set

    fun selectSubtitles(selection: T?) {
        subtitles = selection
        subtitlesOff = selection == null
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ZelviroTrackSession(private val player: Player, private val languages: LanguagePreferences) : Player.Listener {
    companion object {
        val active = mutableMapOf<Player, ZelviroTrackSession>()
    }

    private data class Track(val group: Tracks.Group, val index: Int) {
        val format: Format get() = group.getTrackFormat(index)
        val selected get() = group.isTrackSelected(index)
        fun override() = TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
    }

    private data class Selection(val format: Format, val ordinal: Int) {
        fun find(tracks: List<Track>): Track? = tracks.filter { matches(it.format) }.getOrNull(ordinal)

        fun matches(other: Format): Boolean =
            trackLanguage(format.language) == trackLanguage(other.language) &&
                format.language.takeIf { trackLanguage(it) == null } == other.language.takeIf { trackLanguage(it) == null } &&
                format.label == other.label && format.roleFlags == other.roleFlags
    }

    private val manual = SessionTrackChoices<Selection>()

    fun attach() {
        active[player] = this
        player.addListener(this)
        apply(player.currentTracks)
    }

    fun detach() {
        player.removeListener(this)
        active.remove(player, this)
    }

    fun select(group: Tracks.Group, index: Int) {
        val chosen = Track(group, index)
        val selection = Selection(chosen.format, 0)
        val ordinal = player.currentTracks.supported(group.type)
            .filter { selection.matches(it.format) }.indexOf(chosen).coerceAtLeast(0)
        if (group.type == C.TRACK_TYPE_AUDIO) {
            manual.audio = selection.copy(ordinal = ordinal)
        } else {
            manual.selectSubtitles(selection.copy(ordinal = ordinal))
        }
    }

    fun disableSubtitles() {
        manual.selectSubtitles(null)
    }

    override fun onTracksChanged(tracks: Tracks) = apply(tracks)

    private fun apply(tracks: Tracks) {
        val audio = tracks.supported(C.TRACK_TYPE_AUDIO)
        val subtitles = tracks.supported(C.TRACK_TYPE_TEXT)
        val chosenAudio = manual.audio?.find(audio) ?: preferredAudioIndex(
            languages.audio, audio.map { it.format.language },
            audio.map { it.format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 },
        )?.let { audio[it] }
        val selected = audio.firstOrNull { it.selected }
        val chosenText = when {
            manual.subtitlesOff -> null
            manual.subtitles != null -> manual.subtitles?.find(subtitles)
            else -> preferredSubtitleIndex(
                languages.subtitles, languages.app, selected?.format?.language.takeIf { selected == chosenAudio },
                subtitles.map { it.format.language },
            )?.let { subtitles[it] }
        }
        val current = player.trackSelectionParameters
        val next = current.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, chosenText == null)
        chosenAudio?.let { next.setOverrideForType(it.override()) }
        chosenText?.let { next.setOverrideForType(it.override()) }
        val parameters = next.build()
        if (parameters != current) player.trackSelectionParameters = parameters
    }

    private fun Tracks.supported(type: Int): List<Track> = buildList {
        for (group in groups) {
            if (group.type != type) continue
            for (index in 0 until group.length) {
                if (group.isTrackSupported(index)) add(Track(group, index))
            }
        }
    }
}

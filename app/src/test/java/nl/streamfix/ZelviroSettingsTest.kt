package nl.streamfix

import nl.streamfix.data.local.CatalogSection
import nl.streamfix.data.local.appLanguage
import nl.streamfix.data.local.catalogKey
import nl.streamfix.data.local.catalogPreferenceKey
import nl.streamfix.data.local.hideCurrentCategories
import nl.streamfix.data.local.isCatalogItemVisible
import nl.streamfix.ui.screens.player.SessionTrackChoices
import nl.streamfix.ui.screens.player.preferredAudioIndex
import nl.streamfix.ui.screens.player.preferredSubtitleIndex
import nl.streamfix.ui.screens.player.preferredSubtitleLanguage
import org.junit.Assert.*
import org.junit.Test

class ZelviroSettingsTest {
    @Test
    fun localeUsesSupportedDeviceLanguageOrEnglish() {
        assertEquals("de", appLanguage("auto", "de"))
        assertEquals("nl", appLanguage("auto", "nl"))
        assertEquals("en", appLanguage("auto", "es"))
        assertEquals("de", appLanguage("de", "nl"))
    }

    @Test
    fun categoryStorageIsScopedAndBulkHideKeepsStaleIds() {
        val first = catalogPreferenceKey("playlist-1", CatalogSection.Live)
        val second = catalogPreferenceKey("playlist-2", CatalogSection.Live)
        val movies = catalogPreferenceKey("playlist-1", CatalogSection.Movies)
        val saved = mutableMapOf(first to setOf("stale"), second to setOf("other"), movies to setOf("film"))
        saved[first] = hideCurrentCategories(saved.getValue(first), listOf("news", "sport"))
        assertEquals(setOf("stale", "news", "sport"), saved[first])
        saved[first] = emptySet()
        assertTrue(saved.getValue(first).isEmpty())
        assertEquals(setOf("other"), saved[second])
        assertEquals(setOf("film"), saved[movies])
        assertEquals("HD", catalogKey(null, " HD "))
        assertEquals("42", catalogKey("42", "HD"))
        assertNull(catalogKey(null, " "))
    }

    @Test
    fun searchKeepsAnyVisibleMembershipAndIgnoresStaleHiddenIds() {
        val catalog = setOf("news", "sport")
        assertTrue(isCatalogItemVisible(setOf("news", "sport"), catalog, setOf("news")))
        assertFalse(isCatalogItemVisible(setOf("news"), catalog, setOf("news")))
        assertFalse(isCatalogItemVisible(setOf("missing"), catalog, setOf("news")))
        assertFalse(isCatalogItemVisible(emptySet(), catalog, setOf("news")))
        assertTrue(isCatalogItemVisible(emptySet(), catalog, setOf("stale")))
    }

    @Test
    fun audioUsesFirstMatchThenDefaultThenFirstAvailable() {
        val tracks = listOf("nld", "ger", "de-DE", "eng")
        val defaults = listOf(false, false, false, true)
        assertEquals(1, preferredAudioIndex("de", tracks, defaults))
        assertEquals(3, preferredAudioIndex("auto", tracks, defaults))
        assertEquals(3, preferredAudioIndex("fr", tracks, defaults))
        assertEquals(0, preferredAudioIndex("auto", tracks, List(4) { false }))
        assertNull(preferredAudioIndex("auto", emptyList(), emptyList()))
    }

    @Test
    fun automaticSubtitlesRequireKnownDifferentSelectedAudio() {
        assertEquals("nl", preferredSubtitleLanguage("auto", "nl", "eng"))
        assertNull(preferredSubtitleLanguage("auto", "nl", "dut"))
        assertNull(preferredSubtitleLanguage("auto", "nl", "und"))
        assertNull(preferredSubtitleLanguage("auto", "nl", null))
        assertEquals("nl", preferredSubtitleLanguage("nl", "nl", "nld"))
        assertNull(preferredSubtitleLanguage("off", "nl", "eng"))
        assertNull(preferredSubtitleIndex("auto", "nl", "eng", listOf("de", null)))
        assertNull(preferredSubtitleIndex("de", "nl", "eng", emptyList()))
        assertEquals(1, preferredSubtitleIndex("auto", "nl", "eng", listOf("de", "dut", "nld")))
    }

    @Test
    fun manualChangesPersistWithinSessionAndResetOnNewSession() {
        val playing = SessionTrackChoices<String>()
        playing.audio = "alternate track"
        playing.selectSubtitles(null)
        assertTrue(playing.subtitlesOff)
        assertEquals("alternate track", playing.audio)
        playing.selectSubtitles("Spanish")
        assertEquals("Spanish", playing.subtitles)
        assertFalse(playing.subtitlesOff)
        val next = SessionTrackChoices<String>()
        assertNull(next.audio)
        assertNull(next.subtitles)
        assertFalse(next.subtitlesOff)
    }
}

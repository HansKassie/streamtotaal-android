package nl.streamfix.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt de URL-opbouw, met name de hardening-fix dat een expliciet
 * ingevoerd https:// behouden blijft (geen stille downgrade naar http).
 */
class XtreamUrlsTest {

    @Test
    fun normalize_behoudtExplicietHttps() {
        assertEquals(
            "https://voorbeeld.nl",
            XtreamUrls.normalizeServerUrl("https://voorbeeld.nl"),
        )
    }

    @Test
    fun normalize_behoudtHttpsHoofdletterongevoelig() {
        assertEquals(
            "https://voorbeeld.nl",
            XtreamUrls.normalizeServerUrl("HTTPS://voorbeeld.nl"),
        )
    }

    @Test
    fun normalize_zonderSchemaWordtHttp() {
        assertEquals(
            "http://voorbeeld.nl",
            XtreamUrls.normalizeServerUrl("voorbeeld.nl"),
        )
    }

    @Test
    fun normalize_explicietHttpBlijftHttp() {
        assertEquals(
            "http://voorbeeld.nl",
            XtreamUrls.normalizeServerUrl("http://voorbeeld.nl"),
        )
    }

    @Test
    fun normalize_behoudtPoort() {
        assertEquals(
            "https://voorbeeld.nl:8080",
            XtreamUrls.normalizeServerUrl("https://voorbeeld.nl:8080"),
        )
    }

    @Test
    fun normalize_trimtSpatiesEnTrailingSlash() {
        assertEquals(
            "http://voorbeeld.nl",
            XtreamUrls.normalizeServerUrl("  voorbeeld.nl/  "),
        )
    }

    @Test
    fun normalize_onbekendSchemaValtTerugOpHttp() {
        assertEquals(
            "http://voorbeeld.nl",
            XtreamUrls.normalizeServerUrl("ftp://voorbeeld.nl"),
        )
    }

    @Test
    fun encode_vervangtSpatieDoorProcent20() {
        assertEquals("a%20b", XtreamUrls.encode("a b"))
    }

    @Test
    fun playerApi_bouwtUrlMetActionEnParams() {
        val url = XtreamUrls.playerApi(
            serverUrl = "https://voorbeeld.nl",
            username = "user one",
            password = "p@ss",
            action = "get_live_streams",
            params = mapOf("category_id" to "12"),
        )
        assertTrue(url.startsWith("https://voorbeeld.nl/player_api.php?"))
        assertTrue(url.contains("username=user%20one"))
        assertTrue(url.contains("&action=get_live_streams"))
        assertTrue(url.contains("&category_id=12"))
    }

    @Test
    fun liveStream_gebruiktGenormaliseerdeBasisUrl() {
        val url = XtreamUrls.liveStream(
            serverUrl = "voorbeeld.nl",
            username = "u",
            password = "p",
            streamId = "42",
        )
        assertEquals("http://voorbeeld.nl/live/u/p/42.ts", url)
    }
}

package nl.streamfix.data.remote

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gedeelde Xtream-URL-opbouw (player_api en stream-URLs). */
object XtreamUrls {

    /**
     * Behoudt een expliciet ingevoerd https://; anders default http://
     * (geen schema of http:// = http). Ingevoerde poort blijft behouden.
     */
    fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim()
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        val withoutScheme =
            trimmed.replace(Regex("(?i)^[a-z][a-z0-9+.-]*://"), "")
        val scheme = if (isHttps) "https://" else "http://"
        return scheme + withoutScheme.trimEnd('/')
    }

    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** player_api.php-URL met optionele action en extra query-params. */
    fun playerApi(
        serverUrl: String,
        username: String,
        password: String,
        action: String? = null,
        params: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(normalizeServerUrl(serverUrl))
        append("/player_api.php?username=")
        append(encode(username))
        append("&password=")
        append(encode(password))
        if (action != null) {
            append("&action=")
            append(action)
        }
        for ((key, value) in params) {
            append("&")
            append(key)
            append("=")
            append(encode(value))
        }
    }

    /** Live-stream-URL voor de speler (briefing 7.5). */
    fun liveStream(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String = "ts",
    ): String =
        "${normalizeServerUrl(serverUrl)}/live/" +
            "${encode(username)}/${encode(password)}/$streamId.$extension"

    /** VOD-stream-URL (briefing 7.5). */
    fun vodStream(
        serverUrl: String,
        username: String,
        password: String,
        vodId: String,
        extension: String,
    ): String =
        "${normalizeServerUrl(serverUrl)}/movie/" +
            "${encode(username)}/${encode(password)}/$vodId.$extension"

    /** Series-aflevering-stream-URL (briefing 7.5). */
    fun seriesStream(
        serverUrl: String,
        username: String,
        password: String,
        episodeId: String,
        extension: String,
    ): String =
        "${normalizeServerUrl(serverUrl)}/series/" +
            "${encode(username)}/${encode(password)}/$episodeId.$extension"

    /**
     * Catch-up/terugkijk-URL (timeshift). Meest gangbare Xtream-vorm:
     * /timeshift/user/pass/DUUR_MIN/yyyy-MM-dd:HH-mm/streamId.ext
     * Starttijd in apparaattijd; duur in minuten.
     */
    fun timeshift(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        startMs: Long,
        durationMin: Int,
        extension: String = "ts",
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        val start = fmt.format(Date(startMs))
        return "${normalizeServerUrl(serverUrl)}/timeshift/" +
            "${encode(username)}/${encode(password)}/" +
            "$durationMin/$start/$streamId.$extension"
    }
}

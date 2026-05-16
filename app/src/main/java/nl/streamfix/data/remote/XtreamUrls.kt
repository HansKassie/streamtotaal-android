package nl.streamfix.data.remote

import java.net.URLEncoder

/** Gedeelde Xtream-URL-opbouw (player_api en stream-URLs). */
object XtreamUrls {

    /**
     * Altijd http://; een ingevoerd schema wordt genegeerd. Een door de
     * klant ingevoerde poort blijft behouden (panels op :8080 e.d.).
     */
    fun normalizeServerUrl(input: String): String {
        val withoutScheme =
            input.trim().replace(Regex("(?i)^[a-z][a-z0-9+.-]*://"), "")
        return "http://" + withoutScheme.trimEnd('/')
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
}

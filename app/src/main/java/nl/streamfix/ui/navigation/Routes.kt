package nl.streamfix.ui.navigation

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN_XTREAM = "login/xtream"
    const val MAIN = "main"

    const val PLAYER = "player"
    const val PLAYER_ARG_CATEGORY = "cat"
    const val PLAYER_ARG_CHANNEL = "ch"
    const val PLAYER_ROUTE = "player?cat={cat}&ch={ch}"

    fun player(categoryId: String, channelId: String): String {
        val c = android.net.Uri.encode(categoryId)
        val ch = android.net.Uri.encode(channelId)
        return "player?cat=$c&ch=$ch"
    }

    const val VOD_ARG_ID = "id"
    const val VOD_DETAIL_ROUTE = "vod/{id}"

    fun vodDetail(vodId: String): String = "vod/${android.net.Uri.encode(vodId)}"

    const val PLAYBACK_ARG_URL = "u"
    const val PLAYBACK_ARG_TITLE = "t"
    const val PLAYBACK_ARG_MEDIA = "m"
    const val PLAYBACK_ROUTE = "playback?u={u}&t={t}&m={m}"

    fun playback(url: String, title: String, mediaId: String): String {
        val u = android.net.Uri.encode(url)
        val t = android.net.Uri.encode(title)
        val m = android.net.Uri.encode(mediaId)
        return "playback?u=$u&t=$t&m=$m"
    }
}

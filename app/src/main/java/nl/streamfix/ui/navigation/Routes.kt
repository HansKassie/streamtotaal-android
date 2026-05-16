package nl.streamfix.ui.navigation

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN_XTREAM = "login/xtream"
    const val MAIN = "main"

    const val PLAYER = "player"
    const val PLAYER_ARG_URL = "url"
    const val PLAYER_ARG_TITLE = "title"
    const val PLAYER_ROUTE = "player?url={url}&title={title}"

    fun player(url: String, title: String): String {
        val u = android.net.Uri.encode(url)
        val t = android.net.Uri.encode(title)
        return "player?url=$u&title=$t"
    }
}

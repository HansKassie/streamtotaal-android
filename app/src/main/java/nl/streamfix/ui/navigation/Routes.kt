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
}

package nl.streamfix.ui.navigation

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN_XTREAM = "login/xtream"
    const val MAIN = "main"

    const val SEARCH = "search"
    const val NOW_ON_TV = "nowontv"
    const val CONNECTION_TEST = "connectiontest"

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

    const val SERIES_ARG_ID = "id"
    const val SERIES_DETAIL_ROUTE = "series/{id}"

    fun seriesDetail(seriesId: String): String =
        "series/${android.net.Uri.encode(seriesId)}"

    const val EPISODE_ARG_SERIES = "s"
    const val EPISODE_ARG_SEASON = "se"
    const val EPISODE_ARG_EPISODE = "e"
    const val EPISODE_ROUTE = "episode?s={s}&se={se}&e={e}"

    fun episode(seriesId: String, season: Int, episodeId: String): String {
        val s = android.net.Uri.encode(seriesId)
        val e = android.net.Uri.encode(episodeId)
        return "episode?s=$s&se=$season&e=$e"
    }

    const val CHANNEL_EPG_ARG_ID = "id"
    const val CHANNEL_EPG_ARG_NAME = "name"
    const val CHANNEL_EPG_ROUTE = "channelEpg?id={id}&name={name}"

    fun channelEpg(channelId: String, channelName: String): String {
        val id = android.net.Uri.encode(channelId)
        val n = android.net.Uri.encode(channelName)
        return "channelEpg?id=$id&name=$n"
    }

    const val EPG_GUIDE_ARG_CAT = "cat"
    const val EPG_GUIDE_ROUTE = "epgGuide?cat={cat}"

    fun epgGuide(categoryId: String): String {
        val c = android.net.Uri.encode(categoryId)
        return "epgGuide?cat=$c"
    }

    const val CATCHUP_ARG_ID = "id"
    const val CATCHUP_ARG_NAME = "name"
    const val CATCHUP_ARG_DAYS = "days"
    const val CATCHUP_CHANNEL_ROUTE =
        "catchup?id={id}&name={name}&days={days}"

    fun catchupChannel(
        channelId: String,
        channelName: String,
        days: Int,
    ): String {
        val id = android.net.Uri.encode(channelId)
        val n = android.net.Uri.encode(channelName)
        return "catchup?id=$id&name=$n&days=$days"
    }
}

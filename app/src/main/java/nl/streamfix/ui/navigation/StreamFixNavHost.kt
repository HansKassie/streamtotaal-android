package nl.streamfix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.streamfix.ui.screens.catchup.CatchupChannelScreen
import nl.streamfix.ui.screens.connectiontest.ConnectionTestScreen
import nl.streamfix.ui.screens.nowontv.NowOnTvScreen
import nl.streamfix.ui.screens.search.SearchScreen
import nl.streamfix.ui.screens.login.XtreamLoginScreen
import nl.streamfix.ui.screens.main.MainScreen
import nl.streamfix.ui.screens.epg.ChannelEpgScreen
import nl.streamfix.ui.screens.epg.EpgGuideScreen
import nl.streamfix.ui.screens.player.EpisodePlayerScreen
import nl.streamfix.ui.screens.player.PlaybackScreen
import nl.streamfix.ui.screens.player.PlayerScreen
import nl.streamfix.ui.screens.series.SeriesDetailScreen
import nl.streamfix.ui.screens.vod.VodDetailScreen
import nl.streamfix.ui.screens.welcome.WelcomeScreen

@Composable
fun StreamFixNavHost(startLoggedIn: Boolean, deviceIsTv: Boolean) {
    val navController = rememberNavController()
    val start = if (startLoggedIn) Routes.MAIN else Routes.WELCOME

    NavHost(navController = navController, startDestination = start) {

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onChooseXtream = { navController.navigate(Routes.LOGIN_XTREAM) },
                onProviderChosen = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.LOGIN_XTREAM) {
            XtreamLoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    // Werkt zowel vanaf het welkomscherm als vanuit
                    // "Provider toevoegen": hele backstack leeg, MAIN als enige.
                    navController.navigate(Routes.MAIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onLoggedOut = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onAddProvider = { navController.navigate(Routes.LOGIN_XTREAM) },
                onOpenChannel = { categoryId, channelId ->
                    navController.navigate(Routes.player(categoryId, channelId))
                },
                onOpenChannelEpg = { channelId, channelName ->
                    navController.navigate(
                        Routes.channelEpg(channelId, channelName),
                    )
                },
                onOpenGuide = { categoryId ->
                    navController.navigate(Routes.epgGuide(categoryId))
                },
                onOpenVod = { vodId ->
                    navController.navigate(Routes.vodDetail(vodId))
                },
                onOpenSeries = { seriesId ->
                    navController.navigate(Routes.seriesDetail(seriesId))
                },
                onResumeMedia = { url, title, mediaId ->
                    navController.navigate(Routes.playback(url, title, mediaId))
                },
                onOpenCatchupChannel = { channelId, channelName, days ->
                    navController.navigate(
                        Routes.catchupChannel(channelId, channelName, days),
                    )
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenNowOnTv = { navController.navigate(Routes.NOW_ON_TV) },
                onOpenConnectionTest = {
                    navController.navigate(Routes.CONNECTION_TEST)
                },
                deviceIsTv = deviceIsTv,
            )
        }

        composable(Routes.NOW_ON_TV) {
            NowOnTvScreen(
                onBack = { navController.popBackStack() },
                onOpenChannel = { categoryId, channelId ->
                    navController.navigate(Routes.player(categoryId, channelId))
                },
            )
        }

        composable(Routes.CONNECTION_TEST) {
            ConnectionTestScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenChannel = { categoryId, channelId ->
                    navController.navigate(Routes.player(categoryId, channelId))
                },
                onOpenVod = { vodId ->
                    navController.navigate(Routes.vodDetail(vodId))
                },
                onOpenSeries = { seriesId ->
                    navController.navigate(Routes.seriesDetail(seriesId))
                },
            )
        }

        composable(
            route = Routes.PLAYER_ROUTE,
            arguments = listOf(
                navArgument(Routes.PLAYER_ARG_CATEGORY) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.PLAYER_ARG_CHANNEL) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onOpenGuide = { categoryId ->
                    navController.navigate(Routes.epgGuide(categoryId))
                },
            )
        }

        composable(
            route = Routes.VOD_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(Routes.VOD_ARG_ID) { type = NavType.StringType },
            ),
        ) {
            VodDetailScreen(
                onBack = { navController.popBackStack() },
                onPlay = { url, title, mediaId ->
                    navController.navigate(Routes.playback(url, title, mediaId))
                },
            )
        }

        composable(
            route = Routes.PLAYBACK_ROUTE,
            arguments = listOf(
                navArgument(Routes.PLAYBACK_ARG_URL) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.PLAYBACK_ARG_TITLE) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.PLAYBACK_ARG_MEDIA) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) {
            PlaybackScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.SERIES_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(Routes.SERIES_ARG_ID) { type = NavType.StringType },
            ),
        ) {
            SeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenEpisode = { seriesId, season, episodeId ->
                    navController.navigate(
                        Routes.episode(seriesId, season, episodeId),
                    )
                },
            )
        }

        composable(
            route = Routes.EPISODE_ROUTE,
            arguments = listOf(
                navArgument(Routes.EPISODE_ARG_SERIES) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.EPISODE_ARG_SEASON) {
                    type = NavType.StringType; defaultValue = "0"
                },
                navArgument(Routes.EPISODE_ARG_EPISODE) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) {
            EpisodePlayerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHANNEL_EPG_ROUTE,
            arguments = listOf(
                navArgument(Routes.CHANNEL_EPG_ARG_ID) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.CHANNEL_EPG_ARG_NAME) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) {
            ChannelEpgScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EPG_GUIDE_ROUTE,
            arguments = listOf(
                navArgument(Routes.EPG_GUIDE_ARG_CAT) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) {
            EpgGuideScreen(
                onBack = { navController.popBackStack() },
                onOpenChannel = { categoryId, channelId ->
                    navController.navigate(Routes.player(categoryId, channelId))
                },
            )
        }

        composable(
            route = Routes.CATCHUP_CHANNEL_ROUTE,
            arguments = listOf(
                navArgument(Routes.CATCHUP_ARG_ID) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.CATCHUP_ARG_NAME) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(Routes.CATCHUP_ARG_DAYS) {
                    type = NavType.StringType; defaultValue = "7"
                },
            ),
        ) {
            CatchupChannelScreen(
                onBack = { navController.popBackStack() },
                onPlay = { url, title, mediaId ->
                    navController.navigate(
                        Routes.playback(url, title, mediaId),
                    )
                },
            )
        }
    }
}

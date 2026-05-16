package nl.streamfix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nl.streamfix.ui.screens.login.XtreamLoginScreen
import nl.streamfix.ui.screens.main.MainScreen
import nl.streamfix.ui.screens.welcome.WelcomeScreen

@Composable
fun StreamFixNavHost(startLoggedIn: Boolean) {
    val navController = rememberNavController()
    val start = if (startLoggedIn) Routes.MAIN else Routes.WELCOME

    NavHost(navController = navController, startDestination = start) {

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onChooseXtream = { navController.navigate(Routes.LOGIN_XTREAM) },
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
            )
        }
    }
}

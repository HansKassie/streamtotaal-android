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
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onLoggedOut = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}

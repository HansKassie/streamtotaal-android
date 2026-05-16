package nl.streamfix

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import nl.streamfix.ui.RootState
import nl.streamfix.ui.RootViewModel
import nl.streamfix.ui.navigation.StreamFixNavHost
import nl.streamfix.ui.screens.player.PlayerActive
import nl.streamfix.ui.theme.StreamFixTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            rootViewModel.state.value == RootState.Loading
        }
        enableEdgeToEdge()
        setContent {
            StreamFixTheme {
                val state by rootViewModel.state.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    when (state) {
                        RootState.Loading -> Unit // splash blijft staan
                        RootState.LoggedIn -> StreamFixNavHost(startLoggedIn = true)
                        RootState.LoggedOut -> StreamFixNavHost(startLoggedIn = false)
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            PlayerActive.inPlayer &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }
}

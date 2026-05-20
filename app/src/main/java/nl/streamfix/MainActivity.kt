package nl.streamfix

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import nl.streamfix.ui.LocalIsTv
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import nl.streamfix.ui.RootState
import nl.streamfix.ui.RootViewModel
import nl.streamfix.ui.StartupLoadingScreen
import nl.streamfix.ui.navigation.StreamFixNavHost
import nl.streamfix.ui.screens.player.PlayerActive
import nl.streamfix.ui.theme.StreamFixTheme
import nl.streamfix.ui.update.UpdaterGate

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFixTheme {
                val state by rootViewModel.state.collectAsStateWithLifecycle()
                val deviceIsTv = remember { DeviceMode.isTelevision(this) }
                CompositionLocalProvider(LocalIsTv provides deviceIsTv) {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    when (state) {
                        RootState.Loading -> StartupLoadingScreen()
                        RootState.LoggedIn -> StreamFixNavHost(
                            startLoggedIn = true,
                            deviceIsTv = deviceIsTv,
                        )
                        RootState.LoggedOut -> StreamFixNavHost(
                            startLoggedIn = false,
                            deviceIsTv = deviceIsTv,
                        )
                    }

                    if (state != RootState.Loading) UpdaterGate()
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

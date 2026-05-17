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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import nl.streamfix.ui.RootState
import nl.streamfix.ui.RootViewModel
import nl.streamfix.ui.navigation.StreamFixNavHost
import nl.streamfix.ui.screens.player.PlayerActive
import nl.streamfix.ui.theme.StreamFixTheme
import nl.streamfix.ui.update.UpdateDialog
import nl.streamfix.ui.update.UpdateViewModel

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

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

                    if (state != RootState.Loading) {
                        val updateVm: UpdateViewModel = hiltViewModel()
                        val update by updateVm.update.collectAsStateWithLifecycle()
                        val dismissed by updateVm.dismissed
                            .collectAsStateWithLifecycle()
                        update?.let {
                            if (!dismissed) {
                                UpdateDialog(
                                    update = it,
                                    onDismiss = updateVm::dismiss,
                                )
                            }
                        }
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

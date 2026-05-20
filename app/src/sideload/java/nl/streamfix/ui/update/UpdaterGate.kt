package nl.streamfix.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sideload-flavor: kijkt of er een update beschikbaar is en toont
 * de update-dialoog. MainActivity hoeft niet te weten dat de updater
 * bestaat; in de Play Store-flavor is dit een no-op-composable.
 */
@Composable
fun UpdaterGate() {
    val updateVm: UpdateViewModel = hiltViewModel()
    val update by updateVm.update.collectAsStateWithLifecycle()
    val dismissed by updateVm.dismissed.collectAsStateWithLifecycle()
    update?.let {
        if (!dismissed) {
            UpdateDialog(update = it, onDismiss = updateVm::dismiss)
        }
    }
}

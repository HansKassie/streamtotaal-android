package nl.streamfix.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import nl.streamfix.domain.model.UpdateInfo

private enum class Phase { Idle, Downloading, Failed }

@Composable
fun UpdateDialog(
    update: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(Phase.Idle) }

    val body = when (phase) {
        Phase.Idle -> update.releaseNotes
        Phase.Downloading -> "Bezig met downloaden van de update..."
        Phase.Failed ->
            "De update is mislukt. Controleer je internetverbinding " +
                "en probeer het opnieuw."
    }

    fun start() {
        phase = Phase.Downloading
        AppUpdater.downloadAndInstall(context, update.apkUrl) { ok ->
            if (ok) onDismiss() else phase = Phase.Failed
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!update.mandatory && phase != Phase.Downloading) onDismiss()
        },
        title = { Text("Update beschikbaar (${update.versionName})") },
        text = { Text(body) },
        confirmButton = {
            when (phase) {
                Phase.Downloading -> {}
                Phase.Failed -> TextButton(onClick = { start() }) {
                    Text("Opnieuw proberen")
                }
                Phase.Idle -> TextButton(onClick = { start() }) {
                    Text("Nu updaten")
                }
            }
        },
        dismissButton = if (update.mandatory || phase == Phase.Downloading) {
            null
        } else {
            { TextButton(onClick = onDismiss) { Text("Later") } }
        },
    )
}

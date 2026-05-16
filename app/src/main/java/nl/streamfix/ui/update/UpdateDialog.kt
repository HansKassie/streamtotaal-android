package nl.streamfix.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import nl.streamfix.domain.model.UpdateInfo

@Composable
fun UpdateDialog(
    update: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!update.mandatory) onDismiss() },
        title = { Text("Update beschikbaar (${update.versionName})") },
        text = { Text(update.releaseNotes) },
        confirmButton = {
            TextButton(onClick = {
                AppUpdater.downloadAndInstall(context, update.apkUrl)
                onDismiss()
            }) {
                Text("Nu updaten")
            }
        },
        dismissButton = if (update.mandatory) {
            null
        } else {
            { TextButton(onClick = onDismiss) { Text("Later") } }
        },
    )
}

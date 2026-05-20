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
import androidx.compose.ui.res.stringResource
import nl.streamfix.R
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
        Phase.Downloading -> stringResource(R.string.update_downloading)
        Phase.Failed -> stringResource(R.string.update_failed)
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
        title = {
            Text(
                stringResource(R.string.update_dialog_title_format, update.versionName),
            )
        },
        text = { Text(body) },
        confirmButton = {
            when (phase) {
                Phase.Downloading -> {}
                Phase.Failed -> TextButton(onClick = { start() }) {
                    Text(stringResource(R.string.update_retry))
                }
                Phase.Idle -> TextButton(onClick = { start() }) {
                    Text(stringResource(R.string.update_now))
                }
            }
        },
        dismissButton = if (update.mandatory || phase == Phase.Downloading) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        },
    )
}

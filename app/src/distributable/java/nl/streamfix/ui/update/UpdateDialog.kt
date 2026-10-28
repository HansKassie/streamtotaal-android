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
import nl.streamfix.BuildConfig
import nl.streamfix.R
import nl.streamfix.domain.model.UpdateInfo

private enum class Phase {
    Idle,
    Downloading,
    DownloadFailed,
    IntegrityFailed,
    PermissionRequired,
    InstallerFailed,
}

@Composable
fun UpdateDialog(
    update: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(Phase.Idle) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var diagnostic by remember { mutableStateOf<String?>(null) }

    val message = when (phase) {
        Phase.Idle -> update.releaseNotes
        Phase.Downloading -> downloadProgress?.let {
            stringResource(R.string.update_downloading_progress, it)
        } ?: stringResource(R.string.update_downloading)
        Phase.DownloadFailed -> stringResource(R.string.update_failed_download)
        Phase.IntegrityFailed -> stringResource(R.string.update_failed_integrity)
        Phase.PermissionRequired -> stringResource(R.string.update_permission_required)
        Phase.InstallerFailed -> stringResource(R.string.update_failed_installer)
    }
    val body = diagnostic?.let {
        "$message\n\n${stringResource(R.string.update_diagnostic, BuildConfig.VERSION_NAME, it)}"
    } ?: message

    fun start() {
        phase = Phase.Downloading
        downloadProgress = null
        diagnostic = null
        AppUpdater.downloadAndInstall(
            context = context,
            apkUrl = update.apkUrl,
            expectedSha256 = update.sha256,
            onProgress = { downloadProgress = it },
        ) { outcome ->
            diagnostic = outcome.diagnostic
            phase = when (outcome.result) {
                UpdateResult.DownloadFailed -> Phase.DownloadFailed
                UpdateResult.IntegrityFailed -> Phase.IntegrityFailed
                UpdateResult.PermissionRequired -> Phase.PermissionRequired
                UpdateResult.InstallerFailed -> Phase.InstallerFailed
                UpdateResult.InstallerOpened -> {
                    if (update.mandatory) Phase.Idle else {
                        onDismiss()
                        return@downloadAndInstall
                    }
                }
            }
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
                Phase.DownloadFailed,
                Phase.IntegrityFailed,
                Phase.InstallerFailed -> TextButton(onClick = { start() }) {
                    Text(stringResource(R.string.update_retry))
                }
                Phase.PermissionRequired -> TextButton(onClick = { start() }) {
                    Text(stringResource(R.string.update_open_permission))
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

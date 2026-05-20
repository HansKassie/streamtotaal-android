package nl.streamfix.ui.update

import androidx.compose.runtime.Composable

/**
 * Play Store-flavor: zelf-updaten is verboden door Google Play-beleid,
 * dus de gate is bewust een no-op. MainActivity roept dezelfde naam aan
 * en is daardoor flavor-onafhankelijk.
 */
@Composable
fun UpdaterGate() = Unit

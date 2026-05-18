package nl.streamfix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/** App-breed: draait de UI in tv-modus (afstandsbediening, groot scherm). */
val LocalIsTv = compositionLocalOf { false }

/**
 * Laat een tekstveld de focus loslaten met de D-pad. Zonder dit blijft de
 * afstandsbediening "hangen" in het veld (het tv-toetsenbord opent niet
 * vanzelf, dus de IME-actie is onbereikbaar). Omhoog/omlaag verplaatst de
 * focus naar het vorige/volgende element.
 */
fun Modifier.dpadExitField(focusManager: FocusManager): Modifier =
    onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionDown -> {
                focusManager.moveFocus(FocusDirection.Down); true
            }
            Key.DirectionUp -> {
                focusManager.moveFocus(FocusDirection.Up); true
            }
            else -> false
        }
    }

/**
 * Zichtbare focus-rand voor afstandsbediening (D-pad). Plaats VOOR
 * `clickable`, zodat dezelfde focus-node wordt waargenomen. Op aanraken
 * (telefoon) gebeurt er niets, want dan is er geen focus.
 */
fun Modifier.tvFocusable(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier.border(2.dp, color, RoundedCornerShape(6.dp))
            } else {
                Modifier
            },
        )
}

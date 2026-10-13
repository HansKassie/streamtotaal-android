package nl.streamfix.ui.screens.player

/**
 * True voor de OK-toets van de afstandsbediening.
 *
 * Remotes sturen hier niet allemaal dezelfde code voor: de meeste d-pads
 * geven DPAD_CENTER, maar toetsenborden en sommige settopbox-remotes geven
 * ENTER of NUMPAD_ENTER. Alle drie moeten hetzelfde doen, anders werkt OK
 * op het ene apparaat en op het andere niet.
 *
 * Losse functie zonder Compose of Media3 eromheen, zodat de verzameling op
 * de JVM te testen is en er maar een plek is waar hij staat.
 */
fun isOkKeyCode(keyCode: Int): Boolean =
    keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER

package nl.streamfix.ui.screens.player

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt dat OK op alle remotes dezelfde toets is. Herkent de app er een
 * niet, dan opent het zenderoverzicht op het ene apparaat wel en op het
 * andere niet, en dat is op afstand niet te verklaren.
 */
class TvKeysTest {

    @Test
    fun dpadCenterIsOk() {
        assertTrue(isOkKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun enterIsOk() {
        // Toetsenborden en sommige settopbox-remotes sturen dit.
        assertTrue(isOkKeyCode(KeyEvent.KEYCODE_ENTER))
        assertTrue(isOkKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    @Test
    fun zappenEnTerugZijnGeenOk() {
        // Deze toetsen hebben in de live-speler hun eigen taak; zou OK ze
        // opslokken, dan werkt zappen niet meer.
        assertFalse(isOkKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(isOkKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(isOkKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(isOkKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertFalse(isOkKeyCode(KeyEvent.KEYCODE_BACK))
        assertFalse(isOkKeyCode(KeyEvent.KEYCODE_LAST_CHANNEL))
    }
}

package nl.streamfix.ui.screens.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt dat het Server-URL-veld bij "Eigen provider" weigert bij alleen
 * een scheme of leeg/whitespace, en accepteert zodra er een host is.
 * Zo blokkeert de Inloggen-knop niet stilletjes op een onbruikbare URL.
 */
class XtreamLoginUrlValidationTest {

    @Test
    fun lege_input_wordt_afgekeurd() {
        assertFalse(isValidServerUrl(""))
        assertFalse(isValidServerUrl("   "))
    }

    @Test
    fun alleen_scheme_wordt_afgekeurd() {
        assertFalse(isValidServerUrl("http://"))
        assertFalse(isValidServerUrl("https://"))
        assertFalse(isValidServerUrl("HTTP://"))
    }

    @Test
    fun scheme_met_host_wordt_geaccepteerd() {
        assertTrue(isValidServerUrl("http://voorbeeld.nl"))
        assertTrue(isValidServerUrl("https://voorbeeld.nl:8080"))
    }

    @Test
    fun host_zonder_scheme_wordt_geaccepteerd() {
        // normalizeServerUrl plakt later http:// ervoor.
        assertTrue(isValidServerUrl("voorbeeld.nl"))
    }

    @Test
    fun alleen_slash_na_scheme_wordt_afgekeurd() {
        assertFalse(isValidServerUrl("http:///"))
        assertFalse(isValidServerUrl("http://   "))
    }
}

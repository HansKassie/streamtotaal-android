package nl.streamfix.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bewust met verzonnen adressen: de echte koppeling staat per flavor in
 * ProviderCatalog, zodat de Play Store-build geen resellergegevens bevat.
 */
class NetworkNamesTest {

    private val byHost = mapOf(
        "line.voorbeeld.nl" to "Netwerk A",
        "oud.voorbeeld.nl" to "Netwerk A",
        "ander.voorbeeld.nl" to "Netwerk B",
    )

    @Test
    fun bekendAdresGeeftDeNetwerknaam() {
        assertEquals(
            "Netwerk A",
            NetworkNames.forServerUrl("http://line.voorbeeld.nl", byHost),
        )
    }

    @Test
    fun vervallenAdresGeeftDezelfdeLetter() {
        // Een account dat nog op het oude adres staat hoort niet opeens een
        // andere letter te krijgen.
        assertEquals(
            "Netwerk A",
            NetworkNames.forServerUrl("http://oud.voorbeeld.nl", byHost),
        )
    }

    @Test
    fun schemaPoortPadEnHoofdlettersDoenNietMee() {
        for (url in listOf(
            "https://LINE.Voorbeeld.NL",
            "http://line.voorbeeld.nl:8080",
            "http://line.voorbeeld.nl/",
            "  http://line.voorbeeld.nl  ",
            "line.voorbeeld.nl",
        )) {
            assertEquals(url, "Netwerk A", NetworkNames.forServerUrl(url, byHost))
        }
    }

    @Test
    fun onbekendAdresBlijftOngemoeid() {
        assertNull(NetworkNames.forServerUrl("http://eigen.server.nl", byHost))
    }

    @Test
    fun zonderKoppelingGebeurtErNiets() {
        // Play Store-flavor: lege tabel, dus nooit hernoemen.
        assertNull(NetworkNames.forServerUrl("http://line.voorbeeld.nl", emptyMap()))
    }
}

package nl.streamfix.ui

import nl.streamfix.domain.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Borgt de volgorde laatst bekeken, laatst gefocust, begin van de lijst.
 * Zonder dit sprong de zenderlijst na het sluiten van de speler terug naar
 * de eerste zender.
 */
class FocusTargetTest {

    private val channels = listOf(
        LiveChannel("c1", "Een", null, "cat1", null),
        LiveChannel("c2", "Twee", null, "cat1", null),
        LiveChannel("c3", "Drie", null, "cat1", null),
    )

    @Test
    fun laatstBekekenHeeftVoorrangOpLaatstGefocust() {
        // Wie in de speler doorzapt van c2 naar c3, keert terug bij c3.
        val target = pickFocusTarget(channels, lastWatchedId = "c3", lastFocusedId = "c2")
        assertEquals("c3", target)
    }

    @Test
    fun zonderBekekenZenderTeltDeLaatsteFocus() {
        val target = pickFocusTarget(channels, lastWatchedId = null, lastFocusedId = "c2")
        assertEquals("c2", target)
    }

    @Test
    fun onbekendeBekekenZenderValtTerugOpFocus() {
        // Bekeken in een andere categorie, of weggefilterd door het slot.
        val target = pickFocusTarget(channels, lastWatchedId = "weg", lastFocusedId = "c2")
        assertEquals("c2", target)
    }

    @Test
    fun zonderKandidatenBegintDeLijstBovenaan() {
        val target = pickFocusTarget(channels, lastWatchedId = null, lastFocusedId = null)
        assertEquals("c1", target)
    }

    @Test
    fun beideOnbekendGeeftEersteZender() {
        val target = pickFocusTarget(channels, lastWatchedId = "x", lastFocusedId = "y")
        assertEquals("c1", target)
    }

    @Test
    fun legeLijstGeeftGeenDoel() {
        assertNull(pickFocusTarget(emptyList(), lastWatchedId = "c1", lastFocusedId = "c2"))
    }
}

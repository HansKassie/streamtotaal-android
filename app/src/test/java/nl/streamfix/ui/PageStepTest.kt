package nl.streamfix.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Een sprong van een schermvol in het zenderoverzicht. Op het toestel zijn
 * deze randgevallen lastig te raken: je moet er net een lijst voor hebben
 * die korter is dan een pagina, of precies op de laatste zender staan.
 */
class PageStepTest {

    @Test
    fun gewoonVooruit() {
        assertEquals(10, pickPageTarget(0, 100, 10, forward = true))
        assertEquals(25, pickPageTarget(15, 100, 10, forward = true))
    }

    @Test
    fun gewoonAchteruit() {
        assertEquals(5, pickPageTarget(15, 100, 10, forward = false))
    }

    @Test
    fun voorbijHetEindeLandtOpDeLaatste() {
        // Niet nergens heen, en ook niet niets doen: de kijker verwacht
        // dat nog een druk hem onderaan brengt.
        assertEquals(99, pickPageTarget(95, 100, 10, forward = true))
    }

    @Test
    fun voorHetBeginLandtOpDeEerste() {
        assertEquals(0, pickPageTarget(4, 100, 10, forward = false))
    }

    @Test
    fun opDeLaatsteNogEensVooruitDoetNiets() {
        // Null en niet 99: anders wordt de focus opnieuw aangevraagd voor
        // een rij die hem al heeft.
        assertNull(pickPageTarget(99, 100, 10, forward = true))
    }

    @Test
    fun opDeEersteNogEensAchteruitDoetNiets() {
        assertNull(pickPageTarget(0, 100, 10, forward = false))
    }

    @Test
    fun eenLijstKorterDanEenPagina() {
        assertEquals(3, pickPageTarget(0, 4, 10, forward = true))
        assertEquals(0, pickPageTarget(3, 4, 10, forward = false))
    }

    @Test
    fun eenLijstVanEen() {
        assertNull(pickPageTarget(0, 1, 10, forward = true))
        assertNull(pickPageTarget(0, 1, 10, forward = false))
    }

    @Test
    fun eenLegeLijst() {
        assertNull(pickPageTarget(0, 0, 10, forward = true))
    }

    @Test
    fun eenStapVanNulOfMinderDoetNiets() {
        // Kan voorkomen als er even geen enkele rij zichtbaar is gemeten.
        assertNull(pickPageTarget(5, 100, 0, forward = true))
        assertNull(pickPageTarget(5, 100, -3, forward = true))
    }

    @Test
    fun eenOnbekendeZenderBegintBovenaan() {
        // Het echte geval: indexOfFirst geeft -1 als de zender met de focus
        // niet (meer) in de lijst staat. Dan hoort de sprong vanaf de eerste
        // rij te rekenen en niet vanaf een negatieve index.
        assertEquals(10, pickPageTarget(-1, 100, 10, forward = true))
        assertNull(pickPageTarget(-1, 100, 10, forward = false))
    }

    @Test
    fun eenIndexVoorbijDeLijstWordtEerstBinnengehaald() {
        // Hypothetisch, maar het mag geen sprong naar een onmogelijke rij
        // opleveren. Binnengehaald wordt dit de laatste rij, en vooruit
        // springen vanaf de laatste doet niets.
        assertNull(pickPageTarget(500, 100, 10, forward = true))
        assertEquals(89, pickPageTarget(500, 100, 10, forward = false))
    }

    @Test
    fun eenNieuweBestemmingVraagtDeFocusAltijdAan() {
        assertTrue(pageJumpNeedsRequest("b", "a", isRepeat = false))
        // Ook bij een ingedrukte toets: dan is de vorige sprong al geland,
        // anders had de herhaling dezelfde bestemming berekend.
        assertTrue(pageJumpNeedsRequest("b", "a", isRepeat = true))
    }

    @Test
    fun eenEersteSprongVraagtDeFocusAan() {
        // Ook als de bestemming de spelende zender is en focusRowId dus niet
        // verandert: drie rijen omlaag en dan links, terug naar de eerste.
        assertTrue(pageJumpNeedsRequest("a", null, isRepeat = false))
        assertTrue(pageJumpNeedsRequest("a", null, isRepeat = true))
    }

    @Test
    fun eenNieuweDrukNaarDezelfdeBestemmingVraagtDeFocusOpnieuwAan() {
        // Naar de laatste gesprongen, een rij omhoog, en weer rechts. De
        // bestemming is gelijk, maar de focus staat er niet meer.
        assertTrue(pageJumpNeedsRequest("a", "a", isRepeat = false))
    }

    @Test
    fun eenHerhalingNaarDezelfdeBestemmingLaatDeLopendeAanvraagMetRust() {
        // Toets ingedrukt en de focus nog onderweg: opnieuw aanvragen breekt
        // de lopende aanvraag af.
        assertFalse(pageJumpNeedsRequest("a", "a", isRepeat = true))
    }
}

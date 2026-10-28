package nl.streamfix.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt wanneer het zenderoverzicht een gids wel en niet opnieuw opvraagt.
 * Op het toestel is dat niet te beoordelen: je ziet alleen een lege regel en
 * niet of er geen gids is, of dat er eerder iets misging.
 */
class EpgRequestGateTest {

    private val minuut = 60_000L

    @Test
    fun eenOnbekendeZenderMagOpgevraagdWorden() {
        val gate = EpgRequestGate()
        assertTrue(gate.claim("1", 0L))
    }

    @Test
    fun tijdensEenLopendeAanvraagNietNogEens() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        // Dezelfde rij die opnieuw in beeld scrolt mag de aanvraag niet
        // verdubbelen zolang de eerste nog onderweg is.
        assertFalse(gate.claim("1", 0L))
    }

    @Test
    fun naEenAntwoordNietMeteenOpnieuw() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertFalse(gate.claim("1", 10 * minuut))
    }

    @Test
    fun eenLeegAntwoordTeltOokAlsAntwoord() {
        // De poort weet niet of de lijst leeg was; de aanroeper meldt beide
        // als klaar. Leeg betekent "deze zender heeft geen gids", en bij
        // een provider zonder EPG zou de andere keuze elke scrollbeweging
        // in aanvragen laten ontaarden.
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertFalse(gate.claim("1", 60 * minuut))
    }

    @Test
    fun naDeCacheTtlMagEenAntwoordWelOpnieuw() {
        // "Klaar" is niet voor altijd. Een provider kan de gids later
        // alsnog aanleveren, en dit scherm heeft geen verversknop; zonder
        // deze grens blijft zo'n zender leeg zolang het scherm openstaat.
        // De grens ligt op de TTL van de EPG-cache: eerder vragen levert
        // toch hetzelfde gecachte antwoord op.
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertTrue(gate.claim("1", EpgRequestGate.STALE_AFTER_MS))
    }

    @Test
    fun eenVerlopenGidsMagNaDeKorteWachttijdOpnieuw() {
        // Niet de TTL maar de korte wachttijd, want een gids die helemaal
        // in het verleden ligt zegt niets meer over nu. Anders blijft een
        // rij uren op afgelopen titels staan.
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertTrue(gate.claim("1", minuut, exhausted = true))
    }

    @Test
    fun eenVerlopenGidsNogNietBinnenDeKorteWachttijd() {
        // De rij vraagt het elke 30 seconden opnieuw zolang hij in beeld
        // staat. Zonder deze wachttijd zou dat voor een zender waarvan de
        // gids echt op is een aanvraag per halve minuut betekenen.
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertFalse(gate.claim("1", minuut - 1, exhausted = true))
    }

    @Test
    fun zonderVerlopenGidsGeldtDeLangeGrens() {
        // Hetzelfde moment, maar nu met een antwoord dat nog wel ergens
        // over gaat: dan blijft de TTL gelden en niet de korte wachttijd.
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertFalse(gate.claim("1", minuut, exhausted = false))
    }

    @Test
    fun vlakVoorDeCacheTtlNogNiet() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertFalse(gate.claim("1", EpgRequestGate.STALE_AFTER_MS - 1))
    }

    @Test
    fun binnenDeWachttijdNaEenFoutNietOpnieuw() {
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markFailed("1", 0L)
        assertFalse(gate.claim("1", minuut - 1))
    }

    @Test
    fun naDeWachttijdKrijgtEenFoutEenNieuweKans() {
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markFailed("1", 0L)
        // Een hapering in het netwerk mag geen zender opleveren die tot het
        // afsluiten van de app leeg blijft.
        assertTrue(gate.claim("1", minuut))
    }

    @Test
    fun deNieuweKansGeldtMaarEenKeerTegelijk() {
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markFailed("1", 0L)
        assertTrue(gate.claim("1", minuut))
        // Die tweede poging loopt nu, dus een derde rij die langskomt mag
        // er niet alsnog een aanvraag bovenop zetten.
        assertFalse(gate.claim("1", minuut))
    }

    @Test
    fun afbrekenGeeftDeReserveringTerug() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.release("1")
        // Weggescrold voor het antwoord er was: terugscrollen moet het
        // gewoon opnieuw proberen.
        assertTrue(gate.claim("1", 0L))
    }

    @Test
    fun afbrekenWistGeenAfgerondAntwoord() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        gate.release("1")
        assertFalse(gate.claim("1", 0L))
    }

    @Test
    fun afbrekenWistGeenEerdereFout() {
        val gate = EpgRequestGate(retryAfterMs = minuut)
        gate.claim("1", 0L)
        gate.markFailed("1", 0L)
        gate.release("1")
        // Anders zou een afgebroken aanvraag de wachttijd omzeilen.
        assertFalse(gate.claim("1", 1L))
    }

    @Test
    fun zendersStaanLosVanElkaar() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        assertTrue(gate.claim("2", 0L))
    }

    @Test
    fun clearVergeetAlles() {
        val gate = EpgRequestGate()
        gate.claim("1", 0L)
        gate.markDone("1", 0L)
        gate.clear()
        assertTrue(gate.claim("1", 0L))
    }
}

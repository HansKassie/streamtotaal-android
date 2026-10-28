package nl.streamfix.domain.util

import nl.streamfix.domain.model.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt de twee cachebeslissingen. Op het toestel zijn die niet te meten:
 * je kunt een provider niet op commando laten falen en een cache-rij niet
 * op commando laten verouderen.
 */
class EpgCachePolicyTest {

    private val uur = 60 * 60 * 1000L
    private val ttl = 3 * uur
    private val tien = 10 * uur

    private fun prog(start: Long, eind: Long) =
        EpgProgramme("Journaal", "", start, eind)

    private val lopend = listOf(prog(tien, tien + uur))

    @Test
    fun verseEnLopendeGidsIsBruikbaar() {
        assertTrue(
            EpgCachePolicy.usable(lopend, tien, tien + uur / 2, ttl),
        )
    }

    @Test
    fun naDeTtlNietMeerBruikbaar() {
        val laat = tien + ttl
        // Programma loopt dan nog, maar de rij is te oud.
        val langLopend = listOf(prog(tien, tien + 10 * uur))
        assertFalse(EpgCachePolicy.usable(langLopend, tien, laat, ttl))
    }

    @Test
    fun binnenDeTtlMaarHelemaalVerledenIsNietBruikbaar() {
        // De kern van deze regel: vers op OPHAALTIJD is niet hetzelfde als
        // bruikbaar. Opgehaald om 10:00, nu 12:00, gids liep tot 11:00.
        assertFalse(
            EpgCachePolicy.usable(lopend, tien, tien + 2 * uur, ttl),
        )
    }

    @Test
    fun eenLegeGidsBlijftBinnenDeTtlGeldig() {
        // Leeg betekent: deze zender heeft geen gids. Elke keer opnieuw
        // vragen levert toch hetzelfde op.
        assertTrue(
            EpgCachePolicy.usable(emptyList(), tien, tien + uur, ttl),
        )
    }

    @Test
    fun eenLegeGidsVerlooptWelMetDeTtl() {
        assertFalse(
            EpgCachePolicy.usable(emptyList(), tien, tien + ttl, ttl),
        )
    }

    @Test
    fun bijEenFoutGeenCacheIsGeenTerugval() {
        assertNull(EpgCachePolicy.failureFallback(null, tien))
    }

    @Test
    fun bijEenFoutIsEenLegeCacheGeenAntwoord() {
        // Dit was de fout: een lege cache-rij werd als geslaagd doorgegeven,
        // dus wachtte de aanroeper de volle TTL in plaats van de korte
        // wachttijd die bij een netwerkfout hoort.
        assertNull(EpgCachePolicy.failureFallback(emptyList(), tien))
    }

    @Test
    fun bijEenFoutIsEenAfgelopenCacheGeenAntwoord() {
        assertNull(
            EpgCachePolicy.failureFallback(lopend, tien + 2 * uur),
        )
    }

    @Test
    fun bijEenFoutMagEenLopendeCacheWelTerugkomen() {
        // De offline-val waar hij voor bedoeld is: beter de gids van een
        // kwartier geleden dan een foutmelding, zolang hij nog over nu of
        // later gaat.
        val r = EpgCachePolicy.failureFallback(lopend, tien + uur / 2)
        assertEquals("Journaal", r?.first()?.title)
    }

    @Test
    fun bijEenFoutMagEenCacheDieNogIetsBelooftOokTerugkomen() {
        // Gat in de gids: nu loopt er niets, maar er komt nog wel iets.
        // Dat is bruikbare informatie en geen reden voor een foutmelding.
        val straks = listOf(prog(tien + 2 * uur, tien + 3 * uur))
        val r = EpgCachePolicy.failureFallback(straks, tien + uur)
        assertEquals("Journaal", r?.first()?.title)
    }
}

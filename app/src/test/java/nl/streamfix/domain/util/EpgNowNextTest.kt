package nl.streamfix.domain.util

import nl.streamfix.domain.model.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt de randgevallen van de "Nu en straks"-balk in de live-speler. Op het
 * toestel zijn die niet te meten: je kunt geen programmawissel afdwingen en
 * een gat in de gids evenmin.
 */
class EpgNowNextTest {

    private val uur = 60 * 60 * 1000L
    private val tien = 10 * uur

    private fun prog(title: String, startMs: Long, endMs: Long) =
        EpgProgramme(title, "", startMs, endMs)

    private val gids = listOf(
        prog("Journaal", tien, tien + uur),
        prog("Film", tien + uur, tien + 3 * uur),
        prog("Sport", tien + 3 * uur, tien + 4 * uur),
    )

    @Test
    fun middenInEenProgramma() {
        val r = EpgNowNext.of(gids, tien + uur / 2)
        assertEquals("Journaal", r.now?.title)
        assertEquals("Film", r.next?.title)
    }

    @Test
    fun opDeGrensBegintHetNieuweProgramma() {
        // Precies om 11:00 hoort de film te spelen, niet het journaal.
        val r = EpgNowNext.of(gids, tien + uur)
        assertEquals("Film", r.now?.title)
        assertEquals("Sport", r.next?.title)
    }

    @Test
    fun ongesorteerdAntwoordGeeftHetzelfde() {
        // Het panel levert geen gegarandeerde volgorde.
        val r = EpgNowNext.of(gids.reversed(), tien + uur / 2)
        assertEquals("Journaal", r.now?.title)
        assertEquals("Film", r.next?.title)
    }

    @Test
    fun gatInDeGidsHeeftGeenNuMaarWelStraks() {
        val metGat = listOf(
            prog("Journaal", tien, tien + uur),
            prog("Sport", tien + 3 * uur, tien + 4 * uur),
        )
        val r = EpgNowNext.of(metGat, tien + 2 * uur)
        assertNull(r.now)
        assertEquals("Sport", r.next?.title)
    }

    @Test
    fun naHetLaatsteProgrammaIsErNietsMeer() {
        val r = EpgNowNext.of(gids, tien + 5 * uur)
        assertNull(r.now)
        assertNull(r.next)
    }

    @Test
    fun voorHetEersteProgrammaIsAllesNogStraks() {
        val r = EpgNowNext.of(gids, tien - uur)
        assertNull(r.now)
        assertEquals("Journaal", r.next?.title)
    }

    @Test
    fun legeGidsGeeftNiets() {
        val r = EpgNowNext.of(emptyList(), tien)
        assertNull(r.now)
        assertNull(r.next)
    }

    @Test
    fun programmaZonderDuurIsNooitNu() {
        // Kapotte gidsgegevens: eindtijd niet na de begintijd.
        val kapot = listOf(prog("Onzin", tien, tien))
        assertNull(EpgNowNext.of(kapot, tien).now)
    }

    @Test
    fun verouderdeGidsIsNietMeerBruikbaar() {
        // Dit onderscheid bepaalt of de cache opnieuw opgehaald moet worden.
        assertFalse(EpgNowNext.stillRelevant(gids, tien + 5 * uur))
        assertTrue(EpgNowNext.stillRelevant(gids, tien + 2 * uur))
    }

    @Test
    fun gidsDieNetIsAfgelopenIsNietBruikbaar() {
        // Eindtijd gelijk aan nu: er valt niets meer te tonen.
        val r = listOf(prog("Journaal", tien, tien + uur))
        assertFalse(EpgNowNext.stillRelevant(r, tien + uur))
    }

    @Test
    fun legeGidsVraagtGeenNieuweOphaalpoging() {
        // Een zender zonder gids geeft een lege lijst terug. Die telt als
        // niet bruikbaar, dus de aanroeper moet lege lijsten apart
        // behandelen om een reeks zinloze aanvragen te voorkomen.
        assertFalse(EpgNowNext.stillRelevant(emptyList(), tien))
    }
}

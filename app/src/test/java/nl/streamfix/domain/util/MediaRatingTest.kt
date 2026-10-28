package nl.streamfix.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * De waarden in deze tests komen uit een rondgang langs de vier netwerken op
 * 2026-10-28, niet uit de duim. Op het toestel zijn ze niet te sturen: je
 * kunt een panel geen 55 laten teruggeven wanneer het jou uitkomt.
 */
class MediaRatingTest {

    @Test
    fun eenGewoonCijfer() {
        assertEquals(7.5, MediaRating.of("7.5")!!, 0.001)
    }

    @Test
    fun heelGetalBlijftHeel() {
        assertEquals(8.0, MediaRating.of("8")!!, 0.001)
    }

    @Test
    fun afrondenOpEenDecimaal() {
        // Netwerk D levert dit soort precisie: 6.897 hoort 6,9 te worden.
        assertEquals(6.9, MediaRating.of("6.897")!!, 0.001)
        assertEquals(6.5, MediaRating.of("6.544")!!, 0.001)
    }

    @Test
    fun kommaAlsDecimaalteken() {
        // Niet elk panel gebruikt een punt.
        assertEquals(6.9, MediaRating.of("6,897")!!, 0.001)
    }

    @Test
    fun deRandenVanHetBereik() {
        assertEquals(10.0, MediaRating.of("10")!!, 0.001)
        assertEquals(0.5, MediaRating.of("0.5")!!, 0.001)
    }

    @Test
    fun nulIsGeenBeoordeling() {
        // Bij netwerk B staat hier bij alle 35.000 films een nul. Die als
        // "0,0" tonen is slechter dan niets tonen.
        assertNull(MediaRating.of("0"))
        assertNull(MediaRating.of("0.0"))
    }

    @Test
    fun netBovenNulMaarAfgerondNul() {
        assertNull(MediaRating.of("0.04"))
    }

    @Test
    fun bovenHetBereikIsRommel() {
        // Netwerk D heeft series met een 55 erin, met een rating_5based van
        // 27,5 erbij; het panel heeft daar de helft van onzin berekend.
        assertNull(MediaRating.of("55"))
        assertNull(MediaRating.of("10.1"))
    }

    @Test
    fun negatiefTeltNiet() {
        assertNull(MediaRating.of("-3"))
    }

    @Test
    fun leegOfOntbrekend() {
        assertNull(MediaRating.of(null))
        assertNull(MediaRating.of(""))
        assertNull(MediaRating.of("   "))
    }

    @Test
    fun onleesbareTekst() {
        assertNull(MediaRating.of("N/A"))
        assertNull(MediaRating.of("geen"))
        assertNull(MediaRating.of("7,5/10"))
    }

    @Test
    fun spatiesEromheenGevenNiet() {
        assertEquals(7.4, MediaRating.of(" 7.4 ")!!, 0.001)
    }
}

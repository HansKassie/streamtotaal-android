package nl.streamfix.domain.util

import kotlin.math.round

/**
 * Beoordeelt het `rating`-veld dat providers bij films en series meesturen.
 *
 * Dat veld is een tekstveld en panels vullen daar van alles in. Een rondgang
 * langs de vier netwerken op 2026-10-28 leverde op: lege waarden, nullen bij
 * tienduizenden titels, een enkele niet-numerieke waarde, en bij netwerk D
 * series met een 55 erin. Daarom hier één plek die bepaalt of er iets
 * toonbaars is, los van Compose zodat die randgevallen op de JVM vastliggen.
 *
 * `rating` staat op tien; dat is bij alle vier de netwerken nagekeken aan de
 * hand van `rating_5based`, dat er steeds ongeveer de helft van is. Er wordt
 * dus nooit omgerekend. `rating_5based` en `tmdb` blijven ongebruikt: de
 * eerste voegt niets toe en de tweede is een TMDB-id en geen beoordeling.
 */
object MediaRating {

    /** Hoger dan dit is geen beoordeling meer maar rommel uit het panel. */
    const val MAXIMUM = 10.0

    /**
     * Het cijfer dat getoond mag worden, afgerond op één decimaal, of null.
     *
     * Null betekent: geen badge. Dat geldt voor ontbrekend, leeg, niet
     * leesbaar, nul en alles buiten het bereik. Nul is met afstand het meest
     * voorkomend; bij netwerk B staat er bij alle 35.000 films een nul, en
     * die als "0,0" tonen is slechter dan niets tonen.
     */
    fun of(raw: String?): Double? {
        val tekst = raw?.trim().orEmpty()
        if (tekst.isEmpty()) return null
        // Panels gebruiken zowel punt als komma als decimaalteken.
        val waarde = tekst.replace(',', '.').toDoubleOrNull() ?: return null
        if (!waarde.isFinite() || waarde <= 0.0 || waarde > MAXIMUM) return null
        val afgerond = round(waarde * 10.0) / 10.0
        // 0,04 rondt af naar 0,0 en is dan alsnog niets om te tonen.
        return afgerond.takeIf { it > 0.0 }
    }
}

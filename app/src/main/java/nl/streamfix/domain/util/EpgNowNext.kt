package nl.streamfix.domain.util

import nl.streamfix.domain.model.EpgProgramme

/**
 * Bepaalt uit een programmalijst wat er NU loopt en wat DAARNA komt.
 *
 * Losse functie zonder Compose of Context eromheen, zodat de randgevallen op
 * de JVM te bewijzen zijn: een ongesorteerd antwoord van het panel, het
 * precieze moment van een programmawissel, en een gat in de gids.
 */
object EpgNowNext {

    data class NowNext(val now: EpgProgramme?, val next: EpgProgramme?)

    /**
     * [nowMs] hoort bij een programma vanaf zijn begintijd tot en met de
     * laatste milliseconde ervoor: op het hele uur is de nieuwe uitzending
     * aan de beurt en niet de vorige. Een programma met een eindtijd die
     * niet na zijn begintijd ligt kan daardoor nooit "nu" zijn, en dat is
     * precies goed voor kapotte gidsgegevens.
     *
     * Zit er een gat in de gids, dan is [NowNext.now] leeg maar [NowNext.next]
     * gevuld; dat is bruikbare informatie en geen fout.
     */
    fun of(programmes: List<EpgProgramme>, nowMs: Long): NowNext {
        if (programmes.isEmpty()) return NowNext(null, null)
        // Het panel levert de programma's niet gegarandeerd op tijd
        // gesorteerd. Zonder deze sortering hangt het antwoord af van de
        // volgorde waarin ze binnenkwamen; overlappende programma's kiezen
        // nu altijd dezelfde.
        val sorted = programmes.sortedBy { it.startMs }
        return NowNext(
            now = sorted.firstOrNull { nowMs >= it.startMs && nowMs < it.endMs },
            next = sorted.firstOrNull { it.startMs > nowMs },
        )
    }

    /**
     * Heeft deze lijst nog iets te vertellen over nu of later?
     *
     * De EPG-cache geldt drie uur op basis van OPHAALTIJD en kijkt niet of
     * de programma's het huidige moment nog raken. Een korte gids die om
     * 10:00 is opgehaald kan om 12:00 helemaal in het verleden liggen en
     * toch "vers" zijn. Dan toont de balk oude titels of ten onrechte geen
     * gids, terwijl opnieuw ophalen het antwoord wel geeft.
     */
    fun stillRelevant(programmes: List<EpgProgramme>, nowMs: Long): Boolean =
        programmes.any { it.endMs > nowMs }
}

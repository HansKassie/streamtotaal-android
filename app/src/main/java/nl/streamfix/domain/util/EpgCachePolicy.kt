package nl.streamfix.domain.util

import nl.streamfix.domain.model.EpgProgramme

/**
 * Wanneer mag de EPG-cache een antwoord geven, en wanneer niet?
 *
 * Los van Room en van het netwerk, zodat deze twee beslissingen op de JVM
 * te bewijzen zijn. Ze lijken klein maar bepalen allebei of een kijker een
 * gids te zien krijgt of niet, en de tweede heeft eerder stil een
 * netwerkfout in een "geslaagd leeg antwoord" veranderd.
 */
object EpgCachePolicy {

    /**
     * Mag een gecachte lijst als antwoord dienen zonder opnieuw op te
     * halen?
     *
     * Twee eisen, want vers op OPHAALTIJD is niet hetzelfde als bruikbaar.
     * De korte gids beslaat maar een paar uitzendingen, dus een lijst van
     * 10:00 kan om 12:00 helemaal in het verleden liggen en toch binnen de
     * TTL vallen.
     *
     * Een LEGE lijst blijft wel geldig binnen de TTL: die betekent dat de
     * zender geen gids heeft, en elke keer opnieuw vragen levert een reeks
     * zinloze aanvragen op zonder ooit een ander antwoord.
     */
    fun usable(
        programmes: List<EpgProgramme>,
        fetchedAt: Long,
        nowMs: Long,
        ttlMs: Long,
    ): Boolean {
        if (nowMs - fetchedAt >= ttlMs) return false
        return programmes.isEmpty() || EpgNowNext.stillRelevant(programmes, nowMs)
    }

    /**
     * Wat mag er bij een MISLUKTE aanvraag nog uit de cache komen?
     *
     * Null betekent: niets bruikbaars, geef de fout door.
     *
     * Hier ging het eerder mis. Elke aanwezige cache-rij werd als geslaagd
     * doorgegeven, ook een lege en ook een met alleen afgelopen
     * programma's. Daarmee werd een netwerkfout een "geslaagd leeg
     * antwoord": de aanroeper wacht dan de volle TTL voor hij het nog eens
     * probeert in plaats van de korte wachttijd die bij een fout hoort, en
     * de kijker leest "Geen gids" terwijl het ophalen simpelweg mislukte.
     *
     * Een gevulde, nog lopende gids geven we wel terug. Dat is de
     * offline-val waar hij voor bedoeld is: beter de gids van een kwartier
     * geleden dan een foutmelding, zolang hij nog over nu of later gaat.
     */
    fun failureFallback(
        cached: List<EpgProgramme>?,
        nowMs: Long,
    ): List<EpgProgramme>? {
        if (cached == null || cached.isEmpty()) return null
        return if (EpgNowNext.stillRelevant(cached, nowMs)) cached else null
    }
}

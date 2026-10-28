package nl.streamfix.domain.util

/**
 * Houdt bij welke zenders nog een gidsaanvraag verdienen.
 *
 * Een lijst met zenders vraagt de gids per zichtbare rij op. Zonder
 * boekhouding levert scrollen dezelfde aanvraag keer op keer op, en met een
 * te strenge boekhouding blijft een zender die eenmaal faalde de rest van de
 * sessie leeg. Deze klasse regelt dat verschil op een plek, los van Compose
 * en coroutines, zodat de randgevallen op de JVM te bewijzen zijn.
 *
 * Niet draadveilig: bedoeld voor aanroepen vanaf de hoofdthread.
 */
class EpgRequestGate(private val retryAfterMs: Long = RETRY_AFTER_MS) {

    private sealed interface Mark {
        /** Aanvraag loopt. */
        data object InFlight : Mark

        /** Antwoord binnen, ook als het leeg was. Niet opnieuw vragen. */
        data object Done : Mark

        /** Mislukt op [atMs]; na de wachttijd mag het opnieuw. */
        data class Failed(val atMs: Long) : Mark
    }

    private val marks = mutableMapOf<String, Mark>()

    /**
     * Mag er nu een aanvraag uit voor [channelId]? Zo ja, dan is hij meteen
     * gereserveerd en levert een tweede aanroep false op.
     *
     * Een GESLAAGD maar leeg antwoord telt als klaar. Dat betekent dat de
     * zender geen gids heeft, en opnieuw vragen levert nooit iets anders op;
     * bij een provider zonder EPG zou elke scrollbeweging anders een reeks
     * zinloze aanvragen opleveren.
     *
     * Een MISLUKTE aanvraag is iets anders: die zegt niets over de zender,
     * alleen over dat moment. Een haperend netwerk mag geen lijst opleveren
     * die tot het afsluiten van de app leeg blijft. Daarom een nieuwe kans,
     * maar pas na [retryAfterMs], want zonder wachttijd probeert elke rij
     * die langsscrolt het direct opnieuw.
     */
    fun claim(channelId: String, nowMs: Long): Boolean {
        val allowed = when (val mark = marks[channelId]) {
            null -> true
            Mark.InFlight, Mark.Done -> false
            is Mark.Failed -> nowMs - mark.atMs >= retryAfterMs
        }
        if (allowed) marks[channelId] = Mark.InFlight
        return allowed
    }

    /** Antwoord binnen (ook leeg): deze zender niet meer opvragen. */
    fun markDone(channelId: String) {
        marks[channelId] = Mark.Done
    }

    /** Mislukt: na de wachttijd mag [claim] het opnieuw toestaan. */
    fun markFailed(channelId: String, nowMs: Long) {
        marks[channelId] = Mark.Failed(nowMs)
    }

    /**
     * Reservering teruggeven zonder uitkomst.
     *
     * Nodig als de aanvraag wordt afgebroken omdat de rij uit beeld
     * scrolde. Zou de reservering blijven staan, dan is de aanvraag nooit
     * afgerond en vraagt niemand hem ooit nog op, dus blijft die zender leeg
     * zodra je terugscrolt.
     */
    fun release(channelId: String) {
        if (marks[channelId] == Mark.InFlight) marks.remove(channelId)
    }

    /** Alles vergeten, bijvoorbeeld bij een andere account. */
    fun clear() {
        marks.clear()
    }

    companion object {
        const val RETRY_AFTER_MS = 60_000L
    }
}

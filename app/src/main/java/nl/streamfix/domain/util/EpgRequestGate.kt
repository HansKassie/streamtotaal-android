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
class EpgRequestGate(
    private val retryAfterMs: Long = RETRY_AFTER_MS,
    private val staleAfterMs: Long = STALE_AFTER_MS,
) {

    private sealed interface Mark {
        /** Aanvraag loopt. */
        data object InFlight : Mark

        /** Antwoord binnen op [atMs], ook als het leeg was. */
        data class Done(val atMs: Long) : Mark

        /** Mislukt op [atMs]; na de wachttijd mag het opnieuw. */
        data class Failed(val atMs: Long) : Mark
    }

    private val marks = mutableMapOf<String, Mark>()

    /**
     * Mag er nu een aanvraag uit voor [channelId]? Zo ja, dan is hij meteen
     * gereserveerd en levert een tweede aanroep false op.
     *
     * Een GESLAAGD antwoord telt als klaar, ook een leeg: dat betekent dat
     * de zender geen gids heeft, en bij een provider zonder EPG zou elke
     * scrollbeweging anders een reeks zinloze aanvragen opleveren. Maar
     * klaar is niet voor altijd. Een provider kan de gids later alsnog
     * aanleveren, en zonder grens blijft zo'n zender leeg zolang het scherm
     * openstaat, want dit scherm heeft geen verversknop. Na [staleAfterMs]
     * mag het dus opnieuw. Die grens is gelijk aan de TTL van de
     * EPG-cache: eerder vragen levert toch alleen hetzelfde gecachte
     * antwoord op, later vragen laat de cache voor niets verlopen.
     *
     * Een MISLUKTE aanvraag is iets anders: die zegt niets over de zender,
     * alleen over dat moment. Een haperend netwerk mag geen lijst opleveren
     * die tot het afsluiten van de app leeg blijft. Daarom een nieuwe kans,
     * maar pas na [retryAfterMs], want zonder wachttijd probeert elke rij
     * die langsscrolt het direct opnieuw.
     */
    fun claim(
        channelId: String,
        nowMs: Long,
        exhausted: Boolean = false,
    ): Boolean {
        // Een gids die helemaal in het verleden ligt is geen antwoord meer
        // op de vraag "wat speelt er nu". Daarvoor geldt de korte
        // wachttijd en niet de TTL, anders houdt deze poort een rij uren
        // op oude titels terwijl de cache eronder allang bereid is
        // opnieuw op te halen. Een LEEG antwoord valt hier bewust niet
        // onder: dat is geen verouderde gids maar een zender zonder gids,
        // en dat verandert zelden genoeg om er elke minuut naar te vragen.
        val doneWindow = if (exhausted) retryAfterMs else staleAfterMs
        val allowed = when (val mark = marks[channelId]) {
            null -> true
            Mark.InFlight -> false
            is Mark.Done -> nowMs - mark.atMs >= doneWindow
            is Mark.Failed -> nowMs - mark.atMs >= retryAfterMs
        }
        if (allowed) marks[channelId] = Mark.InFlight
        return allowed
    }

    /** Antwoord binnen (ook leeg): pas na de TTL weer opvragen. */
    fun markDone(channelId: String, nowMs: Long) {
        marks[channelId] = Mark.Done(nowMs)
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
        /** Wachttijd na een mislukte aanvraag. */
        const val RETRY_AFTER_MS = 60_000L

        /**
         * Wanneer een geslaagd antwoord weer oud genoeg is om opnieuw te
         * vragen. Gelijk aan de TTL van de EPG-cache in EpgRepositoryImpl;
         * die twee horen hetzelfde getal te zijn, anders vraagt dit om een
         * antwoord dat de cache toch uit zijn eigen voorraad teruggeeft.
         */
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L
    }
}

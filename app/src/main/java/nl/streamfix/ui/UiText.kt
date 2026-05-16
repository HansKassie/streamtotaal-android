package nl.streamfix.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nl.streamfix.domain.model.AppError

/**
 * Xtream exp_date is een Unix-timestamp (seconden). Maak er een leesbare
 * datum van in de toestel-tijdzone. Leeg/0 = onbeperkt; niet-numeriek
 * (sommige panels sturen al een datumtekst) wordt ongewijzigd getoond.
 */
fun formatXtreamExpiry(raw: String?): String? {
    val value = raw?.trim()
    if (value.isNullOrEmpty()) return null
    val seconds = value.toLongOrNull() ?: return value
    if (seconds <= 0L) return "Onbeperkt"
    return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        .format(Date(seconds * 1000L))
}

/** Nederlandse, gebruiksvriendelijke foutmeldingen (briefing-criterium). */
fun AppError.uiMessage(): String = when (this) {
    AppError.InvalidCredentials ->
        "Gebruikersnaam of wachtwoord klopt niet."
    AppError.SubscriptionExpired ->
        "Je abonnement is verlopen. Neem contact op met je provider."
    AppError.ServerUnreachable ->
        "De server is niet bereikbaar. Controleer de server-URL."
    AppError.ProviderUnavailable ->
        "De server van je provider reageert niet (mogelijk tijdelijk offline). " +
            "Probeer het later opnieuw of gebruik een andere provider."
    AppError.NetworkUnavailable ->
        "Geen internetverbinding. Probeer het opnieuw."
    AppError.NotAnXtreamServer ->
        "Dit lijkt geen geldige Xtream Codes server."
    AppError.InvalidUrl ->
        "De URL is ongeldig. Controleer op spaties en of het webadres klopt."
    AppError.Unknown ->
        "Er ging iets mis. Probeer het opnieuw."
}

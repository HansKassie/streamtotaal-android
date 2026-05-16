package nl.streamfix.ui

import nl.streamfix.domain.model.AppError

/** Nederlandse, gebruiksvriendelijke foutmeldingen (briefing-criterium). */
fun AppError.uiMessage(): String = when (this) {
    AppError.InvalidCredentials ->
        "Gebruikersnaam of wachtwoord klopt niet."
    AppError.SubscriptionExpired ->
        "Je abonnement is verlopen. Neem contact op met je provider."
    AppError.ServerUnreachable ->
        "De server is niet bereikbaar. Controleer de server-URL."
    AppError.NetworkUnavailable ->
        "Geen internetverbinding. Probeer het opnieuw."
    AppError.NotAnXtreamServer ->
        "Dit lijkt geen geldige Xtream Codes server."
    AppError.InvalidUrl ->
        "De URL is ongeldig. Controleer op spaties en of het webadres klopt."
    AppError.InvalidM3u ->
        "Dit is geen geldige M3U-playlist."
    AppError.Unknown ->
        "Er ging iets mis. Probeer het opnieuw."
}

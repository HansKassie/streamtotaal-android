package nl.streamfix.ui

import nl.streamfix.domain.model.LiveChannel

/**
 * Bepaalt op welke zender een kanaallijst de focus moet zetten bij het
 * (her)opbouwen, bijvoorbeeld na terugkeer uit de speler.
 *
 * Volgorde van voorkeur:
 * 1. [lastWatchedId] - de zender waar de speler op eindigde. Wie daar met
 *    de pijltjes doorzapt, verwacht bij terugkeer die zender en niet
 *    degene die hij oorspronkelijk aantikte.
 * 2. [lastFocusedId] - het laatst gefocuste item, voor het geval er niet
 *    is gekeken (of het bekeken kanaal niet in deze lijst staat).
 * 3. Het begin van de lijst.
 *
 * Kandidaten die niet in [channels] voorkomen worden overgeslagen; dat
 * gebeurt bij een andere categorie, een providerwissel of wanneer het
 * volwassen-filter een kanaal verbergt.
 */
fun pickFocusTarget(
    channels: List<LiveChannel>,
    lastWatchedId: String?,
    lastFocusedId: String?,
): String? {
    if (channels.isEmpty()) return null
    if (lastWatchedId != null && channels.any { it.id == lastWatchedId }) {
        return lastWatchedId
    }
    if (lastFocusedId != null && channels.any { it.id == lastFocusedId }) {
        return lastFocusedId
    }
    return channels.first().id
}

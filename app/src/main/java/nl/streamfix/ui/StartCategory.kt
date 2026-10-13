package nl.streamfix.ui

/**
 * Bepaalt met welke groep de zenderlijst opent na het starten van de app of
 * na een providerwissel.
 *
 * De laatst bekeken groep gaat voor op de eerste groep uit de lijst: wie de
 * app afsluit terwijl hij in "Sport" keek, verwacht daar weer te beginnen en
 * niet bovenaan bij de eerste groep. Zonder dit stond de onthouden zender
 * wel vast, maar in een groep die niet openstond, waardoor er niets van te
 * zien was.
 *
 * [lastCategoryId] wordt overgeslagen als die groep er niet meer is: een
 * andere provider, een gewijzigd aanbod, of het volwassen-filter dat de
 * groep verbergt.
 */
fun pickStartCategory(
    categoryIds: List<String>,
    lastCategoryId: String?,
    favoritesId: String,
): String {
    if (lastCategoryId != null &&
        (lastCategoryId == favoritesId || lastCategoryId in categoryIds)
    ) {
        return lastCategoryId
    }
    return categoryIds.firstOrNull() ?: favoritesId
}

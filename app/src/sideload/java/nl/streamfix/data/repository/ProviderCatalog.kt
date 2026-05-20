package nl.streamfix.data.repository

import nl.streamfix.domain.model.Provider

/**
 * Sideload-flavor: ingebouwde providerlijst + optionele remote-catalog-URL.
 * Vul [REMOTE_CATALOG_URL] met een door jou gehoste JSON-URL
 * ([{"name":"...","url":"..."}, ...]) om providers te wijzigen zonder
 * app-update. Leeg = alleen [BUNDLED].
 */
internal object ProviderCatalog {
    const val REMOTE_CATALOG_URL: String = ""

    val BUNDLED: List<Provider> = listOf(
        Provider("PROMAX", "http://line.smarttelevision.xyz"),
        Provider("DIAMOND", "http://kassiee.clear-ocean.link"),
        Provider("NETTV", "http://sales.vivotv.vip"),
        Provider("TIVIONE", "http://line.tivi-ott.net"),
    )
}

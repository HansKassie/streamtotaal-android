package nl.streamfix.data.repository

import nl.streamfix.domain.model.Provider

/**
 * Play Store-flavor: geen ingebouwde providers en geen remote catalog.
 * De gebruiker tikt zelf een Server-URL in op het inlogscherm.
 */
internal object ProviderCatalog {
    const val REMOTE_CATALOG_URL: String = ""
    val BUNDLED: List<Provider> = emptyList()
}

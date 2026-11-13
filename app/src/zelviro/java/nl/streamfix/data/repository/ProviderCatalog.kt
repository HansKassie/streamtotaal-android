package nl.streamfix.data.repository

import nl.streamfix.domain.model.Provider

/**
 * ZelViro-flavor: de algemene speler. Geen ingebouwde netwerken en geen
 * catalogus op afstand: de afspeellijsten komen van het portaal
 * (zelviro-player.com), per apparaat gekoppeld door de klant zelf.
 */
internal object ProviderCatalog {
    const val REMOTE_CATALOG_URL: String = ""

    val BUNDLED: List<Provider> = emptyList()

    /** Geen oude netwerknamen om om te zetten. */
    val NETWORK_NAME_BY_HOST: Map<String, String> = emptyMap()
}

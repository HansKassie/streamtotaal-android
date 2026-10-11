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

    /**
     * Neutrale namen: de klant ziet een letter, niet de naam van het
     * netwerk. Bij welk netwerk iemand hoort, staat op zijn eigen account
     * op het klantenportaal.
     *
     * De koppeling tussen letter en adres is daarmee klantdocumentatie
     * geworden en ligt vast. Wijzig een letter dus NIET, ook niet als er
     * ooit een netwerk bijkomt of wegvalt: een nieuw netwerk krijgt de
     * volgende vrije letter, en een vervallen letter wordt niet hergebruikt.
     *
     *   A = Diamond   B = Nettv   C = Promax   D = TiviOne
     */
    /**
     * Elk adres dat ooit bij een netwerk hoorde, OOK de vervallen adressen.
     * Een account dat nog op een oud adres staat hoort immers dezelfde
     * letter te krijgen. Gebruikt door de hernoeming bij het opstarten.
     */
    val NETWORK_NAME_BY_HOST: Map<String, String> = mapOf(
        "kassiee.clear-ocean.link" to "Netwerk A",
        "tv.allesoptv.live" to "Netwerk B",
        "sales.vivotv.vip" to "Netwerk B",
        "line.smarttelevision.xyz" to "Netwerk C",
        "line.teevee-online.org" to "Netwerk D",
        "line.tivi-ott.net" to "Netwerk D",
    )

    val BUNDLED: List<Provider> = listOf(
        Provider("Netwerk A", "http://kassiee.clear-ocean.link"),
        Provider("Netwerk B", "http://tv.allesoptv.live"),
        Provider("Netwerk C", "http://line.smarttelevision.xyz"),
        Provider("Netwerk D", "http://line.teevee-online.org"),
    )
}

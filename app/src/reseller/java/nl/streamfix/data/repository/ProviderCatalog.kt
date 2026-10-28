package nl.streamfix.data.repository

import nl.streamfix.domain.model.Provider

/**
 * Reseller-flavor: de uitvoering voor de collega-reseller.
 *
 * Alleen zijn eigen netwerk. Onze andere netwerken staan hier bewust niet,
 * ook niet in [NETWORK_NAME_BY_HOST]: zijn klanten hebben niets aan een
 * lijst met adressen waar ze geen abonnement op hebben, en verbergen in de
 * interface is iets anders dan niet meeleveren.
 *
 * Dit netwerk heet hier **Netwerk A** en in onze eigen uitvoering Netwerk E.
 * Dat is geen slordigheid en hoort niet "rechtgetrokken" te worden. Die
 * letters zijn klantdocumentatie, en de twee apps hebben elk hun eigen
 * klanten. In zijn app is dit het enige netwerk, dus een E zou suggereren
 * dat er ergens nog een A tot en met D te vinden is.
 *
 * Verwarring tussen de twee kan niet ontstaan: het zijn aparte
 * applicationIds, dus een toestel heeft nooit allebei dezelfde opgeslagen
 * account.
 *
 * Deze flavor deelt alle overige code met sideload via src/distributable.
 * Verschilt er iets in gedrag, dan hoort dat hier of in een
 * buildConfigField te staan, niet in een tweede kopie van een bestand.
 */
internal object ProviderCatalog {
    /**
     * Leeg: één vast netwerk heeft geen catalogus op afstand nodig. Wil je
     * zijn adres ooit kunnen wijzigen zonder app-update, vul dit dan met een
     * eigen JSON-URL. Let op dat een mislukte of lege ophaalpoging terugvalt
     * op [BUNDLED], dus dat netwerk hoort hoe dan ook hieronder te blijven.
     */
    const val REMOTE_CATALOG_URL: String = ""

    /** Elk adres dat ooit bij dit netwerk hoorde; zie de sideload-variant. */
    val NETWORK_NAME_BY_HOST: Map<String, String> = mapOf(
        "line.trxdnscloud.ru" to "Netwerk A",
    )

    val BUNDLED: List<Provider> = listOf(
        Provider("Netwerk A", "http://line.trxdnscloud.ru"),
    )
}

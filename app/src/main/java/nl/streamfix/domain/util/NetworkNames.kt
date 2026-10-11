package nl.streamfix.domain.util

/**
 * Vertaalt het serveradres van een opgeslagen account naar de neutrale
 * netwerknaam die de klant hoort te zien.
 *
 * De namen worden bij het inloggen eenmalig bij het account opgeslagen, dus
 * wie al voor de hernoeming een provider had toegevoegd, ziet anders nog de
 * oude naam van het netwerk staan.
 *
 * De koppeling zelf zit niet hier maar per flavor in ProviderCatalog: de
 * Play Store-build hoort geen resellergegevens te bevatten. Die tabel bevat
 * ook de vervallen adressen, want een account dat nog op een oud adres
 * staat hoort dezelfde letter te krijgen.
 */
object NetworkNames {

    /** Nieuwe naam, of null als dit adres geen bekend netwerk is. */
    fun forServerUrl(serverUrl: String, byHost: Map<String, String>): String? {
        if (byHost.isEmpty()) return null
        return byHost[hostOf(serverUrl)]
    }

    /**
     * Alleen de hostnaam, zonder schema, poort, pad of hoofdletters. Geen
     * android.net.Uri, zodat dit op de JVM te testen is.
     */
    fun hostOf(serverUrl: String): String =
        serverUrl.trim()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .lowercase()
}

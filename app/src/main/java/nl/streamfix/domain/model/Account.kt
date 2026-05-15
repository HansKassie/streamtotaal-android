package nl.streamfix.domain.model

/**
 * Een ingelogd account. Versie 1.0 ondersteunt Xtream Codes en M3U.
 * Meerdere profielen worden ondersteund, vandaar de stabiele [id].
 */
sealed interface Account {
    val id: String
    val displayName: String

    data class Xtream(
        override val id: String,
        override val displayName: String,
        val serverUrl: String,
        val username: String,
        val password: String,
    ) : Account

    data class M3u(
        override val id: String,
        override val displayName: String,
        val source: M3uSource,
    ) : Account
}

sealed interface M3uSource {
    data class Url(val url: String) : M3uSource

    /** Persistente content-URI van een via SAF gekozen lokaal bestand. */
    data class LocalFile(val uri: String) : M3uSource
}

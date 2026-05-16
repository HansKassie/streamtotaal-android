package nl.streamfix.domain.model

/**
 * Een ingelogd account. Versie 1.0 ondersteunt uitsluitend Xtream Codes.
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
        // Voorkeursextensie voor live streams (m3u8/ts), uit user_info.
        val liveExtension: String = "ts",
        // Door de gebruiker gekozen live-formaat: "auto" volgt liveExtension,
        // anders "ts" of "m3u8" als harde override.
        val streamFormat: String = "auto",
    ) : Account
}

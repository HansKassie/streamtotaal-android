package nl.streamfix.domain.model

/**
 * Account-info uit de Xtream user_info-respons (briefing 3.1).
 * Velden zijn nullable omdat providers hier sterk in afwijken.
 */
data class AccountInfo(
    val username: String?,
    val status: String?,
    val expirationDate: String?,
    val maxConnections: String?,
    val activeConnections: String?,
)

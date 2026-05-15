package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respons van player_api.php zonder action (briefing 7.1).
 * Alle velden nullable: Xtream-servers wijken sterk af van de standaard.
 */
@Serializable
data class XtreamAuthResponseDto(
    @SerialName("user_info") val userInfo: XtreamUserInfoDto? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfoDto? = null,
)

@Serializable
data class XtreamUserInfoDto(
    @SerialName("username") val username: String? = null,
    @SerialName("auth") val auth: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("active_cons") val activeConnections: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class XtreamServerInfoDto(
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null,
)

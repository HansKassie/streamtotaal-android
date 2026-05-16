package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Respons van player_api.php zonder action. Velden die per provider als
 * string OF getal komen, worden als JsonElement tolerant ingelezen, anders
 * faalt de hele login bij afwijkende panels.
 */
@Serializable
data class XtreamAuthResponseDto(
    @SerialName("user_info") val userInfo: XtreamUserInfoDto? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfoDto? = null,
)

@Serializable
data class XtreamUserInfoDto(
    @SerialName("username") val username: String? = null,
    @SerialName("auth") val auth: JsonElement? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("exp_date") val expDate: JsonElement? = null,
    @SerialName("active_cons") val activeConnections: JsonElement? = null,
    @SerialName("max_connections") val maxConnections: JsonElement? = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: JsonElement? = null,
    @SerialName("message") val message: String? = null,
) {
    val authValue: Int?
        get() = auth?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val expDateValue: String?
        get() = expDate?.jsonPrimitive?.contentOrNull
    val activeConnectionsValue: String?
        get() = activeConnections?.jsonPrimitive?.contentOrNull
    val maxConnectionsValue: String?
        get() = maxConnections?.jsonPrimitive?.contentOrNull
    val outputFormats: List<String>
        get() = runCatching {
            allowedOutputFormats?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }
        }.getOrNull().orEmpty()
}

@Serializable
data class XtreamServerInfoDto(
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null,
)

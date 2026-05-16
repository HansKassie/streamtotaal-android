package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderDto(
    @SerialName("name") val name: String? = null,
    @SerialName("url") val url: String? = null,
)

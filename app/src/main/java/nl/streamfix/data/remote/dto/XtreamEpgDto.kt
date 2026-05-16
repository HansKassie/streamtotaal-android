package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class XtreamEpgResponseDto(
    @SerialName("epg_listings") val listings: List<XtreamEpgListingDto>? = null,
)

@Serializable
data class XtreamEpgListingDto(
    // title/description zijn base64; timestamps soms string, soms getal.
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("start_timestamp") val startTimestamp: JsonElement? = null,
    @SerialName("stop_timestamp") val stopTimestamp: JsonElement? = null,
) {
    val startSeconds: Long?
        get() = startTimestamp?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val stopSeconds: Long?
        get() = stopTimestamp?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}

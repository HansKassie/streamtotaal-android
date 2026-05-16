package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class XtreamCategoryDto(
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
)

@Serializable
data class XtreamLiveStreamDto(
    // stream_id is per provider string of getal: tolerant inlezen.
    @SerialName("stream_id") val streamId: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
) {
    val streamIdValue: String? get() = streamId?.jsonPrimitive?.contentOrNull
}

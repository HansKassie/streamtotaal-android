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
    // Catch-up: 0/1 (soms string) en aantal dagen archief.
    @SerialName("tv_archive") val tvArchive: JsonElement? = null,
    @SerialName("tv_archive_duration") val tvArchiveDuration: JsonElement? = null,
) {
    val streamIdValue: String? get() = streamId?.jsonPrimitive?.contentOrNull

    /** Aantal dagen terugkijken, of 0 als het kanaal geen archief heeft. */
    val archiveDays: Int
        get() {
            val on = tvArchive?.jsonPrimitive?.contentOrNull
                ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                ?: false
            if (!on) return 0
            return tvArchiveDuration?.jsonPrimitive?.contentOrNull
                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        }
}

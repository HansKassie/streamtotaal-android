package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class XtreamSeriesStreamDto(
    @SerialName("series_id") val seriesId: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
) {
    val seriesIdValue: String? get() = seriesId?.jsonPrimitive?.contentOrNull
}

@Serializable
data class XtreamSeriesInfoResponseDto(
    @SerialName("info") val info: XtreamSeriesInfoDto? = null,
    @SerialName("episodes") val episodes: Map<String, List<XtreamEpisodeDto>>? = null,
)

@Serializable
data class XtreamSeriesInfoDto(
    @SerialName("name") val name: String? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("cast") val cast: String? = null,
    @SerialName("director") val director: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("rating") val rating: String? = null,
)

@Serializable
data class XtreamEpisodeDto(
    // id en episode_num verschillen per provider tussen string en getal.
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("episode_num") val episodeNum: JsonElement? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
) {
    val idValue: String? get() = id?.jsonPrimitive?.contentOrNull
    val episodeNumValue: String? get() = episodeNum?.jsonPrimitive?.contentOrNull
}

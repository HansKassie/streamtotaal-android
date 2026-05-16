package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class XtreamVodStreamDto(
    @SerialName("stream_id") val streamId: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
    @SerialName("rating") val rating: String? = null,
) {
    val streamIdValue: String? get() = streamId?.jsonPrimitive?.contentOrNull
}

@Serializable
data class XtreamVodInfoResponseDto(
    @SerialName("info") val info: XtreamVodInfoDto? = null,
    @SerialName("movie_data") val movieData: XtreamVodMovieDataDto? = null,
)

@Serializable
data class XtreamVodInfoDto(
    @SerialName("plot") val plot: String? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("releasedate") val releaseDate: String? = null,
    @SerialName("director") val director: String? = null,
    @SerialName("cast") val cast: String? = null,
    @SerialName("rating") val rating: String? = null,
    @SerialName("duration") val duration: String? = null,
    @SerialName("movie_image") val movieImage: String? = null,
)

@Serializable
data class XtreamVodMovieDataDto(
    @SerialName("stream_id") val streamId: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
)

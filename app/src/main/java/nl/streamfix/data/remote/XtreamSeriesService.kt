package nl.streamfix.data.remote

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.domain.model.Episode
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.Season
import nl.streamfix.domain.model.SeriesDetail
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.util.AppResult

class XtreamSeriesService @Inject constructor(
    private val api: XtreamApi,
) {
    suspend fun categories(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<List<LiveCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password, action = "get_series_categories",
            )
            AppResult.Success(
                api.getSeriesCategories(url).mapNotNull { dto ->
                    val id = dto.categoryId ?: return@mapNotNull null
                    LiveCategory(id = id, name = dto.categoryName ?: id)
                },
            )
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }

    suspend fun items(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String,
    ): AppResult<List<SeriesItem>> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password,
                action = "get_series",
                params = mapOf("category_id" to categoryId),
            )
            AppResult.Success(
                api.getSeriesStreams(url).mapNotNull { dto ->
                    val id = dto.seriesId?.toString() ?: return@mapNotNull null
                    SeriesItem(
                        id = id,
                        name = dto.name?.ifBlank { "Serie $id" } ?: "Serie $id",
                        posterUrl = dto.cover?.takeIf { it.isNotBlank() },
                        categoryId = dto.categoryId ?: categoryId,
                    )
                },
            )
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }

    suspend fun detail(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: String,
    ): AppResult<SeriesDetail> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password,
                action = "get_series_info",
                params = mapOf("series_id" to seriesId),
            )
            val dto = api.getSeriesInfo(url)
            val info = dto.info
            val seasons = dto.episodes.orEmpty()
                .mapNotNull { (seasonKey, eps) ->
                    val seasonNum = seasonKey.toIntOrNull() ?: return@mapNotNull null
                    val episodes = eps.mapNotNull { e ->
                        val eid = e.idValue ?: return@mapNotNull null
                        val num = e.episodeNumValue?.toIntOrNull() ?: 0
                        Episode(
                            id = eid,
                            title = e.title?.ifBlank { "Aflevering $num" }
                                ?: "Aflevering $num",
                            seasonNumber = seasonNum,
                            episodeNumber = num,
                            containerExtension = e.containerExtension
                                ?.takeIf { it.isNotBlank() } ?: "mp4",
                        )
                    }.sortedBy { it.episodeNumber }
                    Season(number = seasonNum, episodes = episodes)
                }
                .sortedBy { it.number }

            AppResult.Success(
                SeriesDetail(
                    id = seriesId,
                    name = info?.name?.ifBlank { "Serie $seriesId" }
                        ?: "Serie $seriesId",
                    posterUrl = info?.cover?.takeIf { it.isNotBlank() },
                    plot = info?.plot?.takeIf { it.isNotBlank() },
                    genre = info?.genre?.takeIf { it.isNotBlank() },
                    cast = info?.cast?.takeIf { it.isNotBlank() },
                    director = info?.director?.takeIf { it.isNotBlank() },
                    year = info?.releaseDate?.takeIf { it.isNotBlank() },
                    rating = info?.rating?.takeIf { it.isNotBlank() },
                    seasons = seasons,
                ),
            )
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }
}

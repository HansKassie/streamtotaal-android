package nl.streamfix.data.remote

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.VodDetail
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.util.AppResult

class XtreamVodService @Inject constructor(
    private val api: XtreamApi,
) {
    suspend fun categories(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<List<LiveCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password, action = "get_vod_categories",
            )
            AppResult.Success(
                api.getVodCategories(url).mapNotNull { dto ->
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
    ): AppResult<List<VodItem>> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password,
                action = "get_vod_streams",
                params = mapOf("category_id" to categoryId),
            )
            AppResult.Success(
                api.getVodStreams(url).mapNotNull { dto ->
                    val id = dto.streamId?.toString() ?: return@mapNotNull null
                    VodItem(
                        id = id,
                        name = dto.name?.ifBlank { "Film $id" } ?: "Film $id",
                        posterUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                        categoryId = dto.categoryId ?: categoryId,
                        containerExtension = dto.containerExtension,
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
        vodId: String,
    ): AppResult<VodDetail> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password,
                action = "get_vod_info",
                params = mapOf("vod_id" to vodId),
            )
            val dto = api.getVodInfo(url)
            val info = dto.info
            val ext = dto.movieData?.containerExtension?.takeIf { it.isNotBlank() }
                ?: "mp4"
            AppResult.Success(
                VodDetail(
                    id = vodId,
                    name = dto.movieData?.name?.ifBlank { "Film $vodId" }
                        ?: "Film $vodId",
                    posterUrl = info?.movieImage?.takeIf { it.isNotBlank() },
                    plot = info?.plot?.takeIf { it.isNotBlank() },
                    genre = info?.genre?.takeIf { it.isNotBlank() },
                    year = info?.releaseDate?.takeIf { it.isNotBlank() },
                    director = info?.director?.takeIf { it.isNotBlank() },
                    cast = info?.cast?.takeIf { it.isNotBlank() },
                    rating = info?.rating?.takeIf { it.isNotBlank() },
                    duration = info?.duration?.takeIf { it.isNotBlank() },
                    containerExtension = ext,
                ),
            )
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }
}

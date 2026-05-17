package nl.streamfix.data.remote

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.util.AppResult

class XtreamLiveService @Inject constructor(
    private val api: XtreamApi,
) {
    suspend fun categories(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<List<LiveCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password, action = "get_live_categories",
            )
            val result = api.getLiveCategories(url).mapNotNull { dto ->
                val id = dto.categoryId ?: return@mapNotNull null
                LiveCategory(id = id, name = dto.categoryName ?: id)
            }
            AppResult.Success(result)
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }

    suspend fun channels(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String?,
    ): AppResult<List<LiveChannel>> = withContext(Dispatchers.IO) {
        try {
            val params = categoryId?.let { mapOf("category_id" to it) } ?: emptyMap()
            val url = XtreamUrls.playerApi(
                serverUrl, username, password,
                action = "get_live_streams", params = params,
            )
            val result = api.getLiveStreams(url).mapNotNull { dto ->
                val id = dto.streamIdValue ?: return@mapNotNull null
                LiveChannel(
                    id = id,
                    name = dto.name?.ifBlank { "Kanaal $id" } ?: "Kanaal $id",
                    logoUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                    categoryId = dto.categoryId ?: categoryId,
                    epgChannelId = dto.epgChannelId?.takeIf { it.isNotBlank() },
                    archiveDays = dto.archiveDays,
                )
            }
            AppResult.Success(result)
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }
}

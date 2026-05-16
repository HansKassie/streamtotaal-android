package nl.streamfix.domain.repository

import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.SeriesDetail
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.util.AppResult

interface SeriesRepository {
    suspend fun getCategories(): AppResult<List<LiveCategory>>
    suspend fun getItems(categoryId: String): AppResult<List<SeriesItem>>
    suspend fun getDetail(seriesId: String): AppResult<SeriesDetail>
    fun episodeStreamUrl(episodeId: String, extension: String): String?
}

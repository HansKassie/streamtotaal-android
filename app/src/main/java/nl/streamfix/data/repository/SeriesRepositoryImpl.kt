package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.remote.XtreamSeriesService
import nl.streamfix.data.remote.XtreamUrls
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.SeriesDetail
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.repository.SeriesRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val seriesService: XtreamSeriesService,
    private val store: SecureCredentialStore,
) : SeriesRepository {

    private fun acc(): Account.Xtream? =
        store.currentActiveAccount() as? Account.Xtream

    override suspend fun getCategories(): AppResult<List<LiveCategory>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return seriesService.categories(a.serverUrl, a.username, a.password)
    }

    override suspend fun getItems(categoryId: String): AppResult<List<SeriesItem>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return seriesService.items(a.serverUrl, a.username, a.password, categoryId)
    }

    override suspend fun getDetail(seriesId: String): AppResult<SeriesDetail> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return seriesService.detail(a.serverUrl, a.username, a.password, seriesId)
    }

    override fun episodeStreamUrl(episodeId: String, extension: String): String? {
        val a = acc() ?: return null
        return XtreamUrls.seriesStream(
            a.serverUrl, a.username, a.password, episodeId, extension,
        )
    }
}

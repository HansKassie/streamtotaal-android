package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.remote.XtreamUrls
import nl.streamfix.data.remote.XtreamVodService
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.VodDetail
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.repository.VodRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class VodRepositoryImpl @Inject constructor(
    private val vodService: XtreamVodService,
    private val store: SecureCredentialStore,
) : VodRepository {

    private fun acc(): Account.Xtream? =
        store.currentActiveAccount() as? Account.Xtream

    override suspend fun getCategories(): AppResult<List<LiveCategory>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return vodService.categories(a.serverUrl, a.username, a.password)
    }

    override suspend fun getItems(categoryId: String): AppResult<List<VodItem>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return vodService.items(a.serverUrl, a.username, a.password, categoryId)
    }

    override suspend fun getDetail(vodId: String): AppResult<VodDetail> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return vodService.detail(a.serverUrl, a.username, a.password, vodId)
    }

    override fun streamUrl(vodId: String, extension: String): String? {
        val a = acc() ?: return null
        return XtreamUrls.vodStream(
            a.serverUrl, a.username, a.password, vodId, extension,
        )
    }
}

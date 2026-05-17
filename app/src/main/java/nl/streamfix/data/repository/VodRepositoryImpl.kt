package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.data.local.AdultContent
import nl.streamfix.data.local.AppSettingsStore
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
    private val appSettings: AppSettingsStore,
) : VodRepository {

    private fun acc(): Account.Xtream? =
        store.currentActiveAccount() as? Account.Xtream

    override suspend fun getCategories(): AppResult<List<LiveCategory>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        val r = vodService.categories(a.serverUrl, a.username, a.password)
        return if (r is AppResult.Success && appSettings.adultFilterActive()) {
            AppResult.Success(r.data.filterNot { AdultContent.isAdult(it.name) })
        } else {
            r
        }
    }

    override suspend fun getItems(categoryId: String): AppResult<List<VodItem>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        return vodService.items(a.serverUrl, a.username, a.password, categoryId)
    }

    override suspend fun getAllItems(): AppResult<List<VodItem>> {
        val a = acc() ?: return AppResult.Failure(AppError.Unknown)
        val r = vodService.allItems(a.serverUrl, a.username, a.password)
        if (r !is AppResult.Success || !appSettings.adultFilterActive()) return r
        val cats = vodService.categories(a.serverUrl, a.username, a.password)
        val adultIds = (cats as? AppResult.Success)?.data
            ?.filter { AdultContent.isAdult(it.name) }?.map { it.id }
            ?.toSet().orEmpty()
        return AppResult.Success(
            r.data.filterNot {
                it.categoryId in adultIds || AdultContent.isAdult(it.name)
            },
        )
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

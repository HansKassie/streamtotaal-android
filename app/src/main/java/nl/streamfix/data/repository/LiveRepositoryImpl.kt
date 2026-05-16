package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.local.db.FavoriteChannelEntity
import nl.streamfix.data.local.db.FavoriteDao
import nl.streamfix.data.remote.XtreamLiveService
import nl.streamfix.data.remote.XtreamUrls
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class LiveRepositoryImpl @Inject constructor(
    private val liveService: XtreamLiveService,
    private val store: SecureCredentialStore,
    private val favoriteDao: FavoriteDao,
) : LiveRepository {

    private fun activeXtream(): Account.Xtream? =
        store.currentActiveAccount() as? Account.Xtream

    override suspend fun getCategories(): AppResult<List<LiveCategory>> {
        val acc = activeXtream() ?: return AppResult.Failure(AppError.Unknown)
        return liveService.categories(acc.serverUrl, acc.username, acc.password)
    }

    override suspend fun getChannels(
        categoryId: String?,
    ): AppResult<List<LiveChannel>> {
        val acc = activeXtream() ?: return AppResult.Failure(AppError.Unknown)
        return liveService.channels(
            acc.serverUrl, acc.username, acc.password, categoryId,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<LiveChannel>> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                favoriteDao.observe(account.id).map { list ->
                    list.map {
                        LiveChannel(
                            id = it.channelId,
                            name = it.name,
                            logoUrl = it.logoUrl,
                            categoryId = null,
                            epgChannelId = null,
                        )
                    }
                }
            }
        }

    override suspend fun setFavorite(channel: LiveChannel, favorite: Boolean) {
        val acc = activeXtream() ?: return
        if (favorite) {
            favoriteDao.add(
                FavoriteChannelEntity(
                    accountId = acc.id,
                    channelId = channel.id,
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                ),
            )
        } else {
            favoriteDao.remove(acc.id, channel.id)
        }
    }

    override fun streamUrl(channelId: String): String? {
        val acc = activeXtream() ?: return null
        val ext = when (acc.streamFormat) {
            "ts" -> "ts"
            "m3u8" -> "m3u8"
            else -> acc.liveExtension
        }
        return XtreamUrls.liveStream(
            acc.serverUrl, acc.username, acc.password, channelId,
            extension = ext,
        )
    }
}

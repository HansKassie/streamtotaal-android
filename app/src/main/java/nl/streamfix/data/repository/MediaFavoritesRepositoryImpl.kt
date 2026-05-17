package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import nl.streamfix.data.local.AdultContent
import nl.streamfix.data.local.AppSettingsStore
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.local.db.FavoriteMediaDao
import nl.streamfix.data.local.db.FavoriteMediaEntity
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.repository.MediaFavoritesRepository

private const val TYPE_VOD = "vod"
private const val TYPE_SERIES = "series"

@Singleton
class MediaFavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoriteMediaDao,
    private val store: SecureCredentialStore,
    private val appSettings: AppSettingsStore,
) : MediaFavoritesRepository {

    private fun List<FavoriteMediaEntity>.dropAdult() = filterNot {
        appSettings.adultFilterActive() && AdultContent.isAdult(it.name)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVodFavorites(): Flow<List<VodItem>> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                dao.observe(account.id, TYPE_VOD).map { rows ->
                    rows.dropAdult().map {
                        VodItem(
                            id = it.mediaId,
                            name = it.name,
                            posterUrl = it.posterUrl,
                            categoryId = null,
                            containerExtension = null,
                        )
                    }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSeriesFavorites(): Flow<List<SeriesItem>> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                dao.observe(account.id, TYPE_SERIES).map { rows ->
                    rows.dropAdult().map {
                        SeriesItem(
                            id = it.mediaId,
                            name = it.name,
                            posterUrl = it.posterUrl,
                            categoryId = null,
                        )
                    }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun isVodFavorite(id: String): Flow<Boolean> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) flowOf(false)
            else dao.observeIsFavorite(account.id, TYPE_VOD, id)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun isSeriesFavorite(id: String): Flow<Boolean> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) flowOf(false)
            else dao.observeIsFavorite(account.id, TYPE_SERIES, id)
        }

    override suspend fun setVodFavorite(
        id: String,
        name: String,
        posterUrl: String?,
        favorite: Boolean,
    ) = set(TYPE_VOD, id, name, posterUrl, favorite)

    override suspend fun setSeriesFavorite(
        id: String,
        name: String,
        posterUrl: String?,
        favorite: Boolean,
    ) = set(TYPE_SERIES, id, name, posterUrl, favorite)

    private suspend fun set(
        type: String,
        id: String,
        name: String,
        posterUrl: String?,
        favorite: Boolean,
    ) {
        val accId = store.currentActiveAccount()?.id ?: return
        if (favorite) {
            dao.add(
                FavoriteMediaEntity(
                    accountId = accId,
                    mediaType = type,
                    mediaId = id,
                    name = name,
                    posterUrl = posterUrl,
                ),
            )
        } else {
            dao.remove(accId, type, id)
        }
    }
}

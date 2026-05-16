package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.local.db.PlaybackDao
import nl.streamfix.data.local.db.PlaybackProgressEntity
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.repository.PlaybackRepository

@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val dao: PlaybackDao,
    private val store: SecureCredentialStore,
) : PlaybackRepository {

    private fun accountId(): String? = store.currentActiveAccount()?.id

    override suspend fun getPosition(mediaId: String): Long {
        val id = accountId() ?: return 0L
        return dao.position(id, mediaId) ?: 0L
    }

    override suspend fun startWatching(item: HistoryItem) {
        val id = accountId() ?: return
        val existingPos = dao.position(id, item.mediaId) ?: 0L
        dao.upsert(
            PlaybackProgressEntity(
                accountId = id,
                mediaId = item.mediaId,
                positionMs = existingPos,
                updatedAt = System.currentTimeMillis(),
                title = item.title,
                posterUrl = item.posterUrl,
                type = item.type,
                contentId = item.contentId,
                extension = item.extension,
            ),
        )
    }

    override suspend fun savePosition(mediaId: String, positionMs: Long) {
        val id = accountId() ?: return
        if (positionMs <= 0L) return
        dao.updatePosition(id, mediaId, positionMs, System.currentTimeMillis())
    }

    override suspend fun removeFromHistory(mediaId: String) {
        val id = accountId() ?: return
        dao.deleteOne(id, mediaId)
    }

    override suspend fun clearHistory() {
        val id = accountId() ?: return
        dao.clearAll(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHistory(): Flow<List<HistoryItem>> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                dao.observeHistory(account.id).map { rows ->
                    rows.map {
                        HistoryItem(
                            mediaId = it.mediaId,
                            title = it.title,
                            posterUrl = it.posterUrl,
                            type = it.type,
                            contentId = it.contentId,
                            extension = it.extension,
                            positionMs = it.positionMs,
                        )
                    }
                }
            }
        }
}

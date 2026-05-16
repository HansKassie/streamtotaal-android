package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.local.db.PlaybackDao
import nl.streamfix.data.local.db.PlaybackProgressEntity
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

    override suspend fun savePosition(mediaId: String, positionMs: Long) {
        val id = accountId() ?: return
        if (positionMs <= 0L) return
        dao.save(
            PlaybackProgressEntity(
                accountId = id,
                mediaId = mediaId,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

package nl.streamfix.domain.repository

interface PlaybackRepository {
    /** Opgeslagen kijkpositie in ms (0 = vanaf begin). */
    suspend fun getPosition(mediaId: String): Long
    suspend fun savePosition(mediaId: String, positionMs: Long)
}

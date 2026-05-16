package nl.streamfix.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.HistoryItem

interface PlaybackRepository {
    /** Opgeslagen kijkpositie in ms (0 = vanaf begin). */
    suspend fun getPosition(mediaId: String): Long

    /** Zet/ververst metadata in de historie; behoudt bestaande positie. */
    suspend fun startWatching(item: HistoryItem)

    /** Werkt alleen de positie bij (metadata blijft). */
    suspend fun savePosition(mediaId: String, positionMs: Long)

    /** "Verder kijken"-lijst, meest recent eerst, voor de actieve provider. */
    fun observeHistory(): Flow<List<HistoryItem>>
}

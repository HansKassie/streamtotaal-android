package nl.streamfix.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.util.AppResult

interface LiveRepository {

    suspend fun getCategories(): AppResult<List<LiveCategory>>

    /** [categoryId] null = alle kanalen. */
    suspend fun getChannels(categoryId: String?): AppResult<List<LiveChannel>>

    /** Favorieten van de actieve provider; werkt bij na provider-wissel. */
    fun observeFavorites(): Flow<List<LiveChannel>>

    suspend fun setFavorite(channel: LiveChannel, favorite: Boolean)

    /** Speelbare live-stream-URL, of null als er geen actieve provider is. */
    fun streamUrl(channelId: String): String?

    /** Live-URL geforceerd als HLS (.m3u8), voor casten naar Chromecast. */
    fun streamUrlForCast(channelId: String): String?

    /** Catch-up/terugkijk-URL voor een programma, of null zonder provider. */
    fun timeshiftUrl(
        channelId: String,
        startMs: Long,
        durationMin: Int,
    ): String?
}

package nl.streamfix.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.model.VodItem

/** Favorieten voor films en series, gescheiden per actieve provider. */
interface MediaFavoritesRepository {

    fun observeVodFavorites(): Flow<List<VodItem>>

    fun observeSeriesFavorites(): Flow<List<SeriesItem>>

    fun isVodFavorite(id: String): Flow<Boolean>

    fun isSeriesFavorite(id: String): Flow<Boolean>

    suspend fun setVodFavorite(
        id: String,
        name: String,
        posterUrl: String?,
        favorite: Boolean,
    )

    suspend fun setSeriesFavorite(
        id: String,
        name: String,
        posterUrl: String?,
        favorite: Boolean,
    )
}

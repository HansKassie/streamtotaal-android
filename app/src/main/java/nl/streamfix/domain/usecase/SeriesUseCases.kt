package nl.streamfix.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.SeriesDetail
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.repository.MediaFavoritesRepository
import nl.streamfix.domain.repository.SeriesRepository
import nl.streamfix.domain.util.AppResult

class GetSeriesCategoriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
) {
    suspend operator fun invoke(): AppResult<List<LiveCategory>> =
        repository.getCategories()
}

class GetSeriesItemsUseCase @Inject constructor(
    private val repository: SeriesRepository,
) {
    suspend operator fun invoke(categoryId: String): AppResult<List<SeriesItem>> =
        repository.getItems(categoryId)
}

class GetAllSeriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
) {
    suspend operator fun invoke(): AppResult<List<SeriesItem>> =
        repository.getAllItems()
}

class GetSeriesDetailUseCase @Inject constructor(
    private val repository: SeriesRepository,
) {
    suspend operator fun invoke(seriesId: String): AppResult<SeriesDetail> =
        repository.getDetail(seriesId)
}

class GetEpisodeStreamUrlUseCase @Inject constructor(
    private val repository: SeriesRepository,
) {
    operator fun invoke(episodeId: String, extension: String): String? =
        repository.episodeStreamUrl(episodeId, extension)
}

class ObserveSeriesFavoritesUseCase @Inject constructor(
    private val repository: MediaFavoritesRepository,
) {
    operator fun invoke(): Flow<List<SeriesItem>> =
        repository.observeSeriesFavorites()
}

class IsSeriesFavoriteUseCase @Inject constructor(
    private val repository: MediaFavoritesRepository,
) {
    operator fun invoke(id: String): Flow<Boolean> =
        repository.isSeriesFavorite(id)
}

class SetSeriesFavoriteUseCase @Inject constructor(
    private val repository: MediaFavoritesRepository,
) {
    suspend operator fun invoke(
        id: String,
        name: String,
        posterUrl: String?,
        favorite: Boolean,
    ) = repository.setSeriesFavorite(id, name, posterUrl, favorite)
}

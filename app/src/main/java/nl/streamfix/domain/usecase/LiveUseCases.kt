package nl.streamfix.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.util.AppResult

class GetLiveCategoriesUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    suspend operator fun invoke(): AppResult<List<LiveCategory>> =
        repository.getCategories()
}

class GetLiveChannelsUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    suspend operator fun invoke(categoryId: String?): AppResult<List<LiveChannel>> =
        repository.getChannels(categoryId)
}

class ObserveFavoritesUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    operator fun invoke(): Flow<List<LiveChannel>> = repository.observeFavorites()
}

class SetFavoriteUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    suspend operator fun invoke(channel: LiveChannel, favorite: Boolean) =
        repository.setFavorite(channel, favorite)
}

class GetStreamUrlUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    operator fun invoke(channelId: String): String? =
        repository.streamUrl(channelId)
}

class GetLiveCastUrlUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    operator fun invoke(channelId: String): String? =
        repository.streamUrlForCast(channelId)
}

class GetTimeshiftUrlUseCase @Inject constructor(
    private val repository: LiveRepository,
) {
    operator fun invoke(
        channelId: String,
        startMs: Long,
        durationMin: Int,
    ): String? = repository.timeshiftUrl(channelId, startMs, durationMin)
}

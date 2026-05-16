package nl.streamfix.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.VodDetail
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.repository.PlaybackRepository
import nl.streamfix.domain.repository.VodRepository
import nl.streamfix.domain.util.AppResult

class GetVodCategoriesUseCase @Inject constructor(
    private val repository: VodRepository,
) {
    suspend operator fun invoke(): AppResult<List<LiveCategory>> =
        repository.getCategories()
}

class GetVodItemsUseCase @Inject constructor(
    private val repository: VodRepository,
) {
    suspend operator fun invoke(categoryId: String): AppResult<List<VodItem>> =
        repository.getItems(categoryId)
}

class GetVodDetailUseCase @Inject constructor(
    private val repository: VodRepository,
) {
    suspend operator fun invoke(vodId: String): AppResult<VodDetail> =
        repository.getDetail(vodId)
}

class GetVodStreamUrlUseCase @Inject constructor(
    private val repository: VodRepository,
) {
    operator fun invoke(vodId: String, extension: String): String? =
        repository.streamUrl(vodId, extension)
}

class GetResumePositionUseCase @Inject constructor(
    private val repository: PlaybackRepository,
) {
    suspend operator fun invoke(mediaId: String): Long =
        repository.getPosition(mediaId)
}

class SaveResumePositionUseCase @Inject constructor(
    private val repository: PlaybackRepository,
) {
    suspend operator fun invoke(mediaId: String, positionMs: Long) =
        repository.savePosition(mediaId, positionMs)
}

class StartWatchingUseCase @Inject constructor(
    private val repository: PlaybackRepository,
) {
    suspend operator fun invoke(item: HistoryItem) =
        repository.startWatching(item)
}

class ObserveHistoryUseCase @Inject constructor(
    private val repository: PlaybackRepository,
) {
    operator fun invoke(): Flow<List<HistoryItem>> = repository.observeHistory()
}

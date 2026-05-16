package nl.streamfix.domain.usecase

import javax.inject.Inject
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.repository.EpgRepository
import nl.streamfix.domain.util.AppResult

class GetChannelEpgUseCase @Inject constructor(
    private val repository: EpgRepository,
) {
    suspend operator fun invoke(streamId: String): AppResult<List<EpgProgramme>> =
        repository.getEpg(streamId)
}

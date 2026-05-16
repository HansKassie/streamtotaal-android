package nl.streamfix.domain.usecase

import javax.inject.Inject
import nl.streamfix.domain.model.UpdateInfo
import nl.streamfix.domain.repository.UpdateRepository

class CheckForUpdateUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): UpdateInfo? = repository.checkForUpdate()
}

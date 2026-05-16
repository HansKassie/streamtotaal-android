package nl.streamfix.domain.usecase

import javax.inject.Inject
import nl.streamfix.domain.model.Provider
import nl.streamfix.domain.repository.ProviderRepository

class GetProvidersUseCase @Inject constructor(
    private val repository: ProviderRepository,
) {
    suspend operator fun invoke(): List<Provider> = repository.getProviders()
}

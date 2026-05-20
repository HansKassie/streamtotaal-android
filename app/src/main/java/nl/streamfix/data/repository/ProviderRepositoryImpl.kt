package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.data.remote.XtreamApi
import nl.streamfix.domain.model.Provider
import nl.streamfix.domain.repository.ProviderRepository

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val api: XtreamApi,
) : ProviderRepository {

    override suspend fun getProviders(): List<Provider> {
        val remoteUrl = ProviderCatalog.REMOTE_CATALOG_URL
        if (remoteUrl.isNotBlank()) {
            runCatching {
                api.getProviderCatalog(remoteUrl).mapNotNull { dto ->
                    val name = dto.name?.takeIf { it.isNotBlank() }
                    val url = dto.url?.takeIf { it.isNotBlank() }
                    if (name != null && url != null) Provider(name, url) else null
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ProviderCatalog.BUNDLED
    }
}

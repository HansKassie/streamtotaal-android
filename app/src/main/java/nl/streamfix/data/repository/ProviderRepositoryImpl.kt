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
        if (REMOTE_CATALOG_URL.isNotBlank()) {
            runCatching {
                api.getProviderCatalog(REMOTE_CATALOG_URL).mapNotNull { dto ->
                    val name = dto.name?.takeIf { it.isNotBlank() }
                    val url = dto.url?.takeIf { it.isNotBlank() }
                    if (name != null && url != null) Provider(name, url) else null
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return BUNDLED
    }

    private companion object {
        // Vul dit later met een door jou gehoste JSON-URL
        // ([{"name":"...","url":"..."}, ...]) om providers te wijzigen
        // zonder app-update. Leeg = alleen de ingebouwde lijst.
        const val REMOTE_CATALOG_URL = ""

        val BUNDLED = listOf(
            Provider("PROMAX", "http://line.smarttelevision.xyz"),
            Provider("DIAMOND", "http://kassiee.clear-ocean.link"),
            Provider("Nettv", "http://sales.vivotv.vip"),
            Provider("TIVIONE", "http://line.tivi-ott.net"),
        )
    }
}

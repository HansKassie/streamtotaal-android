package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.BuildConfig
import nl.streamfix.data.remote.XtreamApi
import nl.streamfix.domain.model.UpdateInfo
import nl.streamfix.domain.repository.UpdateRepository

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    private val api: XtreamApi,
) : UpdateRepository {

    override suspend fun checkForUpdate(): UpdateInfo? {
        if (UPDATE_MANIFEST_URL.isBlank()) return null
        return runCatching {
            val m = api.getUpdateManifest(UPDATE_MANIFEST_URL)
            val remote = m.versionCodeValue ?: return null
            val apk = m.apkUrl
                ?.takeIf { it.isNotBlank() }
                ?.takeIf {
                    it.startsWith("https://") &&
                        android.net.Uri.parse(it).host == UPDATE_HOST
                } ?: return null
            if (remote <= BuildConfig.VERSION_CODE) return null
            val mandatory = m.forceUpdateValue ||
                (m.minSupportedValue?.let { BuildConfig.VERSION_CODE < it } ?: false)
            UpdateInfo(
                versionName = m.versionName ?: "nieuw",
                apkUrl = apk,
                releaseNotes = m.releaseNotes?.takeIf { it.isNotBlank() }
                    ?: "Nieuwe versie beschikbaar.",
                mandatory = mandatory,
                // Alleen een geldig 64-teken hex-veld doorgeven; een typfout
                // in version.json mag de update niet onmogelijk maken.
                sha256 = m.sha256?.trim()
                    ?.takeIf { it.matches(Regex("(?i)[0-9a-f]{64}")) },
            )
        }.getOrNull()
    }

    private companion object {
        // Het manifest in de Cloudflare R2-bucket. Vervang dat object om een
        // update uit te rollen. Leeg maken = updatecheck uit.
        //
        // Staat per flavor in build.gradle.kts en niet hier, want sideload en
        // reseller delen dit bestand maar hebben elk een eigen kanaal. Wezen
        // die naar hetzelfde manifest, dan zou de reseller-uitvoering bij de
        // volgende update onze volledige app installeren.
        val UPDATE_HOST: String = BuildConfig.UPDATE_HOST
        val UPDATE_MANIFEST_URL: String = BuildConfig.UPDATE_MANIFEST_URL
    }
}

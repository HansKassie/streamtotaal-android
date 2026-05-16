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
            val apk = m.apkUrl?.takeIf { it.isNotBlank() } ?: return null
            if (remote <= BuildConfig.VERSION_CODE) return null
            val mandatory = m.forceUpdateValue ||
                (m.minSupportedValue?.let { BuildConfig.VERSION_CODE < it } ?: false)
            UpdateInfo(
                versionName = m.versionName ?: "nieuw",
                apkUrl = apk,
                releaseNotes = m.releaseNotes?.takeIf { it.isNotBlank() }
                    ?: "Nieuwe versie beschikbaar.",
                mandatory = mandatory,
            )
        }.getOrNull()
    }

    private companion object {
        // version.json in de repo (branch main). Bewerk + push om een
        // update uit te rollen. Leeg maken = updatecheck uit.
        const val UPDATE_MANIFEST_URL =
            "https://raw.githubusercontent.com/HansKassie/" +
                "streamtotaal-android/main/version.json"
    }
}

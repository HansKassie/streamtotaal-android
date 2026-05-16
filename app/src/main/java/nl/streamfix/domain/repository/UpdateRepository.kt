package nl.streamfix.domain.repository

import nl.streamfix.domain.model.UpdateInfo

interface UpdateRepository {
    /** Nieuwere versie beschikbaar? Null = geen update of niet ingesteld. */
    suspend fun checkForUpdate(): UpdateInfo?
}

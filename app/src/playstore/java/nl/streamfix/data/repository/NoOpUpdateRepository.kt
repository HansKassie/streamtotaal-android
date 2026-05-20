package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.domain.model.UpdateInfo
import nl.streamfix.domain.repository.UpdateRepository

/**
 * Play Store-flavor: zelf-updaten is verboden door Google Play-beleid
 * (Device and Network Abuse). De repository geeft altijd geen update,
 * zodat alle bestaande update-aanroepers stilletjes niets doen.
 */
@Singleton
class NoOpUpdateRepository @Inject constructor() : UpdateRepository {
    override suspend fun checkForUpdate(): UpdateInfo? = null
}

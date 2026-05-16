package nl.streamfix.domain.repository

import nl.streamfix.domain.model.Provider

interface ProviderRepository {
    /** Ingebouwde lijst, vervangen door de online lijst als die bereikbaar is. */
    suspend fun getProviders(): List<Provider>
}

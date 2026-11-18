package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.domain.repository.AccountMigrations
import nl.streamfix.domain.util.NetworkNames

/**
 * Zet opgeslagen accounts om naar de neutrale netwerknamen.
 *
 * De naam wordt bij het inloggen eenmalig opgeslagen, dus zonder dit zien
 * klanten die al een provider hadden toegevoegd nog de oude naam van hun
 * netwerk, terwijl het klantenportaal over letters praat.
 *
 * Draait bij elke start, maar schrijft alleen als er iets verandert.
 */
@Singleton
class NetworkNameMigration @Inject constructor(
    private val store: SecureCredentialStore,
) : AccountMigrations {

    override suspend fun run() = withContext(Dispatchers.IO) {
        store.renameAccounts { account ->
            NetworkNames.forServerUrl(
                account.serverUrl,
                ProviderCatalog.NETWORK_NAME_BY_HOST,
            )
        }
    }
}

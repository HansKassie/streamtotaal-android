package nl.streamfix.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.util.AppResult

interface AuthRepository {

    /** Valideert via player_api.php en slaat bij succes het account versleuteld op. */
    suspend fun loginWithXtream(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream>

    /** Het actieve account, of null als er niemand ingelogd is. */
    fun observeActiveAccount(): Flow<Account?>

    suspend fun getActiveAccount(): Account?

    /** Alle opgeslagen providers (voor wisselen). */
    suspend fun getAccounts(): List<Account>

    /** Wisselt naar een reeds opgeslagen provider. */
    suspend fun switchActiveAccount(id: String)

    /** Verwijdert één opgeslagen provider. */
    suspend fun removeAccount(id: String)

    /** Zet het live-formaat ("auto"/"ts"/"m3u8") voor het actieve account. */
    suspend fun setStreamFormat(format: String)

    /** Optionele account-info uit player_api.php, of null als ophalen faalt. */
    suspend fun getAccountInfo(): AccountInfo?

    /**
     * Test het actieve account live tegen de provider (server bereikbaar,
     * inlog geldig, abonnement actief). Success = in orde; Failure bevat de
     * reden (verlopen, onbereikbaar, foute inlog, ...).
     */
    suspend fun verifyActiveAccount(): AppResult<Unit>

    /** Wist alle credentials en lokale gegevens (briefing: logout wist alles). */
    suspend fun logout()
}

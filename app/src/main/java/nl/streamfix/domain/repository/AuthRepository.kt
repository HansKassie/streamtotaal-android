package nl.streamfix.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.util.AppResult

interface AuthRepository {

    /** Valideert via player_api.php en slaat bij succes het account versleuteld op. */
    suspend fun loginWithXtream(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream>

    /** Het actieve account, of null als er niemand ingelogd is. */
    fun observeActiveAccount(): Flow<Account?>

    suspend fun getActiveAccount(): Account?

    /** Optionele account-info uit player_api.php, of null als ophalen faalt. */
    suspend fun getAccountInfo(): AccountInfo?

    /** Wist alle credentials en lokale gegevens (briefing: logout wist alles). */
    suspend fun logout()
}

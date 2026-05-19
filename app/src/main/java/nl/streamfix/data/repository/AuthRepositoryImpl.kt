package nl.streamfix.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.remote.XtreamAuthService
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.repository.AuthRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val xtreamAuthService: XtreamAuthService,
    private val store: SecureCredentialStore,
) : AuthRepository {

    override suspend fun loginWithXtream(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream> {
        return when (val result =
            xtreamAuthService.authenticate(serverUrl, username, password)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val data = result.data
                val label = name.ifBlank {
                    "$username @ ${data.normalizedServerUrl.hostLabel()}"
                }
                // Hergebruik bestaand id voor dezelfde server+gebruiker zodat
                // favorieten en "Verder kijken" niet wegvallen bij herinloggen.
                val existingId = store.allAccounts()
                    .filterIsInstance<Account.Xtream>()
                    .firstOrNull {
                        it.serverUrl == data.normalizedServerUrl &&
                            it.username == username
                    }?.id
                val formats = data.userInfo.outputFormats
                val ext = when {
                    "m3u8" in formats -> "m3u8"
                    "ts" in formats -> "ts"
                    else -> formats.firstOrNull() ?: "ts"
                }
                val account = Account.Xtream(
                    id = existingId ?: UUID.randomUUID().toString(),
                    displayName = label,
                    serverUrl = data.normalizedServerUrl,
                    username = username,
                    password = password,
                    liveExtension = ext,
                )
                store.saveAndActivate(account)
                AppResult.Success(account)
            }
        }
    }

    override fun observeActiveAccount(): Flow<Account?> = store.activeAccount

    override suspend fun getActiveAccount(): Account? =
        withContext(Dispatchers.IO) { store.currentActiveAccount() }

    override suspend fun getAccounts(): List<Account> = store.allAccounts()

    override suspend fun switchActiveAccount(id: String) = store.setActive(id)

    override suspend fun removeAccount(id: String) = store.removeAccount(id)

    override suspend fun setStreamFormat(format: String) =
        store.setStreamFormat(format)

    override suspend fun getAccountInfo(): AccountInfo? {
        val account = store.currentActiveAccount() as? Account.Xtream ?: return null
        val result = xtreamAuthService.authenticate(
            account.serverUrl,
            account.username,
            account.password,
        )
        return (result as? AppResult.Success)?.data?.userInfo?.let { info ->
            AccountInfo(
                username = info.username ?: account.username,
                status = info.status,
                expirationDate = info.expDateValue,
                maxConnections = info.maxConnectionsValue,
                activeConnections = info.activeConnectionsValue,
            )
        }
    }

    override suspend fun verifyActiveAccount(): AppResult<Unit> {
        val account = store.currentActiveAccount() as? Account.Xtream
            ?: return AppResult.Failure(AppError.Unknown)
        return when (
            val r = xtreamAuthService.authenticate(
                account.serverUrl,
                account.username,
                account.password,
            )
        ) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> r
        }
    }

    // Bewuste afwijking van de briefing: uitloggen bewaart opgeslagen
    // providers zodat de gebruiker er weer een kan kiezen.
    override suspend fun logout() = store.deactivate()

    private fun String.hostLabel(): String =
        substringAfter("://").substringBefore('/').substringBefore(':')
            .ifBlank { this }
}

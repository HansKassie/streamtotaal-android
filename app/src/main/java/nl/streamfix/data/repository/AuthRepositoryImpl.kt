package nl.streamfix.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.remote.XtreamAuthService
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.repository.AuthRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val xtreamAuthService: XtreamAuthService,
    private val store: SecureCredentialStore,
) : AuthRepository {

    override suspend fun loginWithXtream(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream> {
        return when (val result =
            xtreamAuthService.authenticate(serverUrl, username, password)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val data = result.data
                val account = Account.Xtream(
                    id = UUID.randomUUID().toString(),
                    displayName = "$username @ ${data.normalizedServerUrl.hostLabel()}",
                    serverUrl = data.normalizedServerUrl,
                    username = username,
                    password = password,
                )
                store.saveAndActivate(account)
                AppResult.Success(account)
            }
        }
    }

    override fun observeActiveAccount(): Flow<Account?> = store.activeAccount

    override suspend fun getActiveAccount(): Account? = store.currentActiveAccount()

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
                expirationDate = info.expDate,
                maxConnections = info.maxConnections,
                activeConnections = info.activeConnections,
            )
        }
    }

    override suspend fun logout() = store.clear()

    private fun String.hostLabel(): String =
        substringAfter("://").substringBefore('/').substringBefore(':')
            .ifBlank { this }
}

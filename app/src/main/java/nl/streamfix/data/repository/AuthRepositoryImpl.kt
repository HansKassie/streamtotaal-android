package nl.streamfix.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.remote.M3uValidator
import nl.streamfix.data.remote.XtreamAuthService
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.model.M3uSource
import nl.streamfix.domain.repository.AuthRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val xtreamAuthService: XtreamAuthService,
    private val m3uValidator: M3uValidator,
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

    override suspend fun loginWithM3u(
        displayName: String,
        source: M3uSource,
    ): AppResult<Account.M3u> {
        return when (val result = m3uValidator.validate(source)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val name = displayName.ifBlank {
                    when (source) {
                        is M3uSource.Url -> source.url.hostLabel()
                        is M3uSource.LocalFile -> "M3U-bestand"
                    }
                }
                val account = Account.M3u(
                    id = UUID.randomUUID().toString(),
                    displayName = name,
                    source = source,
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

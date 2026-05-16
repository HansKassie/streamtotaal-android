package nl.streamfix.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.repository.AuthRepository
import nl.streamfix.domain.util.AppResult

class LoginWithXtreamUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream> =
        repository.loginWithXtream(
            name.trim(),
            serverUrl.trim(),
            username.trim(),
            password,
        )
}

class GetAccountsUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): List<Account> = repository.getAccounts()
}

class SwitchAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(id: String) = repository.switchActiveAccount(id)
}

class RemoveAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(id: String) = repository.removeAccount(id)
}

class SetStreamFormatUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(format: String) =
        repository.setStreamFormat(format)
}

class GetActiveAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<Account?> = repository.observeActiveAccount()
    suspend fun once(): Account? = repository.getActiveAccount()
}

class GetAccountInfoUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AccountInfo? = repository.getAccountInfo()
}

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() = repository.logout()
}

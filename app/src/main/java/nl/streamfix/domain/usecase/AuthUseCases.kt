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
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream> =
        repository.loginWithXtream(serverUrl.trim(), username.trim(), password)
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

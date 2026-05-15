package nl.streamfix.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.usecase.GetAccountInfoUseCase
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.LogoutUseCase

data class MainState(
    val account: Account? = null,
    val accountInfo: AccountInfo? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getActiveAccount: GetActiveAccountUseCase,
    private val getAccountInfo: GetAccountInfoUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = getActiveAccount.once()
            _state.update { it.copy(account = account) }
            if (account is Account.Xtream) {
                val info = runCatching { getAccountInfo() }.getOrNull()
                _state.update { it.copy(accountInfo = info) }
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }
}

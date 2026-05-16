package nl.streamfix.ui.screens.welcome

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
import nl.streamfix.domain.usecase.GetAccountsUseCase
import nl.streamfix.domain.usecase.RemoveAccountUseCase
import nl.streamfix.domain.usecase.SwitchAccountUseCase

data class WelcomeState(
    val accounts: List<Account> = emptyList(),
    val chosen: Boolean = false,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val getAccounts: GetAccountsUseCase,
    private val switchAccount: SwitchAccountUseCase,
    private val removeAccount: RemoveAccountUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(accounts = getAccounts()) }
        }
    }

    fun onPickProvider(id: String) {
        viewModelScope.launch {
            switchAccount(id)
            _state.update { it.copy(chosen = true) }
        }
    }

    fun onRemoveProvider(id: String) {
        viewModelScope.launch {
            removeAccount(id)
            _state.update { it.copy(accounts = getAccounts()) }
        }
    }
}

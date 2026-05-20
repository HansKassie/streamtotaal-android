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
import nl.streamfix.data.local.AdultState
import nl.streamfix.data.local.AppSettingsStore
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.usecase.GetAccountInfoUseCase
import nl.streamfix.domain.usecase.GetAccountsUseCase
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.LogoutUseCase
import nl.streamfix.domain.usecase.RemoveAccountUseCase
import nl.streamfix.domain.usecase.SetStreamFormatUseCase
import nl.streamfix.domain.usecase.SwitchAccountUseCase

data class MainState(
    val account: Account? = null,
    val accountInfo: AccountInfo? = null,
    val accounts: List<Account> = emptyList(),
    val loggedOut: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getActiveAccount: GetActiveAccountUseCase,
    private val getAccountInfo: GetAccountInfoUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val switchAccount: SwitchAccountUseCase,
    private val removeAccount: RemoveAccountUseCase,
    private val setStreamFormat: SetStreamFormatUseCase,
    private val logout: LogoutUseCase,
    private val appSettings: AppSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    val adultState: StateFlow<AdultState> = appSettings.adultState

    val tvMode: StateFlow<String> = appSettings.tvMode

    fun onSetTvMode(mode: String) = appSettings.setTvMode(mode)

    val startupTab: StateFlow<String> = appSettings.startupTab

    fun onSetStartupTab(value: String) = appSettings.setStartupTab(value)

    fun onSetAdultPin(pin: String) = appSettings.setPin(pin)

    /** True bij juiste pincode (sessie ontgrendeld). */
    fun onUnlockAdult(pin: String): Boolean = appSettings.unlock(pin)

    fun onHideAdult() = appSettings.hideAgain()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val account = getActiveAccount.once()
            val accounts = getAccounts()
            _state.update {
                it.copy(account = account, accounts = accounts, accountInfo = null)
            }
            if (account is Account.Xtream) {
                val info = runCatching { getAccountInfo() }.getOrNull()
                _state.update { it.copy(accountInfo = info) }
            }
        }
    }

    fun onSwitchProvider(id: String) {
        viewModelScope.launch {
            switchAccount(id)
            reload()
        }
    }

    fun onRemoveProvider(id: String) {
        viewModelScope.launch {
            val wasActive = _state.value.account?.id == id
            removeAccount(id)
            if (wasActive) {
                // Actieve provider weg: terug naar het keuzescherm.
                _state.update { it.copy(loggedOut = true) }
            } else {
                reload()
            }
        }
    }

    fun onSetStreamFormat(format: String) {
        viewModelScope.launch {
            setStreamFormat(format)
            reload()
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }
}

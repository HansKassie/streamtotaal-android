package nl.streamfix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.streamfix.domain.repository.AccountMigrations
import nl.streamfix.domain.usecase.GetActiveAccountUseCase

enum class RootState { Loading, LoggedOut, LoggedIn }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val getActiveAccount: GetActiveAccountUseCase,
    private val accountMigrations: AccountMigrations,
) : ViewModel() {

    private val _state = MutableStateFlow(RootState.Loading)
    val state: StateFlow<RootState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Eerst omzetten, dan pas het account uitlezen: anders toont het
            // eerste scherm nog de oude naam tot de volgende start.
            accountMigrations.run()
            _state.value = if (getActiveAccount.once() != null) {
                RootState.LoggedIn
            } else {
                RootState.LoggedOut
            }
        }
    }
}

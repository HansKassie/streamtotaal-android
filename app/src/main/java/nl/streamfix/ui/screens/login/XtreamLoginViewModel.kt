package nl.streamfix.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.Provider
import nl.streamfix.domain.usecase.GetProvidersUseCase
import nl.streamfix.domain.usecase.LoginWithXtreamUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

data class XtreamLoginState(
    val providers: List<Provider> = emptyList(),
    val selectedProvider: Provider? = null,
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = selectedProvider != null && username.isNotBlank() &&
            password.isNotBlank() && !isLoading
}

@HiltViewModel
class XtreamLoginViewModel @Inject constructor(
    private val getProviders: GetProvidersUseCase,
    private val loginWithXtream: LoginWithXtreamUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(XtreamLoginState())
    val state: StateFlow<XtreamLoginState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val list = getProviders()
            _state.update {
                it.copy(providers = list, selectedProvider = list.firstOrNull())
            }
        }
    }

    fun onSelectProvider(provider: Provider) =
        _state.update { it.copy(selectedProvider = provider, errorMessage = null) }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _state.value
        val provider = current.selectedProvider ?: return
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = loginWithXtream(
                    provider.name,
                    provider.url,
                    current.username,
                    current.password,
                )
            ) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, loggedIn = true) }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.uiMessage(),
                        )
                    }
            }
        }
    }
}

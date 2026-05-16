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
import nl.streamfix.domain.usecase.LoginWithXtreamUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

const val URL_SCHEME = "http://"

data class XtreamLoginState(
    val name: String = "",
    val serverUrl: String = URL_SCHEME,
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
) {
    val host: String get() = serverUrl.removePrefix(URL_SCHEME)

    val canSubmit: Boolean
        get() = host.isNotBlank() && username.isNotBlank() &&
            password.isNotBlank() && !isLoading
}

@HiltViewModel
class XtreamLoginViewModel @Inject constructor(
    private val loginWithXtream: LoginWithXtreamUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(XtreamLoginState())
    val state: StateFlow<XtreamLoginState> = _state.asStateFlow()

    fun onNameChange(value: String) =
        _state.update { it.copy(name = value, errorMessage = null) }

    // Houd "http://" altijd vast vooraan; de klant bewerkt alleen de host.
    fun onServerUrlChange(value: String) {
        val schemeRegex = Regex("(?i)^https?://")
        var host = value.trimStart()
        while (true) {
            val stripped = host.replaceFirst(schemeRegex, "")
            if (stripped == host) break
            host = stripped
        }
        // Backspace in het vaste prefix levert een afkapping van "http://"
        // op (bv. "http:/"): dan host leeg houden zodat het prefix blijft.
        if (value.length < URL_SCHEME.length &&
            URL_SCHEME.startsWith(value, ignoreCase = true)
        ) {
            host = ""
        }
        _state.update { it.copy(serverUrl = URL_SCHEME + host, errorMessage = null) }
    }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = loginWithXtream(
                    current.name,
                    current.serverUrl,
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

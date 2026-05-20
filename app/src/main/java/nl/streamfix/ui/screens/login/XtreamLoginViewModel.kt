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

/**
 * Pre-fill voor het Server-URL-veld bij "Eigen provider". Wordt door
 * [XtreamUrls.normalizeServerUrl] later netjes genormaliseerd; expliciet
 * "https://" intikken blijft daardoor behouden.
 */
const val DEFAULT_SERVER_URL_PREFIX = "http://"

/** True = het ingevoerde server-URL-veld bevat meer dan alleen een scheme. */
fun isValidServerUrl(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    val withoutScheme = trimmed.replace(Regex("(?i)^[a-z][a-z0-9+.-]*://"), "")
    return withoutScheme.trimEnd('/').isNotBlank()
}

data class XtreamLoginState(
    val providers: List<Provider> = emptyList(),
    val selectedProvider: Provider? = null,
    val useCustomProvider: Boolean = false,
    val customName: String = "",
    val customUrl: String = DEFAULT_SERVER_URL_PREFIX,
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() {
            if (isLoading || username.isBlank() || password.isBlank()) return false
            return if (useCustomProvider) {
                customName.isNotBlank() && isValidServerUrl(customUrl)
            } else {
                selectedProvider != null
            }
        }
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
                it.copy(
                    providers = list,
                    selectedProvider = list.firstOrNull(),
                    // Geen presets (playstore-flavor) = direct eigen-provider-flow.
                    useCustomProvider = list.isEmpty(),
                )
            }
        }
    }

    fun onSelectProvider(provider: Provider) =
        _state.update {
            it.copy(
                selectedProvider = provider,
                useCustomProvider = false,
                errorMessage = null,
            )
        }

    fun onSelectCustomProvider() =
        _state.update { it.copy(useCustomProvider = true, errorMessage = null) }

    fun onCustomNameChange(value: String) =
        _state.update { it.copy(customName = value, errorMessage = null) }

    fun onCustomUrlChange(value: String) =
        _state.update { it.copy(customUrl = value, errorMessage = null) }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val (name, url) = if (current.useCustomProvider) {
            current.customName.trim() to current.customUrl.trim()
        } else {
            val provider = current.selectedProvider ?: return
            provider.name to provider.url
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = loginWithXtream(
                    name,
                    url,
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

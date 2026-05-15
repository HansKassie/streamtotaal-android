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
import nl.streamfix.domain.model.M3uSource
import nl.streamfix.domain.usecase.LoginWithM3uUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

data class M3uLoginState(
    val displayName: String = "",
    val urlText: String = "",
    val pickedFileUri: String? = null,
    val pickedFileName: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isLoading && (urlText.isNotBlank() || pickedFileUri != null)
}

@HiltViewModel
class M3uLoginViewModel @Inject constructor(
    private val loginWithM3u: LoginWithM3uUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(M3uLoginState())
    val state: StateFlow<M3uLoginState> = _state.asStateFlow()

    fun onDisplayNameChange(value: String) =
        _state.update { it.copy(displayName = value, errorMessage = null) }

    fun onUrlChange(value: String) =
        _state.update {
            it.copy(
                urlText = value,
                pickedFileUri = null,
                pickedFileName = null,
                errorMessage = null,
            )
        }

    fun onFilePicked(uri: String, name: String?) =
        _state.update {
            it.copy(
                pickedFileUri = uri,
                pickedFileName = name,
                urlText = "",
                errorMessage = null,
            )
        }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val source = current.pickedFileUri?.let { M3uSource.LocalFile(it) }
            ?: M3uSource.Url(current.urlText)

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = loginWithM3u(current.displayName, source)) {
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

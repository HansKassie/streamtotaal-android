package nl.streamfix.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.usecase.GetResumePositionUseCase
import nl.streamfix.domain.usecase.SaveResumePositionUseCase
import nl.streamfix.ui.navigation.Routes

data class PlaybackUiState(
    val title: String = "",
    val streamUrl: String = "",
    val startPositionMs: Long = 0L,
    val ready: Boolean = false,
)

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getResumePosition: GetResumePositionUseCase,
    private val saveResumePosition: SaveResumePositionUseCase,
) : ViewModel() {

    private val mediaId: String =
        savedStateHandle.get<String>(Routes.PLAYBACK_ARG_MEDIA).orEmpty()

    private val _state = MutableStateFlow(
        PlaybackUiState(
            title = savedStateHandle.get<String>(Routes.PLAYBACK_ARG_TITLE)
                .orEmpty(),
            streamUrl = savedStateHandle.get<String>(Routes.PLAYBACK_ARG_URL)
                .orEmpty(),
        ),
    )
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pos = getResumePosition(mediaId)
            _state.update { it.copy(startPositionMs = pos, ready = true) }
        }
    }

    fun savePosition(positionMs: Long) {
        viewModelScope.launch { saveResumePosition(mediaId, positionMs) }
    }
}

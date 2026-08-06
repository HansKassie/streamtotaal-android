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
import nl.streamfix.domain.usecase.GetEpisodeStreamUrlUseCase
import nl.streamfix.domain.usecase.GetResumePositionUseCase
import nl.streamfix.domain.usecase.GetVodStreamUrlUseCase
import nl.streamfix.domain.usecase.SaveResumePositionUseCase
import nl.streamfix.ui.navigation.Routes

data class PlaybackUiState(
    val title: String = "",
    val streamUrl: String = "",
    val startPositionMs: Long = 0L,
    val ready: Boolean = false,
    /** Bron kon niet worden opgebouwd (geen provider of onbekend type). */
    val sourceUnavailable: Boolean = false,
)

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getResumePosition: GetResumePositionUseCase,
    private val saveResumePosition: SaveResumePositionUseCase,
    private val getVodStreamUrl: GetVodStreamUrlUseCase,
    private val getEpisodeStreamUrl: GetEpisodeStreamUrlUseCase,
) : ViewModel() {

    private val mediaId: String =
        savedStateHandle.get<String>(Routes.PLAYBACK_ARG_MEDIA).orEmpty()
    private val type: String =
        savedStateHandle.get<String>(Routes.PLAYBACK_ARG_TYPE).orEmpty()
    private val contentId: String =
        savedStateHandle.get<String>(Routes.PLAYBACK_ARG_CONTENT).orEmpty()
    private val extension: String =
        savedStateHandle.get<String>(Routes.PLAYBACK_ARG_EXT).orEmpty()

    // Tijdelijk: alleen catch-up geeft nog een kant-en-klare URL door.
    private val legacyUrl: String =
        savedStateHandle.get<String>(Routes.PLAYBACK_ARG_URL).orEmpty()

    /**
     * De stream-URL wordt hier opgebouwd uit de brongegevens uit de route,
     * zodat de inloggegevens niet in de navigatiestate terechtkomen. De
     * legacy-parameter wordt alleen nog gebruikt door catch-up, dat nog
     * omgezet moet worden.
     */
    private fun buildStreamUrl(): String? = when (type) {
        Routes.PLAYBACK_TYPE_VOD -> getVodStreamUrl(contentId, extension)
        Routes.PLAYBACK_TYPE_EPISODE ->
            getEpisodeStreamUrl(contentId, extension)
        else -> legacyUrl.takeIf { it.isNotBlank() }
    }

    private val _state = MutableStateFlow(
        PlaybackUiState(
            title = savedStateHandle.get<String>(Routes.PLAYBACK_ARG_TITLE)
                .orEmpty(),
        ),
    )
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val url = buildStreamUrl()
            if (url == null) {
                _state.update { it.copy(sourceUnavailable = true, ready = true) }
                return@launch
            }
            val pos = getResumePosition(mediaId)
            _state.update {
                it.copy(streamUrl = url, startPositionMs = pos, ready = true)
            }
        }
    }

    fun savePosition(positionMs: Long) {
        if (mediaId.isBlank()) return
        viewModelScope.launch { saveResumePosition(mediaId, positionMs) }
    }
}

package nl.streamfix.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.screens.live.FAVORITES_ID

data class PlayerUiState(
    val title: String = "",
    val streamUrl: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannels: GetLiveChannelsUseCase,
    private val observeFavorites: ObserveFavoritesUseCase,
    private val getStreamUrl: GetStreamUrlUseCase,
) : ViewModel() {

    private val categoryId: String =
        savedStateHandle.get<String>(Routes.PLAYER_ARG_CATEGORY).orEmpty()
    private val startChannelId: String =
        savedStateHandle.get<String>(Routes.PLAYER_ARG_CHANNEL).orEmpty()

    private var channels: List<LiveChannel> = emptyList()
    private var index: Int = 0

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            channels = loadChannels()
            index = channels.indexOfFirst { it.id == startChannelId }
                .takeIf { it >= 0 } ?: 0
            emitCurrent()
        }
    }

    private suspend fun loadChannels(): List<LiveChannel> =
        if (categoryId == FAVORITES_ID) {
            observeFavorites().first()
        } else {
            when (val r = getChannels(categoryId)) {
                is AppResult.Success -> r.data
                is AppResult.Failure -> emptyList()
            }
        }

    private fun emitCurrent() {
        val channel = channels.getOrNull(index)
        if (channel == null) {
            _state.update { it.copy(streamUrl = null) }
            return
        }
        _state.update {
            it.copy(
                title = channel.name,
                streamUrl = getStreamUrl(channel.id),
                hasPrevious = index > 0,
                hasNext = index < channels.lastIndex,
            )
        }
    }

    fun next() {
        if (index < channels.lastIndex) {
            index++
            emitCurrent()
        }
    }

    fun previous() {
        if (index > 0) {
            index--
            emitCurrent()
        }
    }
}

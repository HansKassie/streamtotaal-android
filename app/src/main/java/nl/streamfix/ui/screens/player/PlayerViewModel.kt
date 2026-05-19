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
import nl.streamfix.domain.usecase.GetLiveCastUrlUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.screens.live.FAVORITES_ID

data class PlayerUiState(
    val title: String = "",
    val streamUrl: String? = null,
    val castStreamUrl: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val channels: List<LiveChannel> = emptyList(),
    val currentChannelId: String? = null,
    val hasLast: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannels: GetLiveChannelsUseCase,
    private val observeFavorites: ObserveFavoritesUseCase,
    private val getStreamUrl: GetStreamUrlUseCase,
    private val getCastUrl: GetLiveCastUrlUseCase,
) : ViewModel() {

    private val categoryId: String =
        savedStateHandle.get<String>(Routes.PLAYER_ARG_CATEGORY).orEmpty()
    private val startChannelId: String =
        savedStateHandle.get<String>(Routes.PLAYER_ARG_CHANNEL).orEmpty()

    val guideCategoryId: String get() = categoryId.ifBlank { FAVORITES_ID }

    private var channels: List<LiveChannel> = emptyList()
    private var index: Int = 0
    private var previousIndex: Int = -1

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
            // Leeg (bijv. vanuit zoeken) = alle kanalen, anders de categorie.
            val cat = categoryId.ifBlank { null }
            when (val r = getChannels(cat)) {
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
                castStreamUrl = getCastUrl(channel.id),
                hasPrevious = index > 0,
                hasNext = index < channels.lastIndex,
                channels = channels,
                currentChannelId = channel.id,
                hasLast = previousIndex in channels.indices &&
                    previousIndex != index,
            )
        }
    }

    private fun setIndex(newIndex: Int) {
        if (newIndex in channels.indices && newIndex != index) {
            previousIndex = index
            index = newIndex
            emitCurrent()
        }
    }

    fun selectChannel(id: String) {
        setIndex(channels.indexOfFirst { it.id == id })
    }

    fun next() {
        setIndex(index + 1)
    }

    fun previous() {
        setIndex(index - 1)
    }

    fun lastChannel() {
        if (previousIndex in channels.indices && previousIndex != index) {
            val current = index
            index = previousIndex
            previousIndex = current
            emitCurrent()
        }
    }
}

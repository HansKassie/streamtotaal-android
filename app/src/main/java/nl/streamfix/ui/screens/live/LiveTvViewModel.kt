package nl.streamfix.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.SetFavoriteUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

const val FAVORITES_ID = "__favorites__"

data class LiveUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    val favoriteIds: Set<String> = emptySet(),
    private val channels: List<LiveChannel> = emptyList(),
) {
    val visibleChannels: List<LiveChannel>
        get() = if (query.isBlank()) channels
        else channels.filter { it.name.contains(query, ignoreCase = true) }
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val getCategories: GetLiveCategoriesUseCase,
    private val getChannels: GetLiveChannelsUseCase,
    private val observeFavorites: ObserveFavoritesUseCase,
    private val setFavorite: SetFavoriteUseCase,
    private val getStreamUrl: GetStreamUrlUseCase,
    private val getChannelEpg: GetChannelEpgUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val _epg = MutableStateFlow<Map<String, List<EpgProgramme>>>(emptyMap())
    /** Per kanaal-id de (gecachte) programma's; gevuld zodra een rij erom vraagt. */
    val epg: StateFlow<Map<String, List<EpgProgramme>>> = _epg.asStateFlow()

    private val epgRequested = mutableSetOf<String>()

    private var favorites: List<LiveChannel> = emptyList()

    init {
        viewModelScope.launch {
            observeFavorites().collect { favs ->
                favorites = favs
                _state.update { s ->
                    val refreshed = if (s.selectedCategoryId == FAVORITES_ID) {
                        s.copy(
                            favoriteIds = favs.map { it.id }.toSet(),
                            channels = favs,
                        )
                    } else {
                        s.copy(favoriteIds = favs.map { it.id }.toSet())
                    }
                    refreshed
                }
            }
        }
        loadCategories()
    }

    private fun loadCategories() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getCategories()) {
                is AppResult.Success -> {
                    _state.update { it.copy(isLoading = false, categories = result.data) }
                    val first = result.data.firstOrNull()?.id
                    selectCategory(first ?: FAVORITES_ID)
                }
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

    fun selectCategory(categoryId: String) {
        _state.update { it.copy(selectedCategoryId = categoryId, query = "") }
        if (categoryId == FAVORITES_ID) {
            _state.update { it.copy(channels = favorites, isLoading = false) }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getChannels(categoryId)) {
                is AppResult.Success ->
                    _state.update {
                        it.copy(isLoading = false, channels = result.data)
                    }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.uiMessage(),
                            channels = emptyList(),
                        )
                    }
            }
        }
    }

    /** Laadt EPG voor een kanaal eenmalig (lazy, vanuit de zichtbare rij). */
    fun ensureEpg(channelId: String) {
        if (!epgRequested.add(channelId)) return
        viewModelScope.launch {
            val result = getChannelEpg(channelId)
            if (result is AppResult.Success && result.data.isNotEmpty()) {
                _epg.update { it + (channelId to result.data) }
            }
            // Bewust niet opnieuw aanvragen bij leeg/fout: anders een
            // fetch-storm bij scrollen langs kanalen zonder EPG. De
            // EpgRepository-cache (TTL) regelt versheid per sessie.
        }
    }

    fun onQueryChange(value: String) =
        _state.update { it.copy(query = value) }

    fun toggleFavorite(channel: LiveChannel) {
        val isFav = _state.value.favoriteIds.contains(channel.id)
        viewModelScope.launch { setFavorite(channel, !isFav) }
    }

    fun streamUrlFor(channelId: String): String? = getStreamUrl(channelId)
}

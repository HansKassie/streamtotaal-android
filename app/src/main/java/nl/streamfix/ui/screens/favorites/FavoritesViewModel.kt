package nl.streamfix.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetLastWatchedChannelUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.SetFavoriteUseCase
import nl.streamfix.ui.pickFocusTarget

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    observeFavorites: ObserveFavoritesUseCase,
    private val setFavorite: SetFavoriteUseCase,
    private val getLastWatched: GetLastWatchedChannelUseCase,
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<LiveChannel>>(emptyList())
    val favorites: StateFlow<List<LiveChannel>> = _favorites.asStateFlow()

    /** Zie LiveTvViewModel: hoort hier en niet in rememberSaveable. */
    private var focusedChannelId: String? = null

    fun onChannelFocused(channelId: String) {
        focusedChannelId = channelId
    }

    fun focusTargetId(channels: List<LiveChannel>): String? =
        pickFocusTarget(
            channels = channels,
            lastWatchedId = getLastWatched()?.second,
            lastFocusedId = focusedChannelId,
        )

    init {
        viewModelScope.launch {
            observeFavorites().collect { favs -> _favorites.value = favs }
        }
    }

    fun removeFavorite(channel: LiveChannel) {
        viewModelScope.launch { setFavorite(channel, false) }
    }
}

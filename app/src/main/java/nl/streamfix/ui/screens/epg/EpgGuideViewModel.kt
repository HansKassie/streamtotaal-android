package nl.streamfix.ui.screens.epg

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
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetCatchupEpgUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.screens.live.FAVORITES_ID
import nl.streamfix.ui.uiMessage

data class EpgGuideState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val channels: List<LiveChannel> = emptyList(),
)

@HiltViewModel
class EpgGuideViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannels: GetLiveChannelsUseCase,
    private val observeFavorites: ObserveFavoritesUseCase,
    private val getEpgTable: GetCatchupEpgUseCase,
) : ViewModel() {

    val categoryId: String =
        savedStateHandle.get<String>(Routes.EPG_GUIDE_ARG_CAT).orEmpty()

    private val _state = MutableStateFlow(EpgGuideState())
    val state: StateFlow<EpgGuideState> = _state.asStateFlow()

    private val _epg = MutableStateFlow<Map<String, List<EpgProgramme>>>(emptyMap())
    val epg: StateFlow<Map<String, List<EpgProgramme>>> = _epg.asStateFlow()

    private val epgRequested = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            if (categoryId == FAVORITES_ID) {
                val favs = observeFavorites().first()
                _state.update {
                    it.copy(isLoading = false, channels = favs)
                }
            } else {
                when (val r = getChannels(categoryId)) {
                    is AppResult.Success ->
                        _state.update {
                            it.copy(isLoading = false, channels = r.data)
                        }
                    is AppResult.Failure ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = r.error.uiMessage(),
                            )
                        }
                }
            }
        }
    }

    /** Laadt de volledige dag-EPG voor een kanaal, eenmalig (lazy per rij). */
    fun ensureEpg(channelId: String) {
        if (!epgRequested.add(channelId)) return
        viewModelScope.launch {
            val r = getEpgTable(channelId)
            if (r is AppResult.Success && r.data.isNotEmpty()) {
                _epg.update { it + (channelId to r.data) }
            }
        }
    }
}

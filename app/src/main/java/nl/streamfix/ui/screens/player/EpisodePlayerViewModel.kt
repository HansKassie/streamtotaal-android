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
import nl.streamfix.domain.model.Episode
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.usecase.GetEpisodeStreamUrlUseCase
import nl.streamfix.domain.usecase.GetResumePositionUseCase
import nl.streamfix.domain.usecase.GetSeriesDetailUseCase
import nl.streamfix.domain.usecase.SaveResumePositionUseCase
import nl.streamfix.domain.usecase.StartWatchingUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes

data class EpisodePlayerUiState(
    val title: String = "",
    val streamUrl: String? = null,
    val startPositionMs: Long = 0L,
    val hasNext: Boolean = false,
    val mediaKey: String = "",
)

@HiltViewModel
class EpisodePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDetail: GetSeriesDetailUseCase,
    private val getEpisodeStreamUrl: GetEpisodeStreamUrlUseCase,
    private val getResumePosition: GetResumePositionUseCase,
    private val saveResumePosition: SaveResumePositionUseCase,
    private val startWatching: StartWatchingUseCase,
) : ViewModel() {

    private val seriesId: String =
        savedStateHandle.get<String>(Routes.EPISODE_ARG_SERIES).orEmpty()
    private val seasonNumber: Int =
        savedStateHandle.get<String>(Routes.EPISODE_ARG_SEASON)?.toIntOrNull()
            ?: savedStateHandle.get<Int>(Routes.EPISODE_ARG_SEASON) ?: 0
    private val startEpisodeId: String =
        savedStateHandle.get<String>(Routes.EPISODE_ARG_EPISODE).orEmpty()

    private var seriesName: String = ""
    private var seriesPoster: String? = null
    private var episodes: List<Episode> = emptyList()
    private var index: Int = 0

    private val _state = MutableStateFlow(EpisodePlayerUiState())
    val state: StateFlow<EpisodePlayerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = getDetail(seriesId)) {
                is AppResult.Success -> {
                    seriesName = r.data.name
                    seriesPoster = r.data.posterUrl
                    episodes = r.data.seasons
                        .find { it.number == seasonNumber }?.episodes.orEmpty()
                    index = episodes.indexOfFirst { it.id == startEpisodeId }
                        .takeIf { it >= 0 } ?: 0
                    emitCurrent()
                }
                is AppResult.Failure ->
                    _state.update { it.copy(streamUrl = null) }
            }
        }
    }

    private fun emitCurrent() {
        val ep = episodes.getOrNull(index) ?: return
        val mediaId = "ep:${ep.id}"
        viewModelScope.launch {
            val pos = getResumePosition(mediaId)
            startWatching(
                HistoryItem(
                    mediaId = mediaId,
                    title = "$seriesName S${ep.seasonNumber}E${ep.episodeNumber}",
                    posterUrl = seriesPoster,
                    type = "ep",
                    contentId = ep.id,
                    extension = ep.containerExtension,
                    positionMs = 0L,
                ),
            )
            _state.update {
                it.copy(
                    title = "$seriesName S${ep.seasonNumber}E${ep.episodeNumber}",
                    streamUrl = getEpisodeStreamUrl(ep.id, ep.containerExtension),
                    startPositionMs = pos,
                    hasNext = index < episodes.lastIndex,
                    mediaKey = mediaId,
                )
            }
        }
    }

    fun next() {
        if (index < episodes.lastIndex) {
            index++
            emitCurrent()
        }
    }

    fun savePosition(positionMs: Long) {
        val key = _state.value.mediaKey
        if (key.isBlank()) return
        viewModelScope.launch { saveResumePosition(key, positionMs) }
    }
}

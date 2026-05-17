package nl.streamfix.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.GetAllSeriesUseCase
import nl.streamfix.domain.usecase.GetAllVodUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.util.AppResult

private const val MAX_PER_TYPE = 50
private const val DEBOUNCE_MS = 250L

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val live: List<LiveChannel> = emptyList(),
    val vod: List<VodItem> = emptyList(),
    val series: List<SeriesItem> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getChannels: GetLiveChannelsUseCase,
    private val getAllVod: GetAllVodUseCase,
    private val getAllSeries: GetAllSeriesUseCase,
    private val getActiveAccount: GetActiveAccountUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var allLive: List<LiveChannel> = emptyList()
    private var allVod: List<VodItem> = emptyList()
    private var allSeries: List<SeriesItem> = emptyList()
    private var loaded = false
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            var lastId: String? = null
            var first = true
            getActiveAccount().collect { acc ->
                val id = acc?.id
                if (first || id != lastId) {
                    first = false
                    lastId = id
                    searchJob?.cancel()
                    allLive = emptyList()
                    allVod = emptyList()
                    allSeries = emptyList()
                    loaded = false
                    _state.value = SearchUiState()
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    live = emptyList(),
                    vod = emptyList(),
                    series = emptyList(),
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            ensureLoaded()
            val q = value.trim()
            val (l, v, s) = withContext(Dispatchers.Default) {
                fun m(name: String) = name.contains(q, ignoreCase = true)
                Triple(
                    allLive.filter { m(it.name) }.take(MAX_PER_TYPE),
                    allVod.filter { m(it.name) }.take(MAX_PER_TYPE),
                    allSeries.filter { m(it.name) }.take(MAX_PER_TYPE),
                )
            }
            _state.update {
                it.copy(isLoading = false, live = l, vod = v, series = s)
            }
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _state.update { it.copy(isLoading = true) }
        val liveR = getChannels(null)
        val vodR = getAllVod()
        val seriesR = getAllSeries()
        (liveR as? AppResult.Success)?.let { allLive = it.data }
        (vodR as? AppResult.Success)?.let { allVod = it.data }
        (seriesR as? AppResult.Success)?.let { allSeries = it.data }
        // Pas "geladen" als minstens een bron lukte; anders volgende keer
        // opnieuw proberen i.p.v. blijvend leeg.
        loaded = liveR is AppResult.Success ||
            vodR is AppResult.Success ||
            seriesR is AppResult.Success
    }
}

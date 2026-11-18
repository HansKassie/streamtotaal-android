package nl.streamfix.ui.screens.nowontv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.util.AppResult

data class NowItem(
    val channel: LiveChannel,
    val nowTitle: String?,
    val startMs: Long,
    val endMs: Long,
)

data class NowOnTvState(
    val isLoading: Boolean = true,
    val items: List<NowItem> = emptyList(),
)

@HiltViewModel
class NowOnTvViewModel @Inject constructor(
    private val observeFavorites: ObserveFavoritesUseCase,
    private val getChannelEpg: GetChannelEpgUseCase,
    private val getActiveAccount: GetActiveAccountUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(NowOnTvState())
    val state: StateFlow<NowOnTvState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0
    private val epgSlots = Semaphore(4)

    init {
        viewModelScope.launch {
            var lastId: String? = null
            var first = true
            getActiveAccount().collect { acc ->
                val id = acc?.id
                if (first || id != lastId) {
                    first = false
                    lastId = id
                    loadGeneration++
                    loadJob?.cancel()
                    _state.value = NowOnTvState(isLoading = acc != null)
                    if (acc != null) load()
                }
            }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            val favs = observeFavorites().first()
            if (generation != loadGeneration) return@launch
            val now = System.currentTimeMillis()
            val items = coroutineScope {
                favs.map { ch ->
                    async {
                        val epg = epgSlots.withPermit {
                            (getChannelEpg(ch.id) as? AppResult.Success)?.data
                        }
                        val current = epg?.firstOrNull {
                            now in it.startMs until it.endMs
                        }
                        NowItem(
                            channel = ch,
                            nowTitle = current?.title,
                            startMs = current?.startMs ?: 0L,
                            endMs = current?.endMs ?: 0L,
                        )
                    }
                }.awaitAll()
            }
            if (generation != loadGeneration) return@launch
            _state.update { it.copy(isLoading = false, items = items) }
        }
    }
}

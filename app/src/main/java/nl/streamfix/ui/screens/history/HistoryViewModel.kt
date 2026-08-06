package nl.streamfix.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.usecase.ClearHistoryUseCase
import nl.streamfix.domain.usecase.ObserveHistoryUseCase
import nl.streamfix.domain.usecase.RemoveFromHistoryUseCase

/** Wat de speler nodig heeft om een item af te spelen, zonder stream-URL. */
data class PlaybackTarget(
    val type: String,
    val contentId: String,
    val extension: String,
    val title: String,
    val mediaId: String,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    private val removeFromHistory: RemoveFromHistoryUseCase,
    private val clearHistory: ClearHistoryUseCase,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> =
        observeHistory().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun remove(item: HistoryItem) {
        viewModelScope.launch { removeFromHistory(item.mediaId) }
    }

    fun clearAll() {
        viewModelScope.launch { clearHistory() }
    }

    /**
     * Brongegevens om af te spelen, of null bij een onbekend type. Bewust
     * GEEN stream-URL: die bevat inloggegevens en zou zo in de
     * navigatiestate belanden. De speler bouwt hem zelf op.
     */
    fun targetFor(item: HistoryItem): PlaybackTarget? = when (item.type) {
        "vod", "ep" -> PlaybackTarget(
            type = item.type,
            contentId = item.contentId,
            extension = item.extension,
            title = item.title,
            mediaId = item.mediaId,
        )
        else -> null
    }
}

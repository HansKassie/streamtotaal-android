package nl.streamfix.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.usecase.GetVodStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveHistoryUseCase

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    private val getVodStreamUrl: GetVodStreamUrlUseCase,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> =
        observeHistory().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** (streamUrl, title, mediaId) om af te spelen, of null als niet mogelijk. */
    fun targetFor(item: HistoryItem): Triple<String, String, String>? {
        val url = when (item.type) {
            "vod" -> getVodStreamUrl(item.contentId, item.extension)
            else -> null // "ep" volgt met Series (Deel B)
        } ?: return null
        return Triple(url, item.title, item.mediaId)
    }
}

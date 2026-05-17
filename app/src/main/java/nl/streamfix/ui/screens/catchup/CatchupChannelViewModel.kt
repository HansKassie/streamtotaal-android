package nl.streamfix.ui.screens.catchup

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
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.usecase.GetCatchupEpgUseCase
import nl.streamfix.domain.usecase.GetTimeshiftUrlUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.uiMessage

data class CatchupChannelState(
    val channelName: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val programmes: List<EpgProgramme> = emptyList(),
)

@HiltViewModel
class CatchupChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCatchupEpg: GetCatchupEpgUseCase,
    private val getTimeshiftUrl: GetTimeshiftUrlUseCase,
) : ViewModel() {

    private val channelId: String =
        savedStateHandle.get<String>(Routes.CATCHUP_ARG_ID).orEmpty()
    private val days: Int =
        savedStateHandle.get<String>(Routes.CATCHUP_ARG_DAYS)?.toIntOrNull()
            ?: savedStateHandle.get<Int>(Routes.CATCHUP_ARG_DAYS) ?: 7

    private val _state = MutableStateFlow(
        CatchupChannelState(
            channelName = savedStateHandle
                .get<String>(Routes.CATCHUP_ARG_NAME).orEmpty(),
        ),
    )
    val state: StateFlow<CatchupChannelState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = getCatchupEpg(channelId)) {
                is AppResult.Success -> {
                    val now = System.currentTimeMillis()
                    val windowStart = now - days * 24L * 60L * 60L * 1000L
                    val past = r.data
                        .filter { it.endMs <= now && it.startMs >= windowStart }
                        .sortedByDescending { it.startMs }
                    _state.update {
                        it.copy(isLoading = false, programmes = past)
                    }
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

    /** (streamUrl, title, mediaId) om terug te kijken, of null. */
    fun targetFor(p: EpgProgramme): Triple<String, String, String>? {
        val durationMin =
            ((p.endMs - p.startMs) / 60_000L).toInt().coerceAtLeast(1)
        val url = getTimeshiftUrl(channelId, p.startMs, durationMin)
            ?: return null
        val title = "${_state.value.channelName} - ${p.title}"
        val mediaId = "catchup:$channelId:${p.startMs / 1000L}"
        return Triple(url, title, mediaId)
    }
}

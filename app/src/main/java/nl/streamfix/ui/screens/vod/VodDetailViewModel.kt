package nl.streamfix.ui.screens.vod

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
import nl.streamfix.domain.model.VodDetail
import nl.streamfix.domain.usecase.GetVodDetailUseCase
import nl.streamfix.domain.usecase.GetVodStreamUrlUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.uiMessage

data class VodDetailState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val detail: VodDetail? = null,
)

@HiltViewModel
class VodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDetail: GetVodDetailUseCase,
    private val getStreamUrl: GetVodStreamUrlUseCase,
) : ViewModel() {

    private val vodId: String =
        savedStateHandle.get<String>(Routes.VOD_ARG_ID).orEmpty()

    private val _state = MutableStateFlow(VodDetailState())
    val state: StateFlow<VodDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = getDetail(vodId)) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, detail = r.data) }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = r.error.uiMessage())
                    }
            }
        }
    }

    /** (streamUrl, title, mediaId) of null als er nog geen detail is. */
    fun playbackTarget(): Triple<String, String, String>? {
        val d = _state.value.detail ?: return null
        val url = getStreamUrl(d.id, d.containerExtension) ?: return null
        return Triple(url, d.name, "vod:${d.id}")
    }
}

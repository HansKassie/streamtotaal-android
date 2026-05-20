package nl.streamfix.ui.screens.epg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.uiMessage

data class ChannelEpgState(
    val channelName: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val programmes: List<EpgProgramme> = emptyList(),
)

@HiltViewModel
class ChannelEpgViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannelEpg: GetChannelEpgUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val channelId: String =
        savedStateHandle.get<String>(Routes.CHANNEL_EPG_ARG_ID).orEmpty()

    private val _state = MutableStateFlow(
        ChannelEpgState(
            channelName = savedStateHandle
                .get<String>(Routes.CHANNEL_EPG_ARG_NAME).orEmpty(),
        ),
    )
    val state: StateFlow<ChannelEpgState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = getChannelEpg(channelId)) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, programmes = r.data) }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = r.error.uiMessage(context))
                    }
            }
        }
    }
}

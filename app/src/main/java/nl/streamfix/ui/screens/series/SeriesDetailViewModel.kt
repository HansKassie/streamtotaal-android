package nl.streamfix.ui.screens.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.SeriesDetail
import nl.streamfix.domain.usecase.GetSeriesDetailUseCase
import nl.streamfix.domain.usecase.IsSeriesFavoriteUseCase
import nl.streamfix.domain.usecase.SetSeriesFavoriteUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.uiMessage

data class SeriesDetailState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val detail: SeriesDetail? = null,
    val selectedSeason: Int? = null,
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDetail: GetSeriesDetailUseCase,
    private val isSeriesFavorite: IsSeriesFavoriteUseCase,
    private val setSeriesFavorite: SetSeriesFavoriteUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val seriesId: String =
        savedStateHandle.get<String>(Routes.SERIES_ARG_ID).orEmpty()

    private val _state = MutableStateFlow(SeriesDetailState())
    val state: StateFlow<SeriesDetailState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = isSeriesFavorite(seriesId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun toggleFavorite() {
        val d = _state.value.detail ?: return
        viewModelScope.launch {
            setSeriesFavorite(d.id, d.name, d.posterUrl, !isFavorite.value)
        }
    }

    init {
        viewModelScope.launch {
            when (val r = getDetail(seriesId)) {
                is AppResult.Success ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = r.data,
                            selectedSeason = r.data.seasons.firstOrNull()?.number,
                        )
                    }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = r.error.uiMessage(context))
                    }
            }
        }
    }

    fun selectSeason(number: Int) =
        _state.update { it.copy(selectedSeason = number) }
}

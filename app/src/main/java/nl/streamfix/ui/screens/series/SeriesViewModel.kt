package nl.streamfix.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.usecase.GetSeriesCategoriesUseCase
import nl.streamfix.domain.usecase.GetSeriesItemsUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

data class SeriesUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    private val items: List<SeriesItem> = emptyList(),
) {
    val visibleItems: List<SeriesItem>
        get() = if (query.isBlank()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val getCategories: GetSeriesCategoriesUseCase,
    private val getItems: GetSeriesItemsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesUiState())
    val state: StateFlow<SeriesUiState> = _state.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = getCategories()) {
                is AppResult.Success -> {
                    _state.update { it.copy(isLoading = false, categories = r.data) }
                    r.data.firstOrNull()?.id?.let { selectCategory(it) }
                        ?: _state.update { it.copy(isLoading = false) }
                }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = r.error.uiMessage())
                    }
            }
        }
    }

    fun selectCategory(categoryId: String) {
        _state.update {
            it.copy(
                selectedCategoryId = categoryId,
                query = "",
                isLoading = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val r = getItems(categoryId)) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, items = r.data) }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = r.error.uiMessage(),
                            items = emptyList(),
                        )
                    }
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }
}

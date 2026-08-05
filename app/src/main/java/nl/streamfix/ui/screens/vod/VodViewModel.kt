package nl.streamfix.ui.screens.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.R
import nl.streamfix.data.local.AppSettingsStore
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.GetVodCategoriesUseCase
import nl.streamfix.domain.usecase.GetVodItemsUseCase
import nl.streamfix.domain.usecase.ObserveVodFavoritesUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.screens.live.FAVORITES_ID
import nl.streamfix.ui.uiMessage

data class VodUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    private val items: List<VodItem> = emptyList(),
    private val favorites: List<VodItem> = emptyList(),
) {
    val visibleItems: List<VodItem>
        get() {
            val source =
                if (selectedCategoryId == FAVORITES_ID) favorites else items
            return if (query.isBlank()) source
            else source.filter { it.name.contains(query, ignoreCase = true) }
        }
}

@HiltViewModel
class VodViewModel @Inject constructor(
    private val getCategories: GetVodCategoriesUseCase,
    private val getItems: GetVodItemsUseCase,
    observeFavorites: ObserveVodFavoritesUseCase,
    private val getActiveAccount: GetActiveAccountUseCase,
    private val appSettings: AppSettingsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(VodUiState())
    val state: StateFlow<VodUiState> = _state.asStateFlow()

    private var categoriesJob: Job? = null
    private var itemsJob: Job? = null

    /** Zie LiveTvViewModel: negeert antwoorden van een oudere generatie. */
    private var loadGeneration = 0

    private fun startFreshLoad() {
        loadGeneration++
        categoriesJob?.cancel()
        itemsJob?.cancel()
    }

    init {
        viewModelScope.launch {
            observeFavorites().collect { favs ->
                _state.update { it.copy(favorites = favs) }
            }
        }
        // Herlaad zodra de actieve provider wisselt (eerste emissie = start).
        viewModelScope.launch {
            var lastId: String? = null
            var first = true
            getActiveAccount().collect { acc ->
                val id = acc?.id
                if (first || id != lastId) {
                    first = false
                    lastId = id
                    startFreshLoad()
                    _state.value = VodUiState()
                    if (acc != null) loadCategories()
                }
            }
        }
        viewModelScope.launch {
            appSettings.adultState.drop(1).collect {
                startFreshLoad()
                loadCategories()
            }
        }
    }

    private fun loadCategories() {
        val gen = loadGeneration
        categoriesJob?.cancel()
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        categoriesJob = viewModelScope.launch {
            val result = getCategories()
            if (gen != loadGeneration) return@launch
            when (val r = result) {
                is AppResult.Success -> {
                    val cats = listOf(
                        LiveCategory(
                            FAVORITES_ID,
                            context.getString(R.string.common_favorites),
                        ),
                    ) + r.data
                    _state.update { it.copy(isLoading = false, categories = cats) }
                    selectCategory(r.data.firstOrNull()?.id ?: FAVORITES_ID)
                }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = r.error.uiMessage(context))
                    }
            }
        }
    }

    fun selectCategory(categoryId: String) {
        val gen = loadGeneration
        itemsJob?.cancel()
        if (categoryId == FAVORITES_ID) {
            _state.update {
                it.copy(
                    selectedCategoryId = categoryId,
                    query = "",
                    isLoading = false,
                    errorMessage = null,
                )
            }
            return
        }
        _state.update {
            it.copy(
                selectedCategoryId = categoryId,
                query = "",
                isLoading = true,
                errorMessage = null,
            )
        }
        itemsJob = viewModelScope.launch {
            val result = getItems(categoryId)
            if (gen != loadGeneration ||
                _state.value.selectedCategoryId != categoryId
            ) {
                return@launch
            }
            when (val r = result) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, items = r.data) }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = r.error.uiMessage(context),
                            items = emptyList(),
                        )
                    }
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }
}

package nl.streamfix.ui.screens.catchup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.data.local.AppSettingsStore
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

private val NL_TOKENS =
    setOf("nl", "nld", "ned", "nederland", "holland", "dutch")

/** NL herkennen op woordniveau (voorkomt false hits zoals "Finland"). */
private fun isDutch(name: String): Boolean =
    name.lowercase()
        .split(Regex("[^a-z]+"))
        .any { it in NL_TOKENS }

data class CatchupUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val query: String = "",
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    private val channels: List<LiveChannel> = emptyList(),
) {
    val visibleChannels: List<LiveChannel>
        get() {
            val inCat = channels.filter { it.categoryId == selectedCategoryId }
            return if (query.isBlank()) inCat
            else inCat.filter { it.name.contains(query, ignoreCase = true) }
        }
}

@HiltViewModel
class CatchupViewModel @Inject constructor(
    private val getChannels: GetLiveChannelsUseCase,
    private val getCategories: GetLiveCategoriesUseCase,
    private val getActiveAccount: GetActiveAccountUseCase,
    private val appSettings: AppSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CatchupUiState())
    val state: StateFlow<CatchupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var lastId: String? = null
            var first = true
            getActiveAccount().collect { acc ->
                val id = acc?.id
                if (first || id != lastId) {
                    first = false
                    lastId = id
                    _state.value = CatchupUiState()
                    if (acc != null) load()
                }
            }
        }
        viewModelScope.launch {
            appSettings.adultState.drop(1).collect { load() }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val categories = when (val c = getCategories()) {
                is AppResult.Success -> c.data
                is AppResult.Failure -> emptyList()
            }
            when (val r = getChannels(null)) {
                is AppResult.Success -> {
                    val archive = r.data
                        .filter { it.archiveDays > 0 }
                        .sortedBy { it.name }
                    // Alleen categorieen die echt terugkijk-kanalen hebben.
                    val catIds = archive.mapNotNull { it.categoryId }.toSet()
                    val shown = categories.filter { it.id in catIds }
                    val default = shown.firstOrNull { isDutch(it.name) }?.id
                        ?: shown.firstOrNull()?.id
                    _state.update {
                        it.copy(
                            isLoading = false,
                            categories = shown,
                            selectedCategoryId = default,
                            channels = archive,
                        )
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

    fun selectCategory(categoryId: String) =
        _state.update { it.copy(selectedCategoryId = categoryId, query = "") }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }
}

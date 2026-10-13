package nl.streamfix.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetLiveCastUrlUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.RememberLastChannelUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import nl.streamfix.ui.screens.live.FAVORITES_ID

data class PlayerUiState(
    val title: String = "",
    val streamUrl: String? = null,
    val castStreamUrl: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val channels: List<LiveChannel> = emptyList(),
    val currentChannelId: String? = null,
    val hasLast: Boolean = false,
    /**
     * De lijst kwam leeg terug. Bewust GEEN "deze zender bestaat niet meer":
     * een leeg antwoord komt ook van een hernoemde categorie, een panel dat
     * tijdelijk [] teruggeeft of een filter, terwijl de zender zelf prima
     * werkt. Daarom herstelbaar, met een knop om opnieuw te laden.
     */
    val channelListEmpty: Boolean = false,
    val channelLoadFailed: Boolean = false,
    /** Groepen voor het overzicht; leeg als ze niet opgehaald konden worden. */
    val categories: List<LiveCategory> = emptyList(),
    /** De groep die in het overzicht open staat. */
    val browseCategoryId: String = "",
    /**
     * Laden en mislukken van een BEKEKEN groep horen in het overzicht en
     * niet in [channelLoadFailed]: die zet een melding over het hele beeld,
     * en het beeld speelt gewoon door terwijl je een andere groep opent.
     */
    val browseLoading: Boolean = false,
    val browseFailed: Boolean = false,
)

private sealed interface ChannelLoadResult {
    data class Success(val channels: List<LiveChannel>) : ChannelLoadResult
    data object Failure : ChannelLoadResult
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannels: GetLiveChannelsUseCase,
    private val getCategories: GetLiveCategoriesUseCase,
    private val observeFavorites: ObserveFavoritesUseCase,
    private val getStreamUrl: GetStreamUrlUseCase,
    private val getCastUrl: GetLiveCastUrlUseCase,
    private val rememberLast: RememberLastChannelUseCase,
) : ViewModel() {

    private val startCategoryId: String =
        savedStateHandle.get<String>(Routes.PLAYER_ARG_CATEGORY).orEmpty()
    private val startChannelId: String =
        savedStateHandle.get<String>(Routes.PLAYER_ARG_CHANNEL).orEmpty()

    /**
     * De groep die SPEELT en de groep die in het overzicht wordt BEKEKEN.
     * Die lopen uiteen zodra de kijker in het overzicht een andere groep
     * opent: het beeld hoort dan door te lopen tot hij echt een zender
     * kiest. Meteen de eerste zender van die groep starten zou eenvoudiger
     * zijn, maar een stream die niemand vroeg kost bij een provider met een
     * verbindingslimiet een slot, en de kijker ziet iets wat hij niet koos.
     */
    private var playingCategoryId: String = startCategoryId
    private var browseCategoryId: String = startCategoryId

    /** Gids hoort bij de groep die je BEKIJKT, niet bij wat er speelt. */
    val guideCategoryId: String get() = browseCategoryId.ifBlank { FAVORITES_ID }

    /** Lijst waarin zappen gebeurt; volgt de spelende groep. */
    private var playedChannels: List<LiveChannel> = emptyList()

    /** Lijst in het overzicht; gelijk aan [playedChannels] tenzij je kijkt. */
    private var browseChannels: List<LiveChannel> = emptyList()
    private var index: Int = 0
    private var previousIndex: Int = -1

    private val _state =
        MutableStateFlow(PlayerUiState(browseCategoryId = startCategoryId))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        load()
        loadCategories()
    }

    /**
     * Groepen eenmalig ophalen voor de keuzelijst in het overzicht. Mislukt
     * dit, dan blijft de lijst leeg en laat het overzicht de groepkeuze
     * gewoon weg; het kijken mag er niet op stuklopen.
     */
    private fun loadCategories() {
        viewModelScope.launch {
            val result = getCategories()
            if (result is AppResult.Success) {
                _state.update { it.copy(categories = result.data) }
            }
        }
    }

    /**
     * Annuleerbaar en met een generatieteller, zoals de andere ViewModels
     * sinds 1.0.14. Zonder dit kan een trage eerste poging na een geslaagde
     * tweede alsnog een foutvlag zetten, en die melding landt dan over
     * beeld dat gewoon speelt.
     */
    private var loadJob: Job? = null
    private var loadGeneration: Int = 0

    private fun load() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(channelListEmpty = false, channelLoadFailed = false)
            }
            val result = loadChannelsFor(playingCategoryId)
            if (generation != loadGeneration) return@launch
            when (result) {
                ChannelLoadResult.Failure -> {
                    playedChannels = emptyList()
                    _state.update { it.copy(channelLoadFailed = true) }
                    return@launch
                }
                is ChannelLoadResult.Success -> playedChannels = result.channels
            }
            if (playedChannels.isEmpty()) {
                _state.update { it.copy(channelListEmpty = true) }
                return@launch
            }
            // Het overzicht begint bij de groep die speelt.
            browseChannels = playedChannels
            // Bewuste tv-conventie: bestaat het (opgeslagen) startkanaal
            // niet meer, dan speelt het eerste kanaal van de categorie,
            // zoals een echte tv; de zendernaam is bij binnenkomst in
            // beeld, dus geen stille verrassing.
            index = playedChannels.indexOfFirst { it.id == startChannelId }
                .takeIf { it >= 0 } ?: 0
            emitCurrent()
        }
    }

    private suspend fun loadChannelsFor(categoryId: String): ChannelLoadResult =
        try {
            if (categoryId == FAVORITES_ID) {
                ChannelLoadResult.Success(observeFavorites().first())
            } else {
                // Leeg (bijv. vanuit zoeken) = alle kanalen, anders de categorie.
                val cat = categoryId.ifBlank { null }
                when (val r = getChannels(cat)) {
                    is AppResult.Success -> ChannelLoadResult.Success(r.data)
                    is AppResult.Failure -> ChannelLoadResult.Failure
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ChannelLoadResult.Failure
        }

    fun retryLoad() = load()

    /**
     * Opent een andere groep in het overzicht. Verandert bewust NIETS aan
     * wat er speelt; dat gebeurt pas bij [playFromOverlay].
     */
    fun browseCategory(categoryId: String) {
        if (categoryId == browseCategoryId) return
        browseJob?.cancel()
        val generation = ++browseGeneration
        browseCategoryId = categoryId
        browseChannels = emptyList()
        _state.update {
            it.copy(
                browseCategoryId = categoryId,
                browseLoading = true,
                browseFailed = false,
                channels = emptyList(),
            )
        }
        browseJob = viewModelScope.launch {
            val result = loadChannelsFor(categoryId)
            if (generation != browseGeneration) return@launch
            when (result) {
                ChannelLoadResult.Failure ->
                    _state.update {
                        it.copy(browseLoading = false, browseFailed = true)
                    }
                is ChannelLoadResult.Success -> {
                    browseChannels = result.channels
                    _state.update {
                        it.copy(
                            browseLoading = false,
                            channels = result.channels,
                        )
                    }
                }
            }
        }
    }

    private var browseJob: Job? = null
    private var browseGeneration: Int = 0

    /**
     * Zet het overzicht terug op de groep die speelt. Aanroepen als het
     * overzicht sluit: anders kijkt de kijker in groep B, zapt met links en
     * rechts door groep A, en ziet hij bij het heropenen nog steeds B
     * zonder dat daar iets gemarkeerd staat.
     */
    fun resetBrowseToPlaying() {
        if (browseCategoryId == playingCategoryId) return
        browseJob?.cancel()
        browseGeneration++
        browseCategoryId = playingCategoryId
        browseChannels = playedChannels
        _state.update {
            it.copy(
                browseCategoryId = playingCategoryId,
                browseLoading = false,
                browseFailed = false,
                channels = playedChannels,
            )
        }
    }

    /**
     * Kiest een zender uit het overzicht. Staat daar een andere groep open,
     * dan wordt die vanaf nu ook de groep waarin links en rechts zappen.
     */
    fun playFromOverlay(channelId: String) {
        if (browseCategoryId == playingCategoryId) {
            selectChannel(channelId)
            return
        }
        val newIndex = browseChannels.indexOfFirst { it.id == channelId }
        if (newIndex < 0) return
        playingCategoryId = browseCategoryId
        playedChannels = browseChannels
        // "Vorige zender" wees naar een plek in de oude lijst en zou nu een
        // willekeurige andere zender aanwijzen. Daarom vergeten.
        previousIndex = -1
        index = newIndex
        emitCurrent()
    }

    private fun emitCurrent() {
        val channel = playedChannels.getOrNull(index)
        if (channel == null) {
            _state.update { it.copy(streamUrl = null) }
            return
        }
        // Voor "start met laatste zender"; volwassen kanalen worden in de
        // repository overgeslagen. Wisselde de kijker van groep, dan hoort
        // die nieuwe groep onthouden te worden.
        rememberLast(playingCategoryId, channel)
        _state.update {
            it.copy(
                title = channel.name,
                streamUrl = getStreamUrl(channel.id),
                castStreamUrl = getCastUrl(channel.id),
                hasPrevious = index > 0,
                hasNext = index < playedChannels.lastIndex,
                channels = browseChannels,
                browseCategoryId = browseCategoryId,
                currentChannelId = channel.id,
                hasLast = previousIndex in playedChannels.indices &&
                    previousIndex != index,
            )
        }
    }

    private fun setIndex(newIndex: Int) {
        if (newIndex in playedChannels.indices && newIndex != index) {
            previousIndex = index
            index = newIndex
            emitCurrent()
        }
    }

    fun selectChannel(id: String) {
        setIndex(playedChannels.indexOfFirst { it.id == id })
    }

    fun next() {
        setIndex(index + 1)
    }

    fun previous() {
        setIndex(index - 1)
    }

    fun lastChannel() {
        if (previousIndex in playedChannels.indices && previousIndex != index) {
            val current = index
            index = previousIndex
            previousIndex = current
            emitCurrent()
        }
    }
}

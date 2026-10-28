package nl.streamfix.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.streamfix.data.local.AdultVisibility
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLastWatchedChannelUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.SetFavoriteUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.domain.util.EpgNowNext
import nl.streamfix.domain.util.EpgRequestGate
import nl.streamfix.ui.ErrorTexts
import nl.streamfix.ui.pickFocusTarget
import nl.streamfix.ui.pickStartCategory

const val FAVORITES_ID = "__favorites__"

data class LiveUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    val favoriteIds: Set<String> = emptySet(),
    private val channels: List<LiveChannel> = emptyList(),
) {
    val visibleChannels: List<LiveChannel>
        get() = if (query.isBlank()) channels
        else channels.filter { it.name.contains(query, ignoreCase = true) }
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val getCategories: GetLiveCategoriesUseCase,
    private val getChannels: GetLiveChannelsUseCase,
    private val observeFavorites: ObserveFavoritesUseCase,
    private val setFavorite: SetFavoriteUseCase,
    private val getChannelEpg: GetChannelEpgUseCase,
    private val epgGate: EpgRequestGate,
    private val getActiveAccount: GetActiveAccountUseCase,
    private val getLastWatched: GetLastWatchedChannelUseCase,
    private val adultVisibility: AdultVisibility,
    private val errorTexts: ErrorTexts,
) : ViewModel() {

    /**
     * Laatst gefocuste kanaal in de lijst. Staat bewust hier en niet in
     * rememberSaveable: bij het openen van de speler verdwijnt het scherm
     * uit de compositie en bleek die state niet betrouwbaar terug te komen,
     * waardoor de lijst weer bovenaan begon.
     */
    private var focusedChannelId: String? = null

    fun onChannelFocused(channelId: String) {
        focusedChannelId = channelId
    }

    /**
     * Waar de focus heen moet bij het (her)opbouwen van de lijst. Eerst de
     * zender waar de speler op eindigde: wie daar met de pijltjes doorzapt,
     * verwacht bij terugkeer die zender en niet degene die hij aantikte.
     * Daarna het laatst gefocuste item, anders het begin van de lijst.
     */
    fun focusTargetId(channels: List<LiveChannel>): String? =
        pickFocusTarget(
            channels = channels,
            lastWatchedId = getLastWatched()?.second,
            lastFocusedId = focusedChannelId,
        )

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val _epg = MutableStateFlow<Map<String, List<EpgProgramme>>>(emptyMap())
    /** Per kanaal-id de (gecachte) programma's; gevuld zodra een rij erom vraagt. */
    val epg: StateFlow<Map<String, List<EpgProgramme>>> = _epg.asStateFlow()


    /** Zie [EpgRequestGate]: rem tegen tientallen aanvragen bij scrollen. */
    private val epgSlots = Semaphore(MAX_PARALLEL_EPG)

    /** Hoogt op bij elke netwerkwissel; zie [ensureEpg]. */
    private var epgGeneration = 0

    private var favorites: List<LiveChannel> = emptyList()

    private var categoriesJob: Job? = null
    private var channelsJob: Job? = null

    /**
     * Hoogt op bij elke provider- of filterwissel. Een laadactie onthoudt de
     * generatie waarin hij startte; komt het antwoord later binnen dan is het
     * verouderd en wordt het genegeerd. Nodig naast [Job.cancel] omdat
     * annuleren pas bij het volgende suspension-punt aankomt, en omdat
     * categorie-ids per provider hergebruikt kunnen worden.
     */
    private var loadGeneration = 0

    private fun startFreshLoad() {
        loadGeneration++
        categoriesJob?.cancel()
        channelsJob?.cancel()
    }

    init {
        viewModelScope.launch {
            observeFavorites().collect { favs ->
                favorites = favs
                _state.update { s ->
                    val refreshed = if (s.selectedCategoryId == FAVORITES_ID) {
                        s.copy(
                            favoriteIds = favs.map { it.id }.toSet(),
                            channels = favs,
                        )
                    } else {
                        s.copy(favoriteIds = favs.map { it.id }.toSet())
                    }
                    refreshed
                }
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
                    _state.value = LiveUiState()
                    epgGate.clear()
                    _epg.value = emptyMap()
                    // Een gidsaanvraag die nu nog onderweg is hoort bij het
                    // VORIGE netwerk. Leegmaken is niet genoeg: zo'n
                    // aanvraag schrijft na terugkomst gewoon in de verse
                    // map, en dan staat er bij een zender van netwerk B een
                    // programma van netwerk A. Annuleren alleen helpt ook
                    // niet, want tussen het antwoord en het wegschrijven zit
                    // geen opschortpunt waar annulering kan landen.
                    epgGeneration++
                    if (acc != null) loadCategories(preferLastWatched = true)
                }
            }
        }
        // Volwassen-zichtbaarheid gewijzigd: categorieen opnieuw laden
        // (gefilterd); favorieten blijven, zelfde provider.
        viewModelScope.launch {
            adultVisibility.adultState.drop(1).collect {
                startFreshLoad()
                loadCategories()
            }
        }
    }

    /**
     * [preferLastWatched] = openen in de groep waar de kijker gebleven was.
     * Alleen bij een verse start of providerwissel. Bij het omzetten van het
     * volwassen-filter laden we ook opnieuw, en dan zou terugspringen naar
     * een oude groep de kijker juist wegslepen van waar hij net keek.
     */
    private fun loadCategories(preferLastWatched: Boolean = false) {
        val gen = loadGeneration
        categoriesJob?.cancel()
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        categoriesJob = viewModelScope.launch {
            val result = getCategories()
            if (gen != loadGeneration) return@launch
            when (result) {
                is AppResult.Success -> {
                    _state.update { it.copy(isLoading = false, categories = result.data) }
                    selectCategory(
                        pickStartCategory(
                            categoryIds = result.data.map { it.id },
                            lastCategoryId =
                                if (preferLastWatched) getLastWatched()?.first else null,
                            favoritesId = FAVORITES_ID,
                        ),
                    )
                }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorTexts.of(result.error),
                        )
                    }
            }
        }
    }

    fun selectCategory(categoryId: String) {
        val gen = loadGeneration
        channelsJob?.cancel()
        _state.update { it.copy(selectedCategoryId = categoryId, query = "") }
        if (categoryId == FAVORITES_ID) {
            _state.update { it.copy(channels = favorites, isLoading = false) }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        channelsJob = viewModelScope.launch {
            val result = getChannels(categoryId)
            // Verouderd antwoord: andere provider/filter, of de gebruiker
            // koos inmiddels een andere categorie.
            if (gen != loadGeneration ||
                _state.value.selectedCategoryId != categoryId
            ) {
                return@launch
            }
            when (result) {
                is AppResult.Success ->
                    _state.update {
                        it.copy(isLoading = false, channels = result.data)
                    }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorTexts.of(result.error),
                            channels = emptyList(),
                        )
                    }
            }
        }
    }

    /**
     * Laadt de gids voor een kanaal, lazy vanuit de zichtbare rij.
     *
     * Hield eerder simpelweg bij welke kanalen al gevraagd waren, om een
     * reeks aanvragen te voorkomen bij scrollen langs kanalen zonder EPG.
     * Dat ging te ver: ook een MISLUKTE aanvraag telde als afgehandeld, dus
     * één hapering liet die zender de rest van de sessie zonder
     * programma-informatie staan. [EpgRequestGate] scheidt die twee: leeg
     * maar geslaagd blijft definitief, een fout krijgt na een wachttijd een
     * nieuwe kans.
     *
     * Suspend en geen launch in viewModelScope, zodat wegscrollen de
     * aanvraag afbreekt in plaats van hem alsnog af te werken voor een rij
     * die niemand meer ziet.
     */
    suspend fun ensureEpg(channelId: String) {
        val now = System.currentTimeMillis()
        val generation = epgGeneration
        // Zie PlayerViewModel.loadOverlayEpg: een gids die helemaal in het
        // verleden ligt mag de poort niet laten blokkeren. Lege antwoorden
        // komen hier niet in _epg terecht, dus die vallen er vanzelf buiten.
        val known = _epg.value[channelId]
        val exhausted = known != null && known.isNotEmpty() &&
            !EpgNowNext.stillRelevant(known, now)
        if (!epgGate.claim(channelId, now, exhausted)) return
        var settled = false
        try {
            epgSlots.withPermit {
                val result = getChannelEpg(channelId)
                // Intussen van netwerk gewisseld? Dan hoort dit antwoord
                // bij een provider die niet meer actief is. Niets opslaan
                // en ook de poort niet bijwerken: die is leeggemaakt en mag
                // niet alsnog een mark uit de vorige sessie krijgen.
                if (generation != epgGeneration) {
                    settled = true
                    return@withPermit
                }
                when (result) {
                    is AppResult.Success -> {
                        epgGate.markDone(
                            channelId,
                            System.currentTimeMillis(),
                        )
                        settled = true
                        // Leeg niet opslaan: de rij en het paneel lezen een
                        // ontbrekende sleutel al als "niets te melden", en
                        // de poort zorgt dat er niet opnieuw gevraagd wordt.
                        if (result.data.isNotEmpty()) {
                            _epg.update { it + (channelId to result.data) }
                        }
                    }
                    is AppResult.Failure -> {
                        epgGate.markFailed(
                            channelId,
                            System.currentTimeMillis(),
                        )
                        settled = true
                    }
                }
            }
        } finally {
            // Afgebroken zonder uitkomst: reservering terug, anders blijft
            // deze rij leeg zodra je terugscrolt.
            //
            // Maar alleen als er intussen geen netwerkwissel was. De poort
            // kent geen eigenaar: release ziet enkel dat er iets voor dit
            // kanaal-id loopt. Was de poort leeggemaakt en heeft het nieuwe
            // netwerk hetzelfde id alweer gereserveerd, dan zou deze
            // opruiming de reservering van DAT netwerk teruggeven, en start
            // er een tweede aanvraag terwijl de eerste nog loopt. Na een
            // wissel valt er sowieso niets terug te geven; de poort is dan
            // al leeg.
            if (!settled && generation == epgGeneration) {
                epgGate.release(channelId)
            }
        }
    }

    fun onQueryChange(value: String) =
        _state.update { it.copy(query = value) }

    fun toggleFavorite(channel: LiveChannel) {
        val isFav = _state.value.favoriteIds.contains(channel.id)
        viewModelScope.launch { setFavorite(channel, !isFav) }
    }

    private companion object {
        const val MAX_PARALLEL_EPG = 4
    }
}

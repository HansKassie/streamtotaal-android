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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.usecase.GetLiveCastUrlUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.RememberLastChannelUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.domain.util.EpgNowNext
import nl.streamfix.domain.util.EpgRequestGate
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
    /**
     * Gids van de SPELENDE zender voor de "Nu en straks"-balk. null = nog
     * niet opgevraagd of weggegooid na een zenderwissel; een lege lijst
     * betekent dat deze zender geen gids heeft. Drie losse toestanden,
     * zodat de balk "laden", "geen gids" en "ophalen mislukt" kan
     * onderscheiden; de uitzending loopt in alle drie gewoon door.
     */
    val epgProgrammes: List<EpgProgramme>? = null,
    val epgLoading: Boolean = false,
    val epgFailed: Boolean = false,
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
    private val getChannelEpg: GetChannelEpgUseCase,
    private val overlayGate: EpgRequestGate,
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

    /**
     * Gids per zender voor de rijen in het zenderoverzicht, los van de
     * "Nu en straks"-balk. Die balk heeft laden en mislukt nodig om drie
     * meldingen te kunnen tonen; een rij heeft dat niet en blijft gewoon
     * leeg. Beide lezen uit dezelfde cache in EpgRepository, dus een zender
     * die in de lijst opgehaald is kost de balk niets meer, en andersom.
     */
    private val _overlayEpg =
        MutableStateFlow<Map<String, List<EpgProgramme>>>(emptyMap())
    val overlayEpg: StateFlow<Map<String, List<EpgProgramme>>> =
        _overlayEpg.asStateFlow()

    /**
     * Hoeveel gidsaanvragen tegelijk onderweg mogen zijn.
     *
     * Alleen zichtbare rijen opvragen houdt het aantal beperkt, maar bij
     * doorscrollen komen tientallen rijen vlak na elkaar in beeld. Zonder
     * deze rem staan die allemaal tegelijk bij het panel aan te kloppen.
     */
    private val overlaySlots = Semaphore(MAX_PARALLEL_EPG)

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

    private var epgJob: Job? = null

    /** Zender waar de gids in de state bij hoort. */
    private var epgChannelId: String? = null

    /**
     * Haalt de gids van de spelende zender op.
     *
     * Het scherm roept dit aan bij het openen van de balk en na een
     * zenderwissel, in dat laatste geval met een korte vertraging. Zonder
     * die vertraging vuurt doorzappen een aanvraag af voor elke zender
     * waar je langs komt en waarvan niemand de gids te zien krijgt.
     */
    fun loadEpgForCurrentChannel() {
        val channelId = _state.value.currentChannelId ?: return
        val s = _state.value
        // Al in huis of al onderweg voor deze zender: niets te doen. Een
        // mislukte poging mag wel opnieuw.
        if (epgChannelId == channelId && (s.epgProgrammes != null || s.epgLoading)) {
            return
        }
        epgJob?.cancel()
        epgChannelId = channelId
        _state.update {
            it.copy(epgLoading = true, epgFailed = false, epgProgrammes = null)
        }
        epgJob = viewModelScope.launch {
            val result = getChannelEpg(channelId)
            // Kwam het antwoord pas binnen nadat de kijker doorzapte, dan
            // hoort het bij een zender die niet meer speelt. Dan weggooien:
            // anders staat de gids van de vorige zender bij de nieuwe.
            if (epgChannelId != channelId ||
                _state.value.currentChannelId != channelId
            ) {
                return@launch
            }
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(epgLoading = false, epgProgrammes = result.data)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(epgLoading = false, epgFailed = true)
                }
            }
        }
    }

    private fun emitCurrent() {
        val channel = playedChannels.getOrNull(index)
        if (channel == null) {
            _state.update { it.copy(streamUrl = null) }
            return
        }
        // Een gids hoort bij een zender. Bij een wissel de lopende aanvraag
        // annuleren en de gegevens weggooien.
        val channelChanged = _state.value.currentChannelId != channel.id
        if (channelChanged) {
            epgJob?.cancel()
            epgChannelId = null
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
                epgProgrammes = if (channelChanged) null else it.epgProgrammes,
                epgLoading = if (channelChanged) false else it.epgLoading,
                epgFailed = if (channelChanged) false else it.epgFailed,
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

    /**
     * Haalt de gids op voor een rij in het zenderoverzicht.
     *
     * Opzettelijk een suspend-functie en geen launch in viewModelScope: het
     * scherm roept hem aan vanuit de rij zelf, dus scrolt die rij uit beeld
     * dan wordt deze aanvraag afgebroken. Bij snel doorscrollen blijft er zo
     * geen rij werk staan voor zenders die de kijker allang voorbij is.
     */
    suspend fun loadOverlayEpg(channelId: String) {
        val now = System.currentTimeMillis()
        // Wat we al hebben kan intussen verlopen zijn: de korte gids beslaat
        // maar een paar uitzendingen. Is alles afgelopen, dan mag de poort
        // niet blijven blokkeren op "dit is al opgehaald". Leeg telt hier
        // niet als verlopen; dat is een zender zonder gids.
        val known = _overlayEpg.value[channelId]
        val exhausted = known != null && known.isNotEmpty() &&
            !EpgNowNext.stillRelevant(known, now)
        if (!overlayGate.claim(channelId, now, exhausted)) return
        var settled = false
        try {
            overlaySlots.withPermit {
                when (val result = getChannelEpg(channelId)) {
                    is AppResult.Success -> {
                        overlayGate.markDone(
                            channelId,
                            System.currentTimeMillis(),
                        )
                        settled = true
                        _overlayEpg.update { it + (channelId to result.data) }
                    }
                    is AppResult.Failure -> {
                        overlayGate.markFailed(
                            channelId,
                            System.currentTimeMillis(),
                        )
                        settled = true
                    }
                }
            }
        } finally {
            // Afgebroken voordat er een uitkomst was: de reservering
            // teruggeven. Blijft die staan, dan vraagt niemand deze zender
            // ooit nog op en blijft de rij leeg zodra je terugscrolt.
            if (!settled) overlayGate.release(channelId)
        }
    }

    private companion object {
        const val MAX_PARALLEL_EPG = 4
    }
}

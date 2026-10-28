package nl.streamfix.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.repository.EpgRepository
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.usecase.GetLiveCastUrlUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.RememberLastChannelUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.domain.util.EpgRequestGate
import nl.streamfix.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fake-repo: alleen de live-kanalen-paden zijn relevant voor de speler. */
private class FakeLiveRepository(
    private val channels: List<LiveChannel>,
) : LiveRepository {
    var channelResult: AppResult<List<LiveChannel>> = AppResult.Success(channels)

    /** Laat een trage laadpoging nabootsen, voor de race-test. */
    var loadDelayMs: Long = 0L

    var categories: List<LiveCategory> = emptyList()

    /** Zenders per groep, voor het wisselen van groep in het overzicht. */
    var channelsByCategory: Map<String, List<LiveChannel>> = emptyMap()

    override suspend fun getCategories(): AppResult<List<LiveCategory>> =
        AppResult.Success(categories)

    override suspend fun getChannels(
        categoryId: String?,
    ): AppResult<List<LiveChannel>> {
        // Bewust vooraf vastleggen: anders pakt een trage poging alsnog
        // het nieuwere antwoord op en valt er niets te racen.
        val result = channelsByCategory[categoryId]
            ?.let { AppResult.Success(it) }
            ?: channelResult
        if (loadDelayMs > 0L) delay(loadDelayMs)
        return result
    }

    override fun observeFavorites(): Flow<List<LiveChannel>> =
        flowOf(channels)

    override suspend fun setFavorite(
        channel: LiveChannel,
        favorite: Boolean,
    ) = Unit

    override fun streamUrl(channelId: String): String? =
        "stream://$channelId"

    override fun streamUrlForCast(channelId: String): String? =
        "cast://$channelId"

    override fun timeshiftUrl(
        channelId: String,
        startMs: Long,
        durationMin: Int,
    ): String? = null

    var remembered: Pair<String, String>? = null

    override fun rememberLastChannel(categoryId: String, channel: LiveChannel) {
        remembered = categoryId to channel.id
    }

    override fun lastWatchedChannel(): Pair<String, String>? = remembered
}

/** Fake-gids: per zender een antwoord, met een regelbare vertraging. */
private class FakeEpgRepository : EpgRepository {
    var byChannel: Map<String, List<EpgProgramme>> = emptyMap()
    var fail = false
    var delayMs = 0L

    /**
     * Wacht niet-annuleerbaar. Bootst na dat het annuleren van de job nog
     * niet is aangekomen: dat gebeurt pas op een suspension-punt. Alleen zo
     * is de controle NA het ophalen te bewijzen en niet alleen het
     * annuleren zelf.
     */
    var uncancellableDelay = false

    /** Hoe vaak er echt is opgehaald, per zender. */
    val requests = mutableListOf<String>()

    /** Hoogste aantal aanvragen dat tegelijk onderweg was. */
    var maxInFlight = 0
    private var inFlight = 0

    override suspend fun getEpg(
        streamId: String,
    ): AppResult<List<EpgProgramme>> {
        requests += streamId
        val shouldFail = fail
        inFlight++
        if (inFlight > maxInFlight) maxInFlight = inFlight
        try {
            if (delayMs > 0L) {
                if (uncancellableDelay) {
                    withContext(NonCancellable) { delay(delayMs) }
                } else {
                    delay(delayMs)
                }
            }
        } finally {
            inFlight--
        }
        return if (shouldFail) {
            AppResult.Failure(AppError.NetworkUnavailable)
        } else {
            AppResult.Success(byChannel[streamId].orEmpty())
        }
    }

    override suspend fun getEpgTable(
        streamId: String,
    ): AppResult<List<EpgProgramme>> = AppResult.Success(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val sample = listOf(
        LiveChannel("c1", "Een", null, "cat1", null),
        LiveChannel("c2", "Twee", null, "cat1", null),
        LiveChannel("c3", "Drie", null, "cat1", null),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        startChannelId: String = "",
        repo: FakeLiveRepository = FakeLiveRepository(sample),
        epg: FakeEpgRepository = FakeEpgRepository(),
        gate: EpgRequestGate = EpgRequestGate(),
    ): PlayerViewModel {
        val handle = SavedStateHandle(
            mapOf(
                Routes.PLAYER_ARG_CATEGORY to "cat1",
                Routes.PLAYER_ARG_CHANNEL to startChannelId,
            ),
        )
        return PlayerViewModel(
            savedStateHandle = handle,
            getChannels = GetLiveChannelsUseCase(repo),
            getCategories = GetLiveCategoriesUseCase(repo),
            getChannelEpg = GetChannelEpgUseCase(epg),
            overlayGate = gate,
            observeFavorites = ObserveFavoritesUseCase(repo),
            getStreamUrl = GetStreamUrlUseCase(repo),
            getCastUrl = GetLiveCastUrlUseCase(repo),
            rememberLast = RememberLastChannelUseCase(repo),
        )
    }

    @Test
    fun startKanaalUitArgumentWordtGekozen() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "c3")
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals("c3", s.currentChannelId)
        assertEquals("Drie", s.title)
        assertEquals("stream://c3", s.streamUrl)
        assertEquals("cast://c3", s.castStreamUrl)
    }

    @Test
    fun onbekendStartKanaalValtTerugOpEerste() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "bestaat-niet")
        advanceUntilIdle()
        assertEquals("c1", vm.state.value.currentChannelId)
    }

    @Test
    fun nextEnPreviousRespecterenGrenzen() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "c1")
        advanceUntilIdle()

        // Aan het begin is er geen vorige.
        assertFalse(vm.state.value.hasPrevious)
        vm.previous()
        assertEquals("c1", vm.state.value.currentChannelId)

        vm.next()
        assertEquals("c2", vm.state.value.currentChannelId)
        vm.next()
        assertEquals("c3", vm.state.value.currentChannelId)

        // Aan het einde geen volgende meer.
        assertFalse(vm.state.value.hasNext)
        vm.next()
        assertEquals("c3", vm.state.value.currentChannelId)
    }

    @Test
    fun selectChannelSpringtNaarId() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "c1")
        advanceUntilIdle()
        vm.selectChannel("c3")
        assertEquals("c3", vm.state.value.currentChannelId)
    }

    @Test
    fun legeLijstIsHerstelbaarEnGeenVerdwenenZender() = runTest(dispatcher) {
        val vm = viewModel(
            startChannelId = "c1",
            repo = FakeLiveRepository(emptyList()),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.channelListEmpty)
        assertFalse(vm.state.value.channelLoadFailed)
        assertEquals(null, vm.state.value.streamUrl)
    }

    @Test
    fun legeLijstIsTeHerstellenMetOpnieuwProberen() = runTest(dispatcher) {
        val repo = FakeLiveRepository(sample).apply {
            channelResult = AppResult.Success(emptyList())
        }
        val vm = viewModel(startChannelId = "c1", repo = repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.channelListEmpty)

        repo.channelResult = AppResult.Success(sample)
        vm.retryLoad()
        advanceUntilIdle()
        assertFalse(vm.state.value.channelListEmpty)
        assertEquals("c1", vm.state.value.currentChannelId)
    }

    @Test
    fun trageEerstePogingOverschrijftGeslaagdeTweedeNiet() =
        runTest(dispatcher) {
            // Regressie: zonder annuleerbare job plus generatieteller zet de
            // late mislukte poging alsnog een foutvlag, en die melding landt
            // over beeld dat al speelt.
            val repo = FakeLiveRepository(sample).apply {
                channelResult = AppResult.Failure(AppError.NetworkUnavailable)
                loadDelayMs = 5_000L
            }
            val vm = viewModel(startChannelId = "c1", repo = repo)
            advanceTimeBy(1_000L)

            repo.channelResult = AppResult.Success(sample)
            repo.loadDelayMs = 0L
            vm.retryLoad()
            advanceUntilIdle()

            assertFalse(vm.state.value.channelLoadFailed)
            assertFalse(vm.state.value.channelListEmpty)
            assertEquals("c1", vm.state.value.currentChannelId)
        }

    @Test
    fun netwerkfoutKanOpnieuwWordenGeprobeerd() = runTest(dispatcher) {
        val repo = FakeLiveRepository(sample).apply {
            channelResult = AppResult.Failure(AppError.NetworkUnavailable)
        }
        val vm = viewModel(startChannelId = "c1", repo = repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.channelLoadFailed)
        assertFalse(vm.state.value.channelListEmpty)

        repo.channelResult = AppResult.Success(sample)
        vm.retryLoad()
        advanceUntilIdle()
        assertFalse(vm.state.value.channelLoadFailed)
        assertEquals("c1", vm.state.value.currentChannelId)
    }

    @Test
    fun bekekenKanaalWordtOnthouden() = runTest(dispatcher) {
        val repo = FakeLiveRepository(sample)
        val vm = viewModel(startChannelId = "c1", repo = repo)
        advanceUntilIdle()
        assertEquals("cat1" to "c1", repo.remembered)

        vm.next()
        assertEquals("cat1" to "c2", repo.remembered)
    }

    /** Groep cat1 met c1..c3 plus een tweede groep cat2 met d1..d2. */
    private fun twoGroupRepo(): FakeLiveRepository =
        FakeLiveRepository(sample).apply {
            categories = listOf(
                LiveCategory("cat1", "Een"),
                LiveCategory("cat2", "Twee"),
            )
            channelsByCategory = mapOf(
                "cat1" to sample,
                "cat2" to listOf(
                    LiveChannel("d1", "Sport 1", null, "cat2", null),
                    LiveChannel("d2", "Sport 2", null, "cat2", null),
                ),
            )
        }

    @Test
    fun andereGroepBekijkenLaatHetBeeldStaan() = runTest(dispatcher) {
        // De hele reden voor de scheiding tussen bekeken en spelende groep:
        // rondkijken mag geen stream starten, want dat kost bij een
        // verbindingslimiet een slot en toont iets dat niemand koos.
        val vm = viewModel(startChannelId = "c1", repo = twoGroupRepo())
        advanceUntilIdle()

        vm.browseCategory("cat2")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("c1", s.currentChannelId)
        assertEquals("stream://c1", s.streamUrl)
        assertEquals("cat2", s.browseCategoryId)
        assertEquals(listOf("d1", "d2"), s.channels.map { it.id })
    }

    @Test
    fun zenderUitAndereGroepGaatSpelenEnZaptDaarVerder() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "c1", repo = twoGroupRepo())
        advanceUntilIdle()
        vm.browseCategory("cat2")
        advanceUntilIdle()

        vm.playFromOverlay("d1")
        assertEquals("d1", vm.state.value.currentChannelId)

        // Zappen hoort nu in de nieuwe groep te gebeuren, niet in de oude.
        vm.next()
        assertEquals("d2", vm.state.value.currentChannelId)
        assertFalse(vm.state.value.hasNext)
    }

    @Test
    fun groepwisselVergeetDeVorigeZender() = runTest(dispatcher) {
        // previousIndex wees in de oude lijst; zonder vergeten zou "vorige
        // zender" een willekeurige andere zender aanwijzen.
        val vm = viewModel(startChannelId = "c1", repo = twoGroupRepo())
        advanceUntilIdle()
        vm.selectChannel("c3")
        assertTrue(vm.state.value.hasLast)

        vm.browseCategory("cat2")
        advanceUntilIdle()
        vm.playFromOverlay("d2")

        assertFalse(vm.state.value.hasLast)
        assertEquals("d2", vm.state.value.currentChannelId)
    }

    @Test
    fun nieuweGroepWordtOnthoudenVoorDeVolgendeStart() = runTest(dispatcher) {
        val repo = twoGroupRepo()
        val vm = viewModel(startChannelId = "c1", repo = repo)
        advanceUntilIdle()
        assertEquals("cat1" to "c1", repo.remembered)

        vm.browseCategory("cat2")
        advanceUntilIdle()
        // Rondkijken onthoudt niets; alleen echt kijken.
        assertEquals("cat1" to "c1", repo.remembered)

        vm.playFromOverlay("d2")
        assertEquals("cat2" to "d2", repo.remembered)
    }

    @Test
    fun overzichtSluitenZetDeBekekenGroepTerug() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "c1", repo = twoGroupRepo())
        advanceUntilIdle()
        vm.browseCategory("cat2")
        advanceUntilIdle()

        vm.resetBrowseToPlaying()

        val s = vm.state.value
        assertEquals("cat1", s.browseCategoryId)
        assertEquals(listOf("c1", "c2", "c3"), s.channels.map { it.id })
    }

    @Test
    fun mislukteGroepMeldtInHetOverzichtEnNietOverHetBeeld() =
        runTest(dispatcher) {
            // Een melding over het hele beeld zou onjuist zijn: er speelt
            // gewoon iets, alleen de opgevraagde lijst kwam niet binnen.
            val repo = twoGroupRepo().apply {
                channelsByCategory = mapOf("cat1" to sample)
                channelResult = AppResult.Failure(AppError.NetworkUnavailable)
            }
            val vm = viewModel(startChannelId = "c1", repo = repo)
            advanceUntilIdle()

            vm.browseCategory("cat2")
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.browseFailed)
            assertFalse(s.browseLoading)
            assertFalse(s.channelLoadFailed)
            assertEquals("c1", s.currentChannelId)
        }

    private fun prog(title: String, startMs: Long, endMs: Long) =
        EpgProgramme(title, "", startMs, endMs)

    /**
     * Een uitzending die NU bezig is. Nodig waar het erom gaat dat een
     * bruikbare gids niet opnieuw opgehaald wordt: met een programma uit
     * 1970 is de gids altijd verlopen, en dan slaagt zo'n test om de
     * verkeerde reden.
     */
    private fun lopendProg(title: String): EpgProgramme {
        val nu = System.currentTimeMillis()
        val uur = 60 * 60 * 1000L
        return EpgProgramme(title, "", nu - uur, nu + uur)
    }

    @Test
    fun gidsWordtPasOpVerzoekOpgehaald() = runTest(dispatcher) {
        // Bij elke zender meteen ophalen zou doorzappen in een reeks
        // aanvragen laten eindigen voor gidsen die niemand bekijkt.
        val epg = FakeEpgRepository().apply {
            byChannel = mapOf("c1" to listOf(prog("Journaal", 0, 1000)))
        }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        assertTrue(epg.requests.isEmpty())

        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        assertEquals(listOf("c1"), epg.requests)
        assertEquals(1, vm.state.value.epgProgrammes?.size)
        assertFalse(vm.state.value.epgLoading)
    }

    @Test
    fun tweedeKeerOpenenHaaltNietOpnieuwOp() = runTest(dispatcher) {
        val epg = FakeEpgRepository().apply {
            byChannel = mapOf("c1" to listOf(lopendProg("Journaal")))
        }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        assertEquals(1, epg.requests.size)
    }

    @Test
    fun zappenGooitDeGidsVanDeVorigeZenderWeg() = runTest(dispatcher) {
        val epg = FakeEpgRepository().apply {
            byChannel = mapOf("c1" to listOf(prog("Journaal", 0, 1000)))
        }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.epgProgrammes?.size)

        vm.next()
        assertNull(vm.state.value.epgProgrammes)
        assertFalse(vm.state.value.epgLoading)
        assertFalse(vm.state.value.epgFailed)
    }

    @Test
    fun zappenAnnuleertEenLopendeGidsaanvraag() = runTest(dispatcher) {
        val epg = FakeEpgRepository().apply {
            byChannel = mapOf("c1" to listOf(prog("Journaal", 0, 1000)))
            delayMs = 5_000L
        }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceTimeBy(1_000L)

        vm.next()
        assertEquals("c2", vm.state.value.currentChannelId)
        advanceUntilIdle()

        assertNull(vm.state.value.epgProgrammes)
        assertFalse(vm.state.value.epgFailed)
    }

    @Test
    fun laatAntwoordNaZappenKomtNietBijDeNieuweZender() = runTest(dispatcher) {
        // Het echte risico: je vraagt de gids van c1, zapt naar c2, en het
        // trage antwoord van c1 landt daarna bij c2 in beeld.
        //
        // Hier wacht de fake NIET-annuleerbaar, want anders bewijst de test
        // alleen dat zappen de job annuleert. Annuleren komt pas aan op een
        // suspension-punt, dus de controle na het ophalen is de tweede slot
        // op de deur en moet apart bewezen worden.
        val epg = FakeEpgRepository().apply {
            byChannel = mapOf("c1" to listOf(prog("Journaal", 0, 1000)))
            delayMs = 5_000L
            uncancellableDelay = true
        }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceTimeBy(1_000L)

        vm.next()
        assertEquals("c2", vm.state.value.currentChannelId)
        advanceUntilIdle()

        assertNull(vm.state.value.epgProgrammes)
        assertFalse(vm.state.value.epgFailed)
    }

    @Test
    fun mislukteGidsIsGeenGidsloosEnRaaktHetBeeldNiet() = runTest(dispatcher) {
        // "Geen gids" tonen terwijl het ophalen faalde is onjuist, en een
        // melding over het hele beeld hoort hier al helemaal niet.
        val epg = FakeEpgRepository().apply { fail = true }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.epgFailed)
        assertNull(s.epgProgrammes)
        assertFalse(s.epgLoading)
        assertFalse(s.channelLoadFailed)
        assertEquals("c1", s.currentChannelId)
    }

    @Test
    fun naEenMislukteGidsMagOpnieuwWordenGeprobeerd() = runTest(dispatcher) {
        val epg = FakeEpgRepository().apply { fail = true }
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        assertTrue(vm.state.value.epgFailed)

        epg.fail = false
        epg.byChannel = mapOf("c1" to listOf(prog("Journaal", 0, 1000)))
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        assertFalse(vm.state.value.epgFailed)
        assertEquals(1, vm.state.value.epgProgrammes?.size)
    }

    @Test
    fun zenderZonderGidsGeeftEenLegeLijstEnGeenFout() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()

        assertEquals(emptyList<EpgProgramme>(), vm.state.value.epgProgrammes)
        assertFalse(vm.state.value.epgFailed)
    }

    @Test
    fun lastChannelWisseltHeenEnWeer() = runTest(dispatcher) {
        val vm = viewModel(startChannelId = "c1")
        advanceUntilIdle()

        // Geen geschiedenis aan de start.
        assertFalse(vm.state.value.hasLast)

        vm.selectChannel("c3")
        assertTrue(vm.state.value.hasLast)

        vm.lastChannel()
        assertEquals("c1", vm.state.value.currentChannelId)

        vm.lastChannel()
        assertEquals("c3", vm.state.value.currentChannelId)
    }

    @Test
    fun overzichtVultDeGidsPerZender() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        epg.byChannel = mapOf("c2" to listOf(programma("Journaal")))
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        assertEquals("Journaal", vm.overlayEpg.value["c2"]?.first()?.title)
        // De spelende zender blijft ongemoeid: de rijen en de balk hebben
        // ieder hun eigen toestand.
        assertNull(vm.state.value.epgProgrammes)
    }

    @Test
    fun dezelfdeRijVraagtDeGidsMaarEenKeerOp() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        vm.loadOverlayEpg("c2")
        advanceUntilIdle()
        // Heen en weer scrollen laat dezelfde rij opnieuw in beeld komen.
        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        assertEquals(listOf("c2"), epg.requests)
    }

    @Test
    fun eenZenderZonderGidsWordtNietNogEensGevraagd() = runTest(dispatcher) {
        val epg = FakeEpgRepository() // byChannel leeg = geslaagd maar leeg
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        vm.loadOverlayEpg("c2")
        advanceUntilIdle()
        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        // Bij een provider zonder EPG zou elke scrollbeweging anders een
        // reeks aanvragen opleveren die nooit iets anders teruggeeft.
        assertEquals(listOf("c2"), epg.requests)
    }

    @Test
    fun wegscrollenVoorHetAntwoordLaatEenNieuwePogingToe() =
        runTest(dispatcher) {
            val epg = FakeEpgRepository()
            epg.delayMs = 1000
            val vm = viewModel(startChannelId = "c1", epg = epg)
            advanceUntilIdle()

            val job = launch { vm.loadOverlayEpg("c2") }
            advanceTimeBy(100)
            // De rij scrolt uit beeld voordat het antwoord er is.
            job.cancel()
            advanceUntilIdle()
            assertNull(vm.overlayEpg.value["c2"])

            // Terugscrollen moet het gewoon opnieuw proberen. Zonder het
            // teruggeven van de reservering blijft deze rij voorgoed leeg.
            epg.delayMs = 0
            vm.loadOverlayEpg("c2")
            advanceUntilIdle()

            assertEquals(listOf("c2", "c2"), epg.requests)
        }

    @Test
    fun hoogstensVierAanvragenTegelijk() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        epg.delayMs = 1000
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        // Snel doorscrollen: twintig rijen komen vlak na elkaar in beeld.
        repeat(20) { i -> launch { vm.loadOverlayEpg("kanaal$i") } }
        advanceUntilIdle()

        assertEquals(20, epg.requests.size)
        assertEquals(4, epg.maxInFlight)
    }

    @Test
    fun eenMislukteAanvraagLaatDeRijLeeg() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        epg.fail = true
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        // Geen melding per rij: een lijst vol foutregels is onleesbaar en
        // het beeld loopt gewoon door.
        assertNull(vm.overlayEpg.value["c2"])
    }

    @Test
    fun naEenFoutVerschijntHetProgrammaAlsnog() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        epg.byChannel = mapOf("c2" to listOf(programma("Journaal")))
        epg.fail = true
        // Wachttijd nul: de poort staat een nieuwe poging meteen toe, zodat
        // dit zonder klok te sturen is. Dat de wachttijd zelf klopt staat
        // in EpgRequestGateTest.
        val vm = viewModel(
            startChannelId = "c1",
            epg = epg,
            gate = EpgRequestGate(retryAfterMs = 0L),
        )
        advanceUntilIdle()

        // De aanvraag mislukt: de rij blijft leeg.
        vm.loadOverlayEpg("c2")
        advanceUntilIdle()
        assertNull(vm.overlayEpg.value["c2"])

        // De provider herstelt en de rij komt opnieuw in beeld.
        epg.fail = false
        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        // Dan hoort het programma er alsnog te staan. Eén hapering mag geen
        // zender opleveren die tot het afsluiten van de app leeg blijft.
        assertEquals("Journaal", vm.overlayEpg.value["c2"]?.first()?.title)
        assertEquals(listOf("c2", "c2"), epg.requests)
    }

    @Test
    fun eenAfgelopenGidsWordtVernieuwd() = runTest(dispatcher) {
        val nu = System.currentTimeMillis()
        val uur = 60 * 60 * 1000L
        val epg = FakeEpgRepository()
        // Alles al afgelopen: de korte gids beslaat maar een paar
        // uitzendingen en kan dus helemaal in het verleden liggen.
        epg.byChannel = mapOf(
            "c2" to listOf(EpgProgramme("Oud", "", nu - 2 * uur, nu - uur)),
        )
        val vm = viewModel(
            startChannelId = "c1",
            epg = epg,
            gate = EpgRequestGate(retryAfterMs = 0L),
        )
        advanceUntilIdle()

        vm.loadOverlayEpg("c2")
        advanceUntilIdle()
        assertEquals("Oud", vm.overlayEpg.value["c2"]?.first()?.title)

        // De rij staat in beeld en vraagt het opnieuw. "Al opgehaald" mag
        // dan niet blijven blokkeren, want wat er ligt zegt niets over nu.
        epg.byChannel = mapOf(
            "c2" to listOf(EpgProgramme("Nieuw", "", nu - uur, nu + uur)),
        )
        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        assertEquals("Nieuw", vm.overlayEpg.value["c2"]?.first()?.title)
        assertEquals(listOf("c2", "c2"), epg.requests)
    }

    @Test
    fun deBalkHaaltEenAfgelopenGidsOpnieuwOp() = runTest(dispatcher) {
        val nu = System.currentTimeMillis()
        val uur = 60 * 60 * 1000L
        val epg = FakeEpgRepository()
        epg.byChannel = mapOf(
            "c1" to listOf(EpgProgramme("Oud", "", nu - 2 * uur, nu - uur)),
        )
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        assertEquals("Oud", vm.state.value.epgProgrammes?.first()?.title)

        // Blijf je op dezelfde zender tot alle opgeslagen programma's
        // afgelopen zijn, dan zou de balk bij elke druk op OK "Geen gids"
        // tonen terwijl opnieuw ophalen het antwoord wel geeft. "Al in
        // huis" is daarom geen geldige reden om te blijven zwijgen.
        epg.byChannel = mapOf(
            "c1" to listOf(EpgProgramme("Nieuw", "", nu - uur, nu + uur)),
        )
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()

        assertEquals("Nieuw", vm.state.value.epgProgrammes?.first()?.title)
        assertEquals(listOf("c1", "c1"), epg.requests)
    }

    @Test
    fun deBalkVraagtEenLegeGidsNietMeteenOpnieuw() = runTest(dispatcher) {
        // Leeg betekent: deze zender heeft geen gids. Dat blijft staan tot
        // de cache-TTL, anders levert elke druk op OK een aanvraag op die
        // toch hetzelfde teruggeeft.
        val epg = FakeEpgRepository() // byChannel leeg = geslaagd maar leeg
        val vm = viewModel(startChannelId = "c1", epg = epg)
        advanceUntilIdle()

        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()
        vm.loadEpgForCurrentChannel()
        advanceUntilIdle()

        assertEquals(listOf("c1"), epg.requests)
    }

    @Test
    fun eenZenderZonderGidsTeltNietAlsVerlopen() = runTest(dispatcher) {
        // Zelfs met wachttijd nul mag een leeg antwoord geen herhaling
        // uitlokken. Leeg is geen verouderde gids maar een zender zonder
        // gids, en de rij vraagt het elke 30 seconden opnieuw; zou leeg wel
        // als verlopen tellen, dan is dat bij een provider zonder EPG een
        // aanvraag per zichtbare rij per halve minuut, eindeloos.
        val epg = FakeEpgRepository() // byChannel leeg = geslaagd maar leeg
        val vm = viewModel(
            startChannelId = "c1",
            epg = epg,
            gate = EpgRequestGate(retryAfterMs = 0L),
        )
        advanceUntilIdle()

        vm.loadOverlayEpg("c2")
        advanceUntilIdle()
        vm.loadOverlayEpg("c2")
        advanceUntilIdle()

        assertEquals(listOf("c2"), epg.requests)
    }
}

private fun programma(title: String) =
    EpgProgramme(title, "", 0L, 60_000L)

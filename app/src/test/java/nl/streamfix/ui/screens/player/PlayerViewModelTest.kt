package nl.streamfix.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import nl.streamfix.domain.usecase.GetLiveCastUrlUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.RememberLastChannelUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}

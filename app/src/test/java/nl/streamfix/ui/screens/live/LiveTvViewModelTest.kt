package nl.streamfix.ui.screens.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.streamfix.data.local.AdultState
import nl.streamfix.data.local.AdultVisibility
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AccountInfo
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.repository.AuthRepository
import nl.streamfix.domain.repository.EpgRepository
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.usecase.GetActiveAccountUseCase
import nl.streamfix.domain.usecase.GetChannelEpgUseCase
import nl.streamfix.domain.usecase.GetLastWatchedChannelUseCase
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.usecase.SetFavoriteUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.domain.util.EpgRequestGate
import nl.streamfix.ui.ErrorTexts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private fun account(id: String) = Account.Xtream(
    id = id,
    displayName = id,
    serverUrl = "http://$id.voorbeeld.nl",
    username = "u",
    password = "p",
)

private class FakeAuthRepository(first: Account?) : AuthRepository {
    val active = MutableStateFlow(first)

    override fun observeActiveAccount(): Flow<Account?> = active

    override suspend fun loginWithXtream(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<Account.Xtream> = AppResult.Failure(AppError.Unknown)

    override suspend fun getActiveAccount(): Account? = active.value
    override suspend fun getAccounts(): List<Account> = listOfNotNull(active.value)
    override suspend fun switchActiveAccount(id: String) = Unit
    override suspend fun removeAccount(id: String) = Unit
    override suspend fun setStreamFormat(format: String) = Unit
    override suspend fun getAccountInfo(): AccountInfo? = null
    override suspend fun verifyActiveAccount(): AppResult<Unit> =
        AppResult.Success(Unit)
    override suspend fun logout() = Unit
}

private class FakeLiveRepository : LiveRepository {
    var categories: List<LiveCategory> = listOf(LiveCategory("cat1", "Groep"))
    var channelResult: AppResult<List<LiveChannel>> = AppResult.Success(
        listOf(LiveChannel("c1", "Een", null, "cat1", null)),
    )

    override suspend fun getCategories(): AppResult<List<LiveCategory>> =
        AppResult.Success(categories)

    override suspend fun getChannels(
        categoryId: String?,
    ): AppResult<List<LiveChannel>> = channelResult

    override fun observeFavorites(): Flow<List<LiveChannel>> = flowOf(emptyList())
    override suspend fun setFavorite(channel: LiveChannel, favorite: Boolean) = Unit
    override fun streamUrl(channelId: String): String? = null
    override fun streamUrlForCast(channelId: String): String? = null
    override fun timeshiftUrl(
        channelId: String,
        startMs: Long,
        durationMin: Int,
    ): String? = null

    override fun rememberLastChannel(categoryId: String, channel: LiveChannel) = Unit
    override fun lastWatchedChannel(): Pair<String, String>? = null
}

private class FakeEpgRepository : EpgRepository {
    var byChannel: Map<String, List<EpgProgramme>> = emptyMap()
    var fail = false
    var delayMs = 0L
    val requests = mutableListOf<String>()

    override suspend fun getEpg(
        streamId: String,
    ): AppResult<List<EpgProgramme>> {
        requests += streamId
        val shouldFail = fail
        val answer = byChannel[streamId].orEmpty()
        if (delayMs > 0L) delay(delayMs)
        return if (shouldFail) {
            AppResult.Failure(AppError.NetworkUnavailable)
        } else {
            AppResult.Success(answer)
        }
    }

    override suspend fun getEpgTable(
        streamId: String,
    ): AppResult<List<EpgProgramme>> = AppResult.Success(emptyList())
}

private class FakeAdultVisibility : AdultVisibility {
    private val _state = MutableStateFlow(
        AdultState(hasPin = false, hidden = false, unlocked = true),
    )
    override val adultState: StateFlow<AdultState> = _state.asStateFlow()
}

/** Geen resources nodig: de fout zelf is de tekst. */
private class FakeErrorTexts : ErrorTexts {
    override fun of(error: AppError): String = "fout:$error"
}

/**
 * Rechtstreekse tests op het hoofdzenderoverzicht. Konden er eerder niet
 * zijn: dit ViewModel vroeg een Context voor de foutteksten en de
 * versleutelde instellingenstore voor de volwassen-zichtbaarheid. Beide
 * zijn nu een interface, dus het draait hier zonder Robolectric en zonder
 * mock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        live: FakeLiveRepository = FakeLiveRepository(),
        epg: FakeEpgRepository = FakeEpgRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(account("a")),
        gate: EpgRequestGate = EpgRequestGate(),
        errorTexts: ErrorTexts = FakeErrorTexts(),
    ) = LiveTvViewModel(
        getCategories = GetLiveCategoriesUseCase(live),
        getChannels = GetLiveChannelsUseCase(live),
        observeFavorites = ObserveFavoritesUseCase(live),
        setFavorite = SetFavoriteUseCase(live),
        getChannelEpg = GetChannelEpgUseCase(epg),
        epgGate = gate,
        getActiveAccount = GetActiveAccountUseCase(auth),
        getLastWatched = GetLastWatchedChannelUseCase(live),
        adultVisibility = FakeAdultVisibility(),
        errorTexts = errorTexts,
    )

    @Test
    fun naEenFoutVultEenNieuwePogingDeRijAlsnog() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        epg.byChannel = mapOf("c1" to listOf(programma("Journaal")))
        epg.fail = true
        // Wachttijd nul staat de nieuwe poging meteen toe; dat de wachttijd
        // zelf klopt ligt vast in EpgRequestGateTest.
        val vm = viewModel(epg = epg, gate = EpgRequestGate(retryAfterMs = 0L))
        advanceUntilIdle()

        vm.ensureEpg("c1")
        advanceUntilIdle()
        assertNull(vm.epg.value["c1"])

        // De provider herstelt en de rij staat nog in beeld, dus vraagt
        // opnieuw. Eén hapering mag geen zender opleveren die de rest van
        // de sessie zonder programma-informatie blijft.
        epg.fail = false
        vm.ensureEpg("c1")
        advanceUntilIdle()

        assertEquals("Journaal", vm.epg.value["c1"]?.first()?.title)
        assertEquals(listOf("c1", "c1"), epg.requests)
    }

    @Test
    fun eenAntwoordVanHetVorigeNetwerkKomtNietBinnen() = runTest(dispatcher) {
        val epg = FakeEpgRepository()
        epg.byChannel = mapOf("c1" to listOf(programma("Oud netwerk")))
        epg.delayMs = 1000
        val auth = FakeAuthRepository(account("a"))
        val vm = viewModel(epg = epg, auth = auth)
        advanceUntilIdle()

        // Aanvraag onderweg bij netwerk A.
        launch { vm.ensureEpg("c1") }
        advanceTimeBy(100)

        // Kijker wisselt naar netwerk B voordat het antwoord er is.
        auth.active.value = account("b")
        advanceUntilIdle()

        // Het antwoord van A mag niet alsnog bij een zender van B komen te
        // staan. Leegmaken van de map is daarvoor niet genoeg: tussen het
        // antwoord en het wegschrijven zit geen opschortpunt, dus
        // annulering kan daar niet landen.
        assertNull(vm.epg.value["c1"])
    }

    @Test
    fun eenLaadfoutLevertDeTekstVanDeVertaler() = runTest(dispatcher) {
        val live = FakeLiveRepository()
        live.channelResult = AppResult.Failure(AppError.ServerUnreachable)
        val vm = viewModel(live = live)
        advanceUntilIdle()

        assertEquals(
            "fout:${AppError.ServerUnreachable}",
            vm.state.value.errorMessage,
        )
    }
}

private fun programma(title: String) =
    EpgProgramme(title, "", 0L, 60_000L)

package nl.streamfix.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.ErrorTexts
import nl.streamfix.ui.screens.catchup.CatchupViewModel
import nl.streamfix.ui.screens.nowontv.NowOnTvViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private fun account(id: String) = Account.Xtream(id, id, "https://example.test", "user", "password")

private fun channel(id: String, archiveDays: Int = 7) =
    LiveChannel(id, id, null, "cat", null, archiveDays = archiveDays)

private fun programme(title: String) = EpgProgramme(title, "", 0, Long.MAX_VALUE)

private class OverviewAuth : AuthRepository {
    val active = MutableStateFlow<Account?>(account("a"))
    override fun observeActiveAccount(): Flow<Account?> = active
    override suspend fun getActiveAccount(): Account? = active.value
    override suspend fun getAccounts(): List<Account> = listOfNotNull(active.value)
    override suspend fun loginWithXtream(name: String, serverUrl: String, username: String, password: String): AppResult<Account.Xtream> =
        AppResult.Failure(AppError.Unknown)
    override suspend fun switchActiveAccount(id: String) = Unit
    override suspend fun removeAccount(id: String) = Unit
    override suspend fun setStreamFormat(format: String) = Unit
    override suspend fun getAccountInfo(): AccountInfo? = null
    override suspend fun verifyActiveAccount(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun logout() = Unit
}

private class OverviewLive(private val auth: OverviewAuth) : LiveRepository {
    var favorites: List<LiveChannel>? = null
    var categories: suspend () -> AppResult<List<LiveCategory>> = {
        AppResult.Success(listOf(LiveCategory("cat", auth.active.value?.id.orEmpty())))
    }
    var channels: suspend () -> AppResult<List<LiveChannel>> = {
        AppResult.Success(listOf(channel(auth.active.value?.id.orEmpty())))
    }
    val channelRequests = mutableListOf<String?>()

    override suspend fun getCategories(): AppResult<List<LiveCategory>> = categories()
    override suspend fun getChannels(categoryId: String?): AppResult<List<LiveChannel>> {
        channelRequests += auth.active.value?.id
        return channels()
    }
    override fun observeFavorites(): Flow<List<LiveChannel>> =
        flowOf(favorites ?: listOf(channel(auth.active.value?.id.orEmpty())))
    override suspend fun setFavorite(channel: LiveChannel, favorite: Boolean) = Unit
    override fun streamUrl(channelId: String): String? = null
    override fun streamUrlForCast(channelId: String): String? = null
    override fun timeshiftUrl(channelId: String, startMs: Long, durationMin: Int): String? = null
    override fun rememberLastChannel(categoryId: String, channel: LiveChannel) = Unit
    override fun lastWatchedChannel(): Pair<String, String>? = null
}

private class OverviewEpg : EpgRepository {
    val requests = mutableListOf<String>()
    var read: suspend (String) -> AppResult<List<EpgProgramme>> = {
        AppResult.Success(listOf(programme(it)))
    }
    override suspend fun getEpg(streamId: String): AppResult<List<EpgProgramme>> {
        requests += streamId
        return read(streamId)
    }
    override suspend fun getEpgTable(streamId: String): AppResult<List<EpgProgramme>> = AppResult.Success(emptyList())
}

private class OverviewAdultVisibility : AdultVisibility {
    override val adultState = MutableStateFlow(AdultState(hasPin = true, hidden = true, unlocked = false))
}

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewLoadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = OverviewAuth()
    private val live = OverviewLive(auth)
    private val epg = OverviewEpg()
    private val adult = OverviewAdultVisibility()
    private val models = mutableListOf<ViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun nowOnTv() = NowOnTvViewModel(
        ObserveFavoritesUseCase(live), GetChannelEpgUseCase(epg), GetActiveAccountUseCase(auth),
    ).also { models += it }

    private fun catchup() = CatchupViewModel(
        GetLiveChannelsUseCase(live), GetLiveCategoriesUseCase(live), GetActiveAccountUseCase(auth), adult,
        object : ErrorTexts {
            override fun of(error: AppError): String = "error:$error"
        },
    ).also { models += it }

    @Test
    fun nowOnTvLimitsRequestsToFourAndKeepsFavoriteOrder() = runTest(dispatcher) {
        val favorites = (1..40).map { channel(it.toString()) }
        live.favorites = favorites
        var running = 0
        var maximum = 0
        epg.read = { id ->
            running++
            maximum = maxOf(maximum, running)
            try {
                delay(100)
                AppResult.Success(listOf(programme(id)))
            } finally {
                running--
            }
        }
        val model = nowOnTv()
        runCurrent()
        assertEquals(4, running)
        assertEquals(4, epg.requests.size)
        advanceUntilIdle()
        assertEquals(4, maximum)
        assertEquals(40, epg.requests.size)
        assertEquals(favorites, model.state.value.items.map { it.channel })
        assertEquals(favorites.map { it.id }, model.state.value.items.map { it.nowTitle })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun nowOnTvKeepsChannelWithUnavailableEpg() = runTest(dispatcher) {
        live.favorites = listOf(channel("no-guide"), channel("with-guide"))
        epg.read = { id ->
            if (id == "no-guide") AppResult.Failure(AppError.NetworkUnavailable)
            else AppResult.Success(listOf(programme(id)))
        }
        val model = nowOnTv()
        advanceUntilIdle()
        assertEquals(2, model.state.value.items.size)
        assertNull(model.state.value.items.first().nowTitle)
        assertEquals("with-guide", model.state.value.items.last().nowTitle)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun nowOnTvDiscardsOldProviderResponseEvenWhenRequestIgnoresCancellation() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        epg.read = { id ->
            if (id == "a") withContext(NonCancellable) { oldResult.await() }
            AppResult.Success(listOf(programme(id)))
        }
        val model = nowOnTv()
        runCurrent()
        auth.active.value = account("b")
        runCurrent()
        assertEquals(listOf("b"), model.state.value.items.map { it.channel.id })
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("b"), model.state.value.items.map { it.channel.id })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun nowOnTvLogoutCancelsWaitingRequestsAndKeepsEmptyState() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        live.favorites = (1..10).map { channel(it.toString()) }
        epg.read = { id ->
            withContext(NonCancellable) { oldResult.await() }
            AppResult.Success(listOf(programme(id)))
        }
        val model = nowOnTv()
        runCurrent()
        assertEquals(4, epg.requests.size)
        auth.active.value = null
        runCurrent()
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(4, epg.requests.size)
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun catchupDiscardsOldCategoriesBeforeRequestingNewChannels() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        live.categories = {
            val id = auth.active.value?.id.orEmpty()
            if (id == "a") withContext(NonCancellable) { oldResult.await() }
            AppResult.Success(listOf(LiveCategory("cat", id)))
        }
        val model = catchup()
        runCurrent()
        auth.active.value = account("b")
        runCurrent()
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("b"), live.channelRequests)
        assertEquals(listOf("b"), model.state.value.categories.map { it.name })
        assertEquals(listOf("b"), model.state.value.visibleChannels.map { it.id })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun catchupDiscardsOldChannelSuccess() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        live.channels = {
            val id = auth.active.value?.id.orEmpty()
            if (id == "a") withContext(NonCancellable) { oldResult.await() }
            AppResult.Success(listOf(channel(id)))
        }
        val model = catchup()
        runCurrent()
        auth.active.value = account("b")
        runCurrent()
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("b"), model.state.value.visibleChannels.map { it.id })
        assertNull(model.state.value.errorMessage)
    }

    @Test
    fun catchupDiscardsOldChannelFailure() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        live.channels = {
            if (auth.active.value?.id == "a") {
                withContext(NonCancellable) { oldResult.await() }
                AppResult.Failure(AppError.NetworkUnavailable)
            } else AppResult.Success(listOf(channel("b")))
        }
        val model = catchup()
        runCurrent()
        auth.active.value = account("b")
        runCurrent()
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("b"), model.state.value.visibleChannels.map { it.id })
        assertNull(model.state.value.errorMessage)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun catchupFilterChangeDiscardsOldUnfilteredResponse() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        adult.adultState.value = AdultState(true, true, true)
        live.channels = {
            val unlocked = adult.adultState.value.unlocked
            if (unlocked) withContext(NonCancellable) { oldResult.await() }
            AppResult.Success(listOf(channel(if (unlocked) "adult" else "safe")))
        }
        val model = catchup()
        runCurrent()
        adult.adultState.value = AdultState(true, true, false)
        runCurrent()
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("safe"), model.state.value.visibleChannels.map { it.id })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun catchupLogoutDiscardsPendingResponseAndDoesNotReloadOnFilterChange() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<Unit>()
        live.channels = {
            withContext(NonCancellable) { oldResult.await() }
            AppResult.Success(listOf(channel("old")))
        }
        val model = catchup()
        runCurrent()
        auth.active.value = null
        runCurrent()
        adult.adultState.value = AdultState(true, true, true)
        runCurrent()
        oldResult.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, live.channelRequests.size)
        assertTrue(model.state.value.categories.isEmpty())
        assertTrue(model.state.value.visibleChannels.isEmpty())
        assertFalse(model.state.value.isLoading)
        assertNull(model.state.value.errorMessage)
    }
}

package nl.streamfix.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.usecase.GetLiveCastUrlUseCase
import nl.streamfix.domain.usecase.GetLiveChannelsUseCase
import nl.streamfix.domain.usecase.GetStreamUrlUseCase
import nl.streamfix.domain.usecase.ObserveFavoritesUseCase
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
    override suspend fun getCategories(): AppResult<List<LiveCategory>> =
        AppResult.Success(emptyList())

    override suspend fun getChannels(
        categoryId: String?,
    ): AppResult<List<LiveChannel>> = AppResult.Success(channels)

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
        channels: List<LiveChannel> = sample,
    ): PlayerViewModel {
        val repo = FakeLiveRepository(channels)
        val handle = SavedStateHandle(
            mapOf(
                Routes.PLAYER_ARG_CATEGORY to "cat1",
                Routes.PLAYER_ARG_CHANNEL to startChannelId,
            ),
        )
        return PlayerViewModel(
            savedStateHandle = handle,
            getChannels = GetLiveChannelsUseCase(repo),
            observeFavorites = ObserveFavoritesUseCase(repo),
            getStreamUrl = GetStreamUrlUseCase(repo),
            getCastUrl = GetLiveCastUrlUseCase(repo),
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

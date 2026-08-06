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
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.model.SeriesDetail
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.model.VodDetail
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.repository.PlaybackRepository
import nl.streamfix.domain.repository.SeriesRepository
import nl.streamfix.domain.repository.VodRepository
import nl.streamfix.domain.usecase.GetEpisodeStreamUrlUseCase
import nl.streamfix.domain.usecase.GetResumePositionUseCase
import nl.streamfix.domain.usecase.GetTimeshiftUrlUseCase
import nl.streamfix.domain.usecase.GetVodStreamUrlUseCase
import nl.streamfix.domain.usecase.SaveResumePositionUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeVodRepository(var url: String? = "stream://vod") : VodRepository {
    override suspend fun getCategories(): AppResult<List<LiveCategory>> =
        AppResult.Success(emptyList())

    override suspend fun getItems(categoryId: String): AppResult<List<VodItem>> =
        AppResult.Success(emptyList())

    override suspend fun getAllItems(): AppResult<List<VodItem>> =
        AppResult.Success(emptyList())

    override suspend fun getDetail(vodId: String): AppResult<VodDetail> =
        AppResult.Failure(AppError.Unknown)

    var lastArgs: Pair<String, String>? = null

    override fun streamUrl(vodId: String, extension: String): String? {
        lastArgs = vodId to extension
        return url
    }
}

private class FakeSeriesRepository(
    var url: String? = "stream://episode",
) : SeriesRepository {
    override suspend fun getCategories(): AppResult<List<LiveCategory>> =
        AppResult.Success(emptyList())

    override suspend fun getItems(categoryId: String): AppResult<List<SeriesItem>> =
        AppResult.Success(emptyList())

    override suspend fun getAllItems(): AppResult<List<SeriesItem>> =
        AppResult.Success(emptyList())

    override suspend fun getDetail(seriesId: String): AppResult<SeriesDetail> =
        AppResult.Failure(AppError.Unknown)

    var lastArgs: Pair<String, String>? = null

    override fun episodeStreamUrl(episodeId: String, extension: String): String? {
        lastArgs = episodeId to extension
        return url
    }
}

private class FakeTimeshiftRepository(
    var timeshift: String? = "stream://timeshift",
) : LiveRepository {
    override suspend fun getCategories(): AppResult<List<LiveCategory>> =
        AppResult.Success(emptyList())

    override suspend fun getChannels(
        categoryId: String?,
    ): AppResult<List<LiveChannel>> = AppResult.Success(emptyList())

    override fun observeFavorites(): Flow<List<LiveChannel>> =
        flowOf(emptyList())

    override suspend fun setFavorite(channel: LiveChannel, favorite: Boolean) =
        Unit

    override fun streamUrl(channelId: String): String? = null
    override fun streamUrlForCast(channelId: String): String? = null

    var lastTimeshiftArgs: Triple<String, Long, Int>? = null

    override fun timeshiftUrl(
        channelId: String,
        startMs: Long,
        durationMin: Int,
    ): String? {
        lastTimeshiftArgs = Triple(channelId, startMs, durationMin)
        return timeshift
    }

    override fun rememberLastChannel(categoryId: String, channel: LiveChannel) =
        Unit

    override fun lastWatchedChannel(): Pair<String, String>? = null
}

private class FakePlaybackRepository(
    private val position: Long = 0L,
) : PlaybackRepository {
    override suspend fun getPosition(mediaId: String): Long = position
    override suspend fun startWatching(item: HistoryItem) = Unit

    var saved: Pair<String, Long>? = null

    override suspend fun savePosition(mediaId: String, positionMs: Long) {
        saved = mediaId to positionMs
    }

    override fun observeHistory(): Flow<List<HistoryItem>> = flowOf(emptyList())
    override suspend fun removeFromHistory(mediaId: String) = Unit
    override suspend fun clearHistory() = Unit
}

/**
 * Borgt dat de speler zijn stream-URL zelf opbouwt uit de brongegevens uit
 * de route. De volledige URL bevat inloggegevens en mag niet als
 * navigatieargument reizen, omdat die in de saved-instance-state belandt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

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
        args: Map<String, String>,
        vod: FakeVodRepository = FakeVodRepository(),
        series: FakeSeriesRepository = FakeSeriesRepository(),
        playback: FakePlaybackRepository = FakePlaybackRepository(),
        live: FakeTimeshiftRepository = FakeTimeshiftRepository(),
    ) = PlaybackViewModel(
        savedStateHandle = SavedStateHandle(args),
        getResumePosition = GetResumePositionUseCase(playback),
        saveResumePosition = SaveResumePositionUseCase(playback),
        getVodStreamUrl = GetVodStreamUrlUseCase(vod),
        getEpisodeStreamUrl = GetEpisodeStreamUrlUseCase(series),
        getTimeshiftUrl = GetTimeshiftUrlUseCase(live),
    )

    @Test
    fun vodBronWordtLokaalOpgebouwd() = runTest(dispatcher) {
        val vod = FakeVodRepository(url = "stream://vod/42.mkv")
        val vm = viewModel(
            mapOf(
                Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_VOD,
                Routes.PLAYBACK_ARG_CONTENT to "42",
                Routes.PLAYBACK_ARG_EXT to "mkv",
                Routes.PLAYBACK_ARG_TITLE to "Een film",
                Routes.PLAYBACK_ARG_MEDIA to "vod:42",
            ),
            vod = vod,
        )
        advanceUntilIdle()

        assertEquals("42" to "mkv", vod.lastArgs)
        assertEquals("stream://vod/42.mkv", vm.state.value.streamUrl)
        assertEquals("Een film", vm.state.value.title)
        assertTrue(vm.state.value.ready)
        assertFalse(vm.state.value.sourceUnavailable)
        assertTrue(vm.requiresExitConfirmation)
    }

    @Test
    fun afleveringBronWordtLokaalOpgebouwd() = runTest(dispatcher) {
        val series = FakeSeriesRepository(url = "stream://ep/7.mp4")
        val vm = viewModel(
            mapOf(
                Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_EPISODE,
                Routes.PLAYBACK_ARG_CONTENT to "7",
                Routes.PLAYBACK_ARG_EXT to "mp4",
                Routes.PLAYBACK_ARG_MEDIA to "ep:7",
            ),
            series = series,
        )
        advanceUntilIdle()

        assertEquals("7" to "mp4", series.lastArgs)
        assertEquals("stream://ep/7.mp4", vm.state.value.streamUrl)
        assertTrue(vm.requiresExitConfirmation)
    }

    @Test
    fun zonderProviderKomtErEenNetteMelding() = runTest(dispatcher) {
        // streamUrl() geeft null zodra er geen actieve provider is.
        val vm = viewModel(
            mapOf(
                Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_VOD,
                Routes.PLAYBACK_ARG_CONTENT to "42",
                Routes.PLAYBACK_ARG_EXT to "mkv",
            ),
            vod = FakeVodRepository(url = null),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.sourceUnavailable)
        assertEquals("", vm.state.value.streamUrl)
    }

    @Test
    fun hervatpositieWordtGeladenEnOpgeslagen() = runTest(dispatcher) {
        val playback = FakePlaybackRepository(position = 90_000L)
        val vm = viewModel(
            mapOf(
                Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_VOD,
                Routes.PLAYBACK_ARG_CONTENT to "42",
                Routes.PLAYBACK_ARG_EXT to "mkv",
                Routes.PLAYBACK_ARG_MEDIA to "vod:42",
            ),
            playback = playback,
        )
        advanceUntilIdle()
        assertEquals(90_000L, vm.state.value.startPositionMs)

        vm.savePosition(120_000L)
        advanceUntilIdle()
        assertEquals("vod:42" to 120_000L, playback.saved)
    }

    @Test
    fun catchupBronWordtLokaalOpgebouwd() = runTest(dispatcher) {
        val live = FakeTimeshiftRepository(timeshift = "stream://cu/9.ts")
        val vm = viewModel(
            mapOf(
                Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_CATCHUP,
                Routes.PLAYBACK_ARG_CONTENT to "9",
                Routes.PLAYBACK_ARG_START to "1700000000000",
                Routes.PLAYBACK_ARG_DURATION to "45",
                Routes.PLAYBACK_ARG_MEDIA to "catchup:9:1700000000",
            ),
            live = live,
        )
        advanceUntilIdle()

        assertEquals(
            Triple("9", 1_700_000_000_000L, 45),
            live.lastTimeshiftArgs,
        )
        assertEquals("stream://cu/9.ts", vm.state.value.streamUrl)
        assertFalse(vm.state.value.sourceUnavailable)
        assertFalse(vm.requiresExitConfirmation)
    }

    @Test
    fun procesherstelBouwtDeUrlOpnieuwOp() = runTest(dispatcher) {
        // Na een proceskill herstelt Navigation de route met dezelfde
        // argumenten. De URL moet dan opnieuw uit de opslag komen.
        val args = mapOf(
            Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_VOD,
            Routes.PLAYBACK_ARG_CONTENT to "42",
            Routes.PLAYBACK_ARG_EXT to "mkv",
            Routes.PLAYBACK_ARG_MEDIA to "vod:42",
        )
        val vod = FakeVodRepository(url = "stream://vod/42.mkv")
        viewModel(args, vod = vod)
        advanceUntilIdle()

        val herstart = viewModel(
            args,
            vod = vod,
            playback = FakePlaybackRepository(position = 60_000L),
        )
        advanceUntilIdle()

        assertEquals("stream://vod/42.mkv", herstart.state.value.streamUrl)
        assertEquals(60_000L, herstart.state.value.startPositionMs)
    }

    @Test
    fun eenLosseUrlInDeRouteWordtGenegeerd() = runTest(dispatcher) {
        // Regressie: de oude parameter is weg. Een herstelde backstack uit
        // een oudere versie mag geen URL meer naar binnen smokkelen.
        val vm = viewModel(mapOf("u" to "http://gebruiker:wachtwoord@host/1"))
        advanceUntilIdle()

        assertEquals("", vm.state.value.streamUrl)
        assertTrue(vm.state.value.sourceUnavailable)
    }

    @Test
    fun catchupZonderProviderGeeftMelding() = runTest(dispatcher) {
        val vm = viewModel(
            mapOf(
                Routes.PLAYBACK_ARG_TYPE to Routes.PLAYBACK_TYPE_CATCHUP,
                Routes.PLAYBACK_ARG_CONTENT to "9",
                Routes.PLAYBACK_ARG_START to "1700000000000",
                Routes.PLAYBACK_ARG_DURATION to "45",
            ),
            live = FakeTimeshiftRepository(timeshift = null),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.sourceUnavailable)
    }
}

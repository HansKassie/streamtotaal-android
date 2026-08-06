package nl.streamfix.ui.screens.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.streamfix.domain.model.HistoryItem
import nl.streamfix.domain.repository.PlaybackRepository
import nl.streamfix.domain.usecase.ClearHistoryUseCase
import nl.streamfix.domain.usecase.ObserveHistoryUseCase
import nl.streamfix.domain.usecase.RemoveFromHistoryUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class EmptyPlaybackRepository : PlaybackRepository {
    override suspend fun getPosition(mediaId: String): Long = 0L
    override suspend fun startWatching(item: HistoryItem) = Unit
    override suspend fun savePosition(mediaId: String, positionMs: Long) = Unit
    override fun observeHistory(): Flow<List<HistoryItem>> = flowOf(emptyList())
    override suspend fun removeFromHistory(mediaId: String) = Unit
    override suspend fun clearHistory() = Unit
}

/**
 * "Verder kijken" geeft alleen brongegevens door aan de speler. Een
 * complete stream-URL zou de inloggegevens in de navigatiestate zetten.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryTargetTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): HistoryViewModel {
        val repo = EmptyPlaybackRepository()
        return HistoryViewModel(
            observeHistory = ObserveHistoryUseCase(repo),
            removeFromHistory = RemoveFromHistoryUseCase(repo),
            clearHistory = ClearHistoryUseCase(repo),
        )
    }

    private fun item(type: String) = HistoryItem(
        mediaId = "$type:42",
        title = "Een titel",
        posterUrl = null,
        type = type,
        contentId = "42",
        extension = "mkv",
        positionMs = 12_000L,
    )

    @Test
    fun vodGeeftBrongegevensDoor() {
        val target = viewModel().targetFor(item("vod"))
        assertEquals(
            PlaybackTarget("vod", "42", "mkv", "Een titel", "vod:42"),
            target,
        )
    }

    @Test
    fun afleveringGeeftBrongegevensDoor() {
        val target = viewModel().targetFor(item("ep"))
        assertEquals("ep", target?.type)
        assertEquals("42", target?.contentId)
        assertEquals("mkv", target?.extension)
    }

    @Test
    fun onbekendTypeGeeftNull() {
        assertNull(viewModel().targetFor(item("live")))
    }

    @Test
    fun doelBevatGeenStreamUrl() {
        // Regressie: zodra hier een URL-veld bijkomt, reizen er weer
        // inloggegevens door de navigatie.
        val velden = PlaybackTarget::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()
        assertEquals(
            setOf("type", "contentId", "extension", "title", "mediaId"),
            velden,
        )
    }
}

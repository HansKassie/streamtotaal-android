package nl.streamfix

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import nl.streamfix.data.remote.catalogTimestamp
import nl.streamfix.domain.model.SeriesItem
import nl.streamfix.domain.model.VodItem
import nl.streamfix.ui.screens.live.FAVORITES_ID
import nl.streamfix.ui.screens.series.SeriesUiState
import nl.streamfix.ui.screens.vod.VodUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ZelviroCatalogOrderTest {
    @Test
    fun parsesProviderTimestampsWithoutFailingOnInvalidData() {
        assertEquals(1_700_000_000L, catalogTimestamp(JsonPrimitive("1700000000000")))
        assertEquals(1_700_000_000L, catalogTimestamp(JsonPrimitive(1_700_000_000)))
        assertEquals(null, catalogTimestamp(JsonPrimitive(0)))
        assertEquals(null, catalogTimestamp(JsonArray(emptyList())))
    }

    @Test
    fun sortsCategoriesAndKeepsFavoritesAndProviderOrder() {
        val dates = listOf(null, 100L, 300L, 300L)
        val films = dates.mapIndexed { i, date -> VodItem(i.toString(), "Film", null, null, null, addedAt = date) }
        val series = dates.mapIndexed { i, date -> SeriesItem(i.toString(), "Serie", null, null, addedAt = date) }
        val filmState = VodUiState(items = films, favorites = films)
        val seriesState = SeriesUiState(items = series)
        assertEquals(films, filmState.visibleItems)
        assertEquals(listOf(films[2], films[3], films[1], films[0]), filmState.copy(newestFirst = true).visibleItems)
        assertEquals(films, filmState.copy(newestFirst = true, selectedCategoryId = FAVORITES_ID).visibleItems)
        assertEquals(listOf(series[2], series[3], series[1], series[0]), seriesState.copy(newestFirst = true).visibleItems)
    }
}

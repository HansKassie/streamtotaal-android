package nl.streamfix.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Borgt dat de app opent in de groep waar de kijker gebleven was. Zonder
 * dit koos de lijst altijd de eerste groep, en bleef de onthouden zender
 * onvindbaar zodra die ergens anders stond.
 */
class StartCategoryTest {

    private val categories = listOf("cat1", "cat2", "cat3")
    private val favorites = "__favorites__"

    @Test
    fun laatstBekekenGroepGaatVoorOpDeEerste() {
        val start = pickStartCategory(categories, "cat3", favorites)
        assertEquals("cat3", start)
    }

    @Test
    fun zonderGeschiedenisDeEersteGroep() {
        val start = pickStartCategory(categories, null, favorites)
        assertEquals("cat1", start)
    }

    @Test
    fun verdwenenGroepValtTerugOpDeEerste() {
        // Andere provider, gewijzigd aanbod, of weggefilterd door het slot.
        val start = pickStartCategory(categories, "weg", favorites)
        assertEquals("cat1", start)
    }

    @Test
    fun favorietenIsEenGeldigeStartgroep() {
        // Favorieten staan niet in de categorielijst maar bestaan altijd.
        val start = pickStartCategory(categories, favorites, favorites)
        assertEquals(favorites, start)
    }

    @Test
    fun zonderGroepenFavorieten() {
        val start = pickStartCategory(emptyList(), "cat1", favorites)
        assertEquals(favorites, start)
    }
}

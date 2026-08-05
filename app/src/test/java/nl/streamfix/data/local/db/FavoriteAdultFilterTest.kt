package nl.streamfix.data.local.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borgt dat een favoriet uit een volwassen categorie verborgen blijft, ook
 * als de kanaalnaam zelf niets verraadt. Voorheen werd alleen de naam
 * gecontroleerd, waardoor zo'n favoriet het pincodeslot omzeilde.
 */
class FavoriteAdultFilterTest {

    private fun favorite(
        name: String,
        isAdult: Boolean? = null,
    ) = FavoriteChannelEntity(
        accountId = "acc1",
        channelId = "c1",
        name = name,
        logoUrl = null,
        categoryId = "cat1",
        isAdult = isAdult,
    )

    @Test
    fun neutraleNaamUitVolwassenCategorieBlijftVerborgen() {
        val fav = favorite(name = "Kanaal 42", isAdult = true)
        assertTrue(fav.hiddenByAdultFilter(filterActive = true))
    }

    @Test
    fun gewoonKanaalBlijftZichtbaar() {
        val fav = favorite(name = "NPO 1", isAdult = false)
        assertFalse(fav.hiddenByAdultFilter(filterActive = true))
    }

    @Test
    fun expliciteNaamZonderClassificatieValtTerugOpNaam() {
        val fav = favorite(name = "XXX Channel", isAdult = null)
        assertTrue(fav.hiddenByAdultFilter(filterActive = true))
    }

    @Test
    fun neutraleNaamZonderClassificatieBlijftZichtbaar() {
        // Oude rij (voor databaseversie 6): pas na de backfill kan de
        // categorie meewegen; tot dan geen valse verberging.
        val fav = favorite(name = "Kanaal 42", isAdult = null)
        assertFalse(fav.hiddenByAdultFilter(filterActive = true))
    }

    @Test
    fun zonderActiefFilterIsAllesZichtbaar() {
        assertFalse(favorite("XXX Channel", true).hiddenByAdultFilter(false))
        assertFalse(favorite("XXX Channel", null).hiddenByAdultFilter(false))
    }
}

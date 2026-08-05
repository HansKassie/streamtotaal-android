package nl.streamfix.data.local.db

import androidx.room.Entity
import nl.streamfix.data.local.AdultContent

/** Favoriet kanaal, per provider (accountId) zodat ze gescheiden blijven. */
@Entity(tableName = "favorite_channels", primaryKeys = ["accountId", "channelId"])
data class FavoriteChannelEntity(
    val accountId: String,
    val channelId: String,
    val name: String,
    val logoUrl: String?,
    /** Categorie waaruit de favoriet kwam; null bij oude rijen (versie < 6). */
    val categoryId: String? = null,
    /**
     * Of dit kanaal onder volwassen content valt, bepaald bij het opslaan
     * (categorie EN naam). null = nog niet geclassificeerd: dan valt het
     * filter terug op de naamcontrole tot de backfill langskomt.
     */
    val isAdult: Boolean? = null,
)

/**
 * Of deze favoriet nu verborgen moet worden. De opgeslagen classificatie is
 * leidend omdat die de categorie meewoog; is die er nog niet (rij van voor
 * databaseversie 6), dan valt het terug op de naamcontrole.
 */
fun FavoriteChannelEntity.hiddenByAdultFilter(filterActive: Boolean): Boolean =
    filterActive && (isAdult ?: AdultContent.isAdult(name))

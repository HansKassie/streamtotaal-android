package nl.streamfix.data.local.db

import androidx.room.Entity

/** Favoriet kanaal, per provider (accountId) zodat ze gescheiden blijven. */
@Entity(tableName = "favorite_channels", primaryKeys = ["accountId", "channelId"])
data class FavoriteChannelEntity(
    val accountId: String,
    val channelId: String,
    val name: String,
    val logoUrl: String?,
)

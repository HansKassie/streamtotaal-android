package nl.streamfix.data.local.db

import androidx.room.Entity

/**
 * Favoriete film of serie, per provider (accountId) en type ("vod"/"series")
 * gescheiden. Live-kanalen blijven in favorite_channels.
 */
@Entity(
    tableName = "favorite_media",
    primaryKeys = ["accountId", "mediaType", "mediaId"],
)
data class FavoriteMediaEntity(
    val accountId: String,
    val mediaType: String,
    val mediaId: String,
    val name: String,
    val posterUrl: String?,
)

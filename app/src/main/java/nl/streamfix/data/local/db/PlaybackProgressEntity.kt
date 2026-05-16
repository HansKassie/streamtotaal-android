package nl.streamfix.data.local.db

import androidx.room.Entity

/**
 * Kijkpositie + metadata per item, per provider. Voedt zowel resume als de
 * "Verder kijken"-lijst. mediaId bv. "vod:123" of "ep:456".
 */
@Entity(tableName = "playback_progress", primaryKeys = ["accountId", "mediaId"])
data class PlaybackProgressEntity(
    val accountId: String,
    val mediaId: String,
    val positionMs: Long,
    val updatedAt: Long,
    val title: String = "",
    val posterUrl: String? = null,
    val type: String = "vod", // "vod" | "ep"
    val contentId: String = "",
    val extension: String = "mp4",
)

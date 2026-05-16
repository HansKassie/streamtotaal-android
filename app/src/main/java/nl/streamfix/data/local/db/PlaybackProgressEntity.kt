package nl.streamfix.data.local.db

import androidx.room.Entity

/** Kijkpositie per item, per provider (resume). mediaId bv. "vod:123". */
@Entity(tableName = "playback_progress", primaryKeys = ["accountId", "mediaId"])
data class PlaybackProgressEntity(
    val accountId: String,
    val mediaId: String,
    val positionMs: Long,
    val updatedAt: Long,
)

package nl.streamfix.data.local.db

import androidx.room.Entity

/** Gecachte EPG per kanaal, per provider. payload = JSON-lijst programma's. */
@Entity(tableName = "epg_cache", primaryKeys = ["accountId", "streamId"])
data class EpgCacheEntity(
    val accountId: String,
    val streamId: String,
    val payload: String,
    val fetchedAt: Long,
)

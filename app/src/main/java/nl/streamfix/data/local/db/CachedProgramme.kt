package nl.streamfix.data.local.db

import kotlinx.serialization.Serializable

@Serializable
data class CachedProgramme(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
)

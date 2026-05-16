package nl.streamfix.domain.model

data class HistoryItem(
    val mediaId: String,
    val title: String,
    val posterUrl: String?,
    val type: String, // "vod" | "ep"
    val contentId: String,
    val extension: String,
    val positionMs: Long,
)

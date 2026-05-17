package nl.streamfix.domain.model

data class LiveCategory(
    val id: String,
    val name: String,
)

data class LiveChannel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    /** Dagen terugkijken (catch-up); 0 = geen archief. */
    val archiveDays: Int = 0,
)

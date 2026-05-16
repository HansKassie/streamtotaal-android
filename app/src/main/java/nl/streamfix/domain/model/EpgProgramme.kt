package nl.streamfix.domain.model

data class EpgProgramme(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
)

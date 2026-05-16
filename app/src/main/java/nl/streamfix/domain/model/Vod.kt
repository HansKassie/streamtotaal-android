package nl.streamfix.domain.model

data class VodItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val categoryId: String?,
    val containerExtension: String?,
)

data class VodDetail(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val plot: String?,
    val genre: String?,
    val year: String?,
    val director: String?,
    val cast: String?,
    val rating: String?,
    val duration: String?,
    val containerExtension: String,
)

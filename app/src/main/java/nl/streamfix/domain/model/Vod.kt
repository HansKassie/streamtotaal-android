package nl.streamfix.domain.model

data class VodItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val categoryId: String?,
    val containerExtension: String?,
    /** Rauw providerveld; beoordeel het met MediaRating, toon het nooit los. */
    val rating: String? = null,
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

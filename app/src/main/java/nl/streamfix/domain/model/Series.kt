package nl.streamfix.domain.model

data class SeriesItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val categoryId: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val containerExtension: String,
)

data class Season(
    val number: Int,
    val episodes: List<Episode>,
)

data class SeriesDetail(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val year: String?,
    val rating: String?,
    val seasons: List<Season>,
)

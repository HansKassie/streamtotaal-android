package nl.streamfix.domain.model

/** Een vooraf ingestelde provider waaruit de klant kan kiezen. */
data class Provider(
    val name: String,
    val url: String,
)

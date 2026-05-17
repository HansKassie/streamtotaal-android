package nl.streamfix.data.local

/** Herkenning van volwassen-categorieen op naam (woordniveau). */
object AdultContent {

    private val TOKENS = setOf(
        "xxx", "adult", "adults", "adulte", "adultes", "adultos", "adulti",
        "porn", "porno", "erotiek", "erotic", "erotik", "erotique",
        "volwassen", "sex", "sexe", "hentai", "nsfw", "onlyfans",
        "xrated", "18plus", "21plus",
    )

    private val PLUS18 = Regex("(^|[^0-9])(18\\s*\\+|\\+\\s*18)")

    fun isAdult(name: String): Boolean {
        val lower = name.lowercase()
        if (PLUS18.containsMatchIn(lower)) return true
        return lower.split(Regex("[^a-z0-9]+")).any { it in TOKENS }
    }
}

package nl.streamfix.domain.repository

/**
 * Eenmalige opruimacties op opgeslagen accounts, bij het opstarten.
 * Per flavor ingevuld; de Play Store-build doet niets.
 */
interface AccountMigrations {
    suspend fun run()
}

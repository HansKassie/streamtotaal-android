package nl.streamfix.ui.screens.catchup

import nl.streamfix.domain.model.EpgProgramme

/** Wat de speler nodig heeft om terug te kijken, zonder stream-URL. */
data class CatchupTarget(
    val channelId: String,
    val startMs: Long,
    val durationMin: Int,
    val title: String,
    val mediaId: String,
)

/**
 * Brongegevens om een afgelopen programma terug te kijken. Bewust GEEN
 * stream-URL: die bevat inloggegevens en zou zo in de navigatiestate
 * belanden. De speler bouwt hem zelf op uit deze gegevens.
 */
fun catchupTargetFor(
    channelId: String,
    channelName: String,
    programme: EpgProgramme,
): CatchupTarget? {
    if (channelId.isBlank()) return null
    val durationMin = ((programme.endMs - programme.startMs) / 60_000L)
        .toInt().coerceAtLeast(1)
    return CatchupTarget(
        channelId = channelId,
        startMs = programme.startMs,
        durationMin = durationMin,
        title = "$channelName - ${programme.title}",
        mediaId = "catchup:$channelId:${programme.startMs / 1000L}",
    )
}

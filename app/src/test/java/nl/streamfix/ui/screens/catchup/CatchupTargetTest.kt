package nl.streamfix.ui.screens.catchup

import nl.streamfix.domain.model.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Terugkijken geeft alleen brongegevens door aan de speler. Een complete
 * timeshift-URL zou de inloggegevens in de navigatiestate zetten.
 */
class CatchupTargetTest {

    private fun programme(startMs: Long, endMs: Long) = EpgProgramme(
        title = "Journaal",
        description = "",
        startMs = startMs,
        endMs = endMs,
    )

    @Test
    fun duurWordtInMinutenBerekend() {
        val target = catchupTargetFor(
            channelId = "9",
            channelName = "NPO 1",
            programme = programme(1_700_000_000_000L, 1_700_002_700_000L),
        )
        assertEquals(45, target?.durationMin)
        assertEquals(1_700_000_000_000L, target?.startMs)
        assertEquals("NPO 1 - Journaal", target?.title)
        assertEquals("catchup:9:1700000000", target?.mediaId)
    }

    @Test
    fun heelKortProgrammaKrijgtMinstensEenMinuut() {
        // 0 minuten zou een lege timeshift-aanvraag opleveren.
        val target = catchupTargetFor(
            channelId = "9",
            channelName = "NPO 1",
            programme = programme(1_700_000_000_000L, 1_700_000_010_000L),
        )
        assertEquals(1, target?.durationMin)
    }

    @Test
    fun zonderKanaalIdGeenDoel() {
        assertNull(
            catchupTargetFor(
                channelId = "",
                channelName = "NPO 1",
                programme = programme(1L, 2L),
            ),
        )
    }

    @Test
    fun doelBevatGeenStreamUrl() {
        // Regressie: zodra hier een URL-veld bijkomt, reizen er weer
        // inloggegevens door de navigatie.
        val velden = CatchupTarget::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()
        assertEquals(
            setOf("channelId", "startMs", "durationMin", "title", "mediaId"),
            velden,
        )
    }
}

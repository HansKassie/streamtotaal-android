package nl.streamfix.domain.repository

import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.util.AppResult

interface EpgRepository {
    /** EPG voor een kanaal; cache-first met TTL, valt offline terug op cache. */
    suspend fun getEpg(streamId: String): AppResult<List<EpgProgramme>>

    /** Volledige EPG-tabel (incl. afgelopen) voor catch-up/terugkijken. */
    suspend fun getEpgTable(streamId: String): AppResult<List<EpgProgramme>>
}

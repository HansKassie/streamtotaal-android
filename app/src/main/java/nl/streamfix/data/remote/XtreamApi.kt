package nl.streamfix.data.remote

import nl.streamfix.data.remote.dto.XtreamAuthResponseDto
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * De server-URL is gebruikersinvoer, dus geen vaste baseUrl: de volledige
 * URL wordt per request via @Url meegegeven.
 */
interface XtreamApi {
    @GET
    suspend fun authenticate(@Url url: String): XtreamAuthResponseDto
}

package nl.streamfix.data.remote

import nl.streamfix.data.remote.dto.XtreamAuthResponseDto
import nl.streamfix.data.remote.dto.XtreamCategoryDto
import nl.streamfix.data.remote.dto.XtreamLiveStreamDto
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * De server-URL is gebruikersinvoer, dus geen vaste baseUrl: de volledige
 * URL wordt per request via @Url meegegeven.
 */
interface XtreamApi {
    @GET
    suspend fun authenticate(@Url url: String): XtreamAuthResponseDto

    @GET
    suspend fun getLiveCategories(@Url url: String): List<XtreamCategoryDto>

    @GET
    suspend fun getLiveStreams(@Url url: String): List<XtreamLiveStreamDto>
}


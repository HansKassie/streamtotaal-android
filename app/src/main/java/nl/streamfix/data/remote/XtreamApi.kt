package nl.streamfix.data.remote

import nl.streamfix.data.remote.dto.ProviderDto
import nl.streamfix.data.remote.dto.UpdateManifestDto
import nl.streamfix.data.remote.dto.XtreamAuthResponseDto
import nl.streamfix.data.remote.dto.XtreamCategoryDto
import nl.streamfix.data.remote.dto.XtreamEpgResponseDto
import nl.streamfix.data.remote.dto.XtreamLiveStreamDto
import nl.streamfix.data.remote.dto.XtreamSeriesInfoResponseDto
import nl.streamfix.data.remote.dto.XtreamSeriesStreamDto
import nl.streamfix.data.remote.dto.XtreamVodInfoResponseDto
import nl.streamfix.data.remote.dto.XtreamVodStreamDto
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

    @GET
    suspend fun getVodCategories(@Url url: String): List<XtreamCategoryDto>

    @GET
    suspend fun getVodStreams(@Url url: String): List<XtreamVodStreamDto>

    @GET
    suspend fun getVodInfo(@Url url: String): XtreamVodInfoResponseDto

    @GET
    suspend fun getSeriesCategories(@Url url: String): List<XtreamCategoryDto>

    @GET
    suspend fun getSeriesStreams(@Url url: String): List<XtreamSeriesStreamDto>

    @GET
    suspend fun getSeriesInfo(@Url url: String): XtreamSeriesInfoResponseDto

    @GET
    suspend fun getShortEpg(@Url url: String): XtreamEpgResponseDto

    @GET
    suspend fun getProviderCatalog(@Url url: String): List<ProviderDto>

    @GET
    suspend fun getUpdateManifest(@Url url: String): UpdateManifestDto
}


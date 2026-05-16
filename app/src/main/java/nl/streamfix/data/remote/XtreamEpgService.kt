package nl.streamfix.data.remote

import android.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.util.AppResult

class XtreamEpgService @Inject constructor(
    private val api: XtreamApi,
) {
    suspend fun shortEpg(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        limit: Int = 12,
    ): AppResult<List<EpgProgramme>> = withContext(Dispatchers.IO) {
        try {
            val url = XtreamUrls.playerApi(
                serverUrl, username, password,
                action = "get_short_epg",
                params = mapOf("stream_id" to streamId, "limit" to limit.toString()),
            )
            val programmes = api.getShortEpg(url).listings.orEmpty()
                .mapNotNull { dto ->
                    val start = dto.startSeconds ?: return@mapNotNull null
                    val stop = dto.stopSeconds ?: return@mapNotNull null
                    EpgProgramme(
                        title = decode(dto.title),
                        description = decode(dto.description),
                        startMs = start * 1000L,
                        endMs = stop * 1000L,
                    )
                }
                .sortedBy { it.startMs }
            AppResult.Success(programmes)
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }

    /** Xtream codeert titel/omschrijving als base64; val terug op ruw bij twijfel. */
    private fun decode(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        return runCatching {
            String(Base64.decode(raw, Base64.DEFAULT)).trim()
        }.getOrDefault(raw)
    }
}

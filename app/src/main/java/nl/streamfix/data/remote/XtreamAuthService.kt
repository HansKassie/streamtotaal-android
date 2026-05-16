package nl.streamfix.data.remote

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.data.remote.dto.XtreamUserInfoDto
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.util.AppResult

data class XtreamAuthData(
    val normalizedServerUrl: String,
    val userInfo: XtreamUserInfoDto,
)

class XtreamAuthService @Inject constructor(
    private val api: XtreamApi,
) {
    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<XtreamAuthData> = withContext(Dispatchers.IO) {
        val base = XtreamUrls.normalizeServerUrl(serverUrl)
        val url = XtreamUrls.playerApi(serverUrl, username, password)

        try {
            val response = api.authenticate(url)
            val info = response.userInfo
                ?: return@withContext AppResult.Failure(AppError.NotAnXtreamServer)

            when {
                info.authValue != 1 -> AppResult.Failure(AppError.InvalidCredentials)
                info.status.equalsIgnoreCase("Expired") ->
                    AppResult.Failure(AppError.SubscriptionExpired)
                info.status.equalsIgnoreCase("Banned") ||
                    info.status.equalsIgnoreCase("Disabled") ->
                    AppResult.Failure(AppError.InvalidCredentials)
                else -> AppResult.Success(XtreamAuthData(base, info))
            }
        } catch (e: Exception) {
            AppResult.Failure(XtreamErrorMapper.map(e))
        }
    }

    private fun String?.equalsIgnoreCase(other: String): Boolean =
        this != null && this.equals(other, ignoreCase = true)
}

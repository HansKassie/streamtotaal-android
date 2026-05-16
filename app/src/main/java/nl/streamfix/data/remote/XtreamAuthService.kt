package nl.streamfix.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import nl.streamfix.data.remote.dto.XtreamUserInfoDto
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.util.AppResult
import retrofit2.HttpException

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
        val base = normalizeServerUrl(serverUrl)
        val url = buildString {
            append(base)
            append("/player_api.php?username=")
            append(encode(username))
            append("&password=")
            append(encode(password))
        }

        try {
            val response = api.authenticate(url)
            val info = response.userInfo
                ?: return@withContext AppResult.Failure(AppError.NotAnXtreamServer)

            when {
                info.auth != 1 -> AppResult.Failure(AppError.InvalidCredentials)
                info.status.equalsIgnoreCase("Expired") ->
                    AppResult.Failure(AppError.SubscriptionExpired)
                info.status.equalsIgnoreCase("Banned") ||
                    info.status.equalsIgnoreCase("Disabled") ->
                    AppResult.Failure(AppError.InvalidCredentials)
                else -> AppResult.Success(XtreamAuthData(base, info))
            }
        } catch (e: UnknownHostException) {
            AppResult.Failure(AppError.ServerUnreachable)
        } catch (e: ConnectException) {
            AppResult.Failure(AppError.ServerUnreachable)
        } catch (e: SerializationException) {
            AppResult.Failure(AppError.NotAnXtreamServer)
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> AppResult.Failure(AppError.InvalidCredentials)
                404 -> AppResult.Failure(AppError.NotAnXtreamServer)
                // Sommige panels (achter een WAF) geven 512 met lege body
                // terug bij een geweigerde login i.p.v. auth:0 in een 200.
                512 -> AppResult.Failure(AppError.InvalidCredentials)
                // Cloudflare origin-fouten: provider-server ligt eruit
                in 520..526 -> AppResult.Failure(AppError.ProviderUnavailable)
                in 500..599 -> AppResult.Failure(AppError.ServerUnreachable)
                else -> AppResult.Failure(AppError.NotAnXtreamServer)
            }
        } catch (e: IOException) {
            AppResult.Failure(AppError.NetworkUnavailable)
        } catch (e: IllegalArgumentException) {
            // OkHttp gooit dit bij een onbruikbare URL (spaties, lege host, alleen http://)
            AppResult.Failure(AppError.InvalidUrl)
        } catch (e: Exception) {
            // Retrofit/converter wikkelt parse-fouten soms in een runtime-exception
            if (e.cause is SerializationException) {
                AppResult.Failure(AppError.NotAnXtreamServer)
            } else {
                AppResult.Failure(AppError.Unknown)
            }
        }
    }

    private fun normalizeServerUrl(input: String): String {
        // We gebruiken altijd http://: een eventueel ingevoerd schema wordt
        // genegeerd en hard op http:// gezet. Een door de klant ingevoerde
        // poort blijft wel behouden (sommige panels draaien op :8080 e.d.).
        val withoutScheme = input.trim().replace(Regex("(?i)^[a-z][a-z0-9+.-]*://"), "")
        return "http://" + withoutScheme.trimEnd('/')
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun String?.equalsIgnoreCase(other: String): Boolean =
        this != null && this.equals(other, ignoreCase = true)
}

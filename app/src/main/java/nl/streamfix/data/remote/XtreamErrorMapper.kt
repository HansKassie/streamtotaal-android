package nl.streamfix.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import nl.streamfix.domain.model.AppError
import retrofit2.HttpException

/** Eén plek voor het mappen van netwerk/parse-fouten naar AppError. */
object XtreamErrorMapper {
    fun map(e: Throwable): AppError = when (e) {
        is UnknownHostException -> AppError.ServerUnreachable
        is ConnectException -> AppError.ServerUnreachable
        is SerializationException -> AppError.NotAnXtreamServer
        is HttpException -> when (e.code()) {
            401, 403 -> AppError.InvalidCredentials
            404 -> AppError.NotAnXtreamServer
            // Sommige panels achter een WAF geven 512 bij geweigerde login.
            512 -> AppError.InvalidCredentials
            // Cloudflare origin-fouten: provider-server ligt eruit.
            in 520..526 -> AppError.ProviderUnavailable
            in 500..599 -> AppError.ServerUnreachable
            else -> AppError.NotAnXtreamServer
        }
        is IOException -> AppError.NetworkUnavailable
        is IllegalArgumentException -> AppError.InvalidUrl
        else ->
            if (e.cause is SerializationException) AppError.NotAnXtreamServer
            else AppError.Unknown
    }
}

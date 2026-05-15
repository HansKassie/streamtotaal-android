package nl.streamfix.data.remote

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.M3uSource
import nl.streamfix.domain.util.AppResult
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lichte geldigheidscheck voor Fase 1: alleen of de bron met #EXTM3U begint.
 * De volledige parser (kanalen, tvg-id, group-title) komt in Fase 2.
 */
class M3uValidator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun validate(source: M3uSource): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            when (source) {
                is M3uSource.Url -> validateUrl(source.url.trim())
                is M3uSource.LocalFile -> validateFile(source.uri)
            }
        }

    private fun validateUrl(url: String): AppResult<Unit> {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            return AppResult.Failure(AppError.InvalidM3u)
        }
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return AppResult.Failure(AppError.InvalidM3u)
                }
                val head = response.body?.source()?.let { src ->
                    src.request(PEEK_BYTES)
                    src.buffer.snapshot().utf8()
                }.orEmpty()
                if (head.containsHeader()) AppResult.Success(Unit)
                else AppResult.Failure(AppError.InvalidM3u)
            }
        } catch (e: UnknownHostException) {
            AppResult.Failure(AppError.ServerUnreachable)
        } catch (e: ConnectException) {
            AppResult.Failure(AppError.ServerUnreachable)
        } catch (e: IOException) {
            AppResult.Failure(AppError.NetworkUnavailable)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    private fun validateFile(uriString: String): AppResult<Unit> {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(PEEK_BYTES.toInt())
                val read = input.read(head)
                val text = if (read > 0) String(head, 0, read) else ""
                if (text.containsHeader()) AppResult.Success(Unit)
                else AppResult.Failure(AppError.InvalidM3u)
            } ?: AppResult.Failure(AppError.InvalidM3u)
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.InvalidM3u)
        } catch (e: IOException) {
            AppResult.Failure(AppError.InvalidM3u)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    private fun String.containsHeader(): Boolean =
        trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("#EXTM3U", true)

    private companion object {
        const val PEEK_BYTES = 1024L
    }
}

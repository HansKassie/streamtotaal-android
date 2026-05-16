package nl.streamfix.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import nl.streamfix.BuildConfig
import nl.streamfix.data.remote.XtreamApi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(userAgentInterceptor())

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(passwordRedactingLogger())
        }
        return builder.build()
    }

    /**
     * Veel Xtream-panels (vaak achter Cloudflare) blokkeren de standaard
     * okhttp User-Agent met o.a. HTTP 520. Een normale browser-UA wordt
     * breed geaccepteerd. (Briefing: providerafwijkingen.)
     */
    private fun userAgentInterceptor() = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            .header("Accept", "*/*")
            .build()
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Verplichte placeholder; echte URL gaat per request via @Url.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideXtreamApi(retrofit: Retrofit): XtreamApi =
        retrofit.create(XtreamApi::class.java)

    /**
     * Debug-only logger. Logt methode, host, pad en HTTP-status plus een
     * korte body-preview, met username/password geredigeerd, zodat
     * providerafwijkingen te diagnosticeren zijn zonder credentials te lekken
     * (briefing 6.1: geen PII in logs).
     */
    private fun passwordRedactingLogger() = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        val isDefaultPort =
            (url.scheme == "http" && url.port == 80) ||
                (url.scheme == "https" && url.port == 443)
        val portPart = if (isDefaultPort) "" else ":${url.port}"
        val safeUrl = "${url.scheme}://${url.host}$portPart${url.encodedPath}"
        android.util.Log.d("StreamFixHttp", "${request.method} -> $safeUrl")

        val response = chain.proceed(request)

        val preview = runCatching {
            val body = response.peekBody(512)
            redact(body.string())
        }.getOrDefault("<body niet leesbaar>")
        android.util.Log.d(
            "StreamFixHttp",
            "<- HTTP ${response.code} ${response.message}; body: $preview",
        )
        response
    }

    private fun redact(text: String): String =
        text
            // query-vorm: username=...&password=...
            .replace(Regex("(?i)(username|password)=([^&\\s\"]+)"), "$1=***")
            // JSON-vorm uit de respons: "password":"..."
            .replace(
                Regex("(?i)\"(username|password)\"\\s*:\\s*\"[^\"]*\""),
                "\"$1\":\"***\"",
            )
            .take(300)
            .replace("\n", " ")
}

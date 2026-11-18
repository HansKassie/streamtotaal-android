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
     * Debugverzoeken met methode, host, HTTP-status en doorlooptijd.
     * Paden, queryparameters en response-inhoud kunnen inloggegevens bevatten.
     */
    internal fun passwordRedactingLogger(
        log: (String) -> Unit = { android.util.Log.d("StreamFixHttp", it) },
    ) = Interceptor { chain ->
        val request = chain.request()
        val started = System.nanoTime()
        log("${request.method} -> ${request.url.host}")

        val response = chain.proceed(request)

        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        log("<- HTTP ${response.code}; $elapsedMs ms")
        response
    }

}

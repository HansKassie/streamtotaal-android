package nl.streamfix.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.streamfix.data.local.PortalDevice
import nl.streamfix.data.local.PortalIdentityStorageException
import nl.streamfix.data.local.PortalIdentityStore
import nl.streamfix.data.local.createPortalIdentityStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal const val PORTAL_URL = "https://zelviro-player.com"
private val playlistId = Regex("[A-Za-z0-9_-]{1,32}")

@Serializable
class PortalPlaylist(
    val id: String,
    @SerialName("naam") val name: String,
    @SerialName("server") val server: String,
    @SerialName("gebruiker") val username: String,
    @SerialName("wachtwoord") val password: String,
) {
    internal fun valid(): Boolean {
        val url = server.toHttpUrlOrNull() ?: return false
        return playlistId.matches(id) && name.trim().length in 1..40 &&
            !server.contains('@') && url.encodedPath == "/" && url.query == null && url.fragment == null &&
            listOf(username, password).all { value ->
                value.isNotBlank() && value.length <= 200 && value.none(Char::isISOControl)
            }
    }
}

@Serializable
private class PortalPending(val code: String, val afspeellijsten: Int)

@Serializable
private class PortalActive(val afspeellijsten: List<PortalPlaylist>)

@Serializable
private class PortalStatus(val versie: Int, val status: String)

sealed interface PortalResult {
    class Pending(
        val code: String,
        val key: String,
        val playlistCount: Int,
        val activated: Boolean = false,
    ) : PortalResult
    class Active(val playlists: List<PortalPlaylist>, val code: String, val key: String) : PortalResult
    data object Blocked : PortalResult
    data object Unavailable : PortalResult
    data object RateLimited : PortalResult
    data object StorageUnavailable : PortalResult
}

@Singleton
class PortalClient internal constructor(
    private val openIdentity: () -> PortalIdentityStore,
    private val http: OkHttpClient,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        { createPortalIdentityStore(context) },
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .build(),
    )

    private val store by lazy { openIdentity() }
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun status(register: Boolean): PortalResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                statusLocked(register)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: PortalIdentityStorageException) {
                PortalResult.StorageUnavailable
            } catch (_: Exception) {
                PortalResult.Unavailable
            }
        }
    }

    private fun statusLocked(register: Boolean): PortalResult {
        var identity = store.load()
        val previous = identity.previous
        if (previous != null) {
            val (code, body) = post("stand", "{}", previous.geheim)
            if (code == 200) {
                val result = parseStatus(body, previous)
                if (result != PortalResult.Unavailable) {
                    store.save(identity.copy(device = previous, previous = null, registrationPending = false))
                    return result
                }
            }
        }
        // Bij een onderbroken registratie weten we niet of de server al een
        // apparaat maakte. Niet opnieuw aanmelden en spookapparaten veroorzaken.
        if (identity.registrationPending) return PortalResult.StorageUnavailable
        var device = identity.device
        if (device != null) {
            val (code, body) = post("stand", "{}", device.geheim)
            if (code == 200) return parseStatus(body, device)
            if (code != 401 || !register) return PortalResult.Unavailable
            if (previous == null) identity = identity.copy(previous = device)
        } else if (!register) return PortalResult.Unavailable

        // Eerst duurzaam schrijven én heropenen, dan pas naar het portaal.
        identity = identity.copy(registrationPending = true)
        store.save(identity)
        val (code, body) = try {
            post("aanmelden", """{"platform":"android-tv"}""")
        } catch (error: Exception) {
            // DNS/verbinding mislukte: er is nog geen HTTP-verzoek verstuurd.
            // Een verloren antwoord of timeout blijft juist onzeker.
            if (error is UnknownHostException || error is ConnectException || error is NoRouteToHostException) {
                store.save(identity.copy(registrationPending = false))
            }
            throw error
        }
        if (code in 400..499) {
            store.save(identity.copy(registrationPending = false))
            return if (code == 429) PortalResult.RateLimited else PortalResult.Unavailable
        }
        if (code != 201) return PortalResult.Unavailable
        device = json.decodeFromString<PortalDevice>(body).takeIf { it.valid() }
            ?: return PortalResult.Unavailable
        store.save(identity.copy(device = device, registrationPending = false))
        return PortalResult.Pending(device.code, device.sleutel, 0)
    }

    private fun parseStatus(body: String, device: PortalDevice): PortalResult {
        val status = json.decodeFromString<PortalStatus>(body)
        if (status.versie != 2) return PortalResult.Unavailable
        return when (status.status) {
            "niet-geactiveerd" -> {
                val pending = json.decodeFromString<PortalPending>(body)
                if (pending.code != device.code || pending.afspeellijsten < 0) PortalResult.Unavailable
                else PortalResult.Pending(device.code, device.sleutel, pending.afspeellijsten)
            }
            "geactiveerd" -> {
                val playlists = json.decodeFromString<PortalActive>(body).afspeellijsten
                when {
                    playlists.isEmpty() -> PortalResult.Pending(device.code, device.sleutel, 0, activated = true)
                    playlists.any { !it.valid() } || playlists.distinctBy { it.id }.size != playlists.size ->
                        PortalResult.Unavailable
                    else -> PortalResult.Active(playlists, device.code, device.sleutel)
                }
            }
            "geblokkeerd" -> PortalResult.Blocked
            else -> PortalResult.Unavailable
        }
    }

    private fun post(path: String, body: String, secret: String? = null): Pair<Int, String> {
        val request = Request.Builder()
            .url("$PORTAL_URL/api/apparaat/$path")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { if (secret != null) header("Authorization", "Bearer $secret") }
            .build()
        return http.newCall(request).execute().use {
            val response = it.body?.source()
            // Lees maximaal 256 KiB, ook als de server geen Content-Length opgeeft.
            val tooLarge = response?.request(262_145) == true
            if (tooLarge) return it.code to ""
            it.code to response?.readUtf8().orEmpty()
        }
    }
}

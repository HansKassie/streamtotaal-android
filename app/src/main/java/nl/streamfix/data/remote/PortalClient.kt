package nl.streamfix.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.streamfix.data.local.createSecurePrefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal const val PORTAL_URL = "https://zelviro-player.com"
private val deviceCode = Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}")
private val deviceKey = Regex("[0-9]{6}")
private val deviceSecret = Regex("[A-Za-z0-9_-]{43}")
private val playlistId = Regex("[A-Za-z0-9_-]{1,32}")

@Serializable
private class PortalDevice(val code: String, val sleutel: String, val geheim: String) {
    fun valid() = deviceCode.matches(code) && deviceKey.matches(sleutel) && deviceSecret.matches(geheim)
}

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
}

@Singleton
class PortalClient @Inject constructor(@ApplicationContext context: Context) {
    private val prefs by lazy { createSecurePrefs(context, "streamfix_portal_device") }
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun status(register: Boolean): PortalResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                statusLocked(register)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                PortalResult.Unavailable
            }
        }
    }

    private fun statusLocked(register: Boolean): PortalResult {
        val previous = readDevice("previous")
        if (previous != null) {
            val (code, body) = post("stand", "{}", previous.geheim)
            if (code == 200) {
                val result = parseStatus(body, previous)
                if (result != PortalResult.Unavailable) {
                    prefs.edit().putString("device", json.encodeToString(previous)).remove("previous").apply()
                    return result
                }
            }
        }
        var device = readDevice("device")
        if (device != null) {
            val (code, body) = post("stand", "{}", device.geheim)
            if (code == 200) return parseStatus(body, device)
            if (code != 401 || !register) return PortalResult.Unavailable
            if (previous == null) prefs.edit().putString("previous", json.encodeToString(device)).apply()
        } else if (!register) return PortalResult.Unavailable

        val (code, body) = post("aanmelden", """{"platform":"android-tv"}""")
        if (code == 429) return PortalResult.RateLimited
        if (code != 201) return PortalResult.Unavailable
        device = json.decodeFromString<PortalDevice>(body).takeIf { it.valid() }
            ?: return PortalResult.Unavailable
        prefs.edit().putString("device", json.encodeToString(device)).apply()
        return PortalResult.Pending(device.code, device.sleutel, 0)
    }

    private fun readDevice(key: String): PortalDevice? = prefs.getString(key, null)?.let { raw ->
        runCatching { json.decodeFromString<PortalDevice>(raw) }.getOrNull()?.takeIf { it.valid() }
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

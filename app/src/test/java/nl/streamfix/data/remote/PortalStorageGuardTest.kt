package nl.streamfix.data.remote

import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.runBlocking
import nl.streamfix.data.local.InMemorySharedPreferences
import nl.streamfix.data.local.openSecurePrefsWithRetry
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Normale SharedPreferences als testdouble, onderscheiden van de echte geheugenfallback. */
private class RegularPreferencesFake : SharedPreferences by InMemorySharedPreferences()

class PortalStorageGuardTest {
    private val device = """{"code":"12:34:56:78:9a:bc","sleutel":"123456","geheim":"${"a".repeat(43)}"}"""
    private val pending = """{"versie":2,"status":"niet-geactiveerd","code":"12:34:56:78:9a:bc","afspeellijsten":0}"""

    private fun http(requests: MutableList<String>) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            requests += path
            val registering = path.endsWith("/aanmelden")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (registering) 201 else 200)
                .message("Test response")
                .body((if (registering) device else pending).toResponseBody())
                .build()
        }
        .build()

    @Test
    fun memoryFallbackNeverRegistersAcrossRetriesAndRestarts() = runBlocking {
        val requests = mutableListOf<String>()
        val http = http(requests)
        repeat(3) {
            val fallback = openSecurePrefsWithRetry { throw IOException("Simulated opening failure") }
            val client = PortalClient({ fallback }, http)
            repeat(4) {
                assertSame(PortalResult.StorageUnavailable, client.status(register = true))
            }
        }
        assertTrue("Memory fallback must never contact the portal", requests.isEmpty())
    }

    @Test
    fun memoryFallbackWithAnIdentityStillNeverContactsPortal() = runBlocking {
        val requests = mutableListOf<String>()
        val fallback = InMemorySharedPreferences()
        fallback.edit().putString("device", device).putString("previous", device).apply()
        val client = PortalClient({ fallback }, http(requests))
        assertSame(PortalResult.StorageUnavailable, client.status(register = false))
        assertSame(PortalResult.StorageUnavailable, client.status(register = true))
        assertTrue(requests.isEmpty())
        assertEquals(device, fallback.getString("device", null))
    }

    @Test
    fun normalPreferencesRegisterOnceAndReuseIdentity() = runBlocking {
        val requests = mutableListOf<String>()
        val preferences = RegularPreferencesFake()
        val http = http(requests)
        val first = PortalClient({ preferences }, http).status(register = true)
        assertTrue(first is PortalResult.Pending)
        val stored = preferences.getString("device", null)
        assertNotNull(stored)

        val restarted = PortalClient({ preferences }, http)
        assertTrue(restarted.status(register = true) is PortalResult.Pending)
        assertEquals(listOf("/api/apparaat/aanmelden", "/api/apparaat/stand"), requests)
        assertEquals(stored, preferences.getString("device", null))
    }

    @Test
    fun existingNormalIdentityOnlyRequestsItsStatus() = runBlocking {
        val requests = mutableListOf<String>()
        val preferences = RegularPreferencesFake()
        preferences.edit().putString("device", device).apply()
        val client = PortalClient({ preferences }, http(requests))
        val result = client.status(register = true)
        assertTrue(result is PortalResult.Pending)
        assertEquals("12:34:56:78:9a:bc", (result as PortalResult.Pending).code)
        assertEquals(listOf("/api/apparaat/stand"), requests)
        assertEquals(device, preferences.getString("device", null))
    }

    @Test
    fun startupWithoutRegistrationPermissionDoesNotRegister() = runBlocking {
        val requests = mutableListOf<String>()
        val client = PortalClient({ RegularPreferencesFake() }, http(requests))
        assertSame(PortalResult.Unavailable, client.status(register = false))
        assertTrue(requests.isEmpty())
    }
}

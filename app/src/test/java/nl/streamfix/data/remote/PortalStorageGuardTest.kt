package nl.streamfix.data.remote

import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import nl.streamfix.data.local.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PortalStorageGuardTest {
    private val pending = """{"versie":2,"status":"niet-geactiveerd","code":"12:34:56:78:9a:bc","afspeellijsten":0}"""

    private fun http(requests: MutableList<String>, onRegister: (() -> Unit)? = null) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            requests += path
            val registering = path.endsWith("/aanmelden")
            if (registering) onRegister?.invoke()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (registering) 201 else 200).message("Test response")
                .body((if (registering) testPortalDeviceJson else pending).toResponseBody()).build()
        }.build()

    @Test fun memoryFallbackNeverRegistersAcrossRetriesAndRestarts() = runBlocking {
        val requests = mutableListOf<String>()
        repeat(3) {
            val fallback = openSecurePrefsWithRetry { throw IOException("Simulated opening failure") }
            val client = PortalClient({ testIdentityStore(IdentityFileFake(), fallback) }, http(requests))
            repeat(4) { assertSame(PortalResult.StorageUnavailable, client.status(register = true)) }
        }
        assertTrue(requests.isEmpty())
    }

    @Test fun memoryFallbackWithAnIdentityStillNeverContactsPortal() = runBlocking {
        val requests = mutableListOf<String>()
        val fallback = InMemorySharedPreferences()
        fallback.edit().putString("device", testPortalDeviceJson).apply()
        val client = PortalClient({ testIdentityStore(IdentityFileFake(), fallback) }, http(requests))
        assertSame(PortalResult.StorageUnavailable, client.status(register = false))
        assertSame(PortalResult.StorageUnavailable, client.status(register = true))
        assertTrue(requests.isEmpty())
    }

    @Test fun freshInstallRegistersOnceAndReusesIdentityWithoutKeystore() = runBlocking {
        val requests = mutableListOf<String>()
        val file = IdentityFileFake()
        fun store() = testIdentityStore(file, legacyExists = false, open = { error("No Keystore needed") })
        assertTrue(PortalClient({ store() }, http(requests)).status(true) is PortalResult.Pending)
        assertTrue(PortalClient({ store() }, http(requests)).status(true) is PortalResult.Pending)
        assertEquals(listOf("/api/apparaat/aanmelden", "/api/apparaat/stand"), requests)
    }

    @Test fun migratedIdentityOnlyRequestsItsStatus() = runBlocking {
        val requests = mutableListOf<String>()
        val preferences = LegacyPreferencesFake()
        preferences.edit().putString("device", testPortalDeviceJson).commit()
        val file = IdentityFileFake()
        val client = PortalClient({ testIdentityStore(file, preferences) }, http(requests))
        val result = client.status(true)
        assertTrue(result is PortalResult.Pending)
        assertEquals("12:34:56:78:9a:bc", (result as PortalResult.Pending).code)
        assertEquals(listOf("/api/apparaat/stand"), requests)
        assertFalse(preferences.contains("device"))
    }

    @Test fun startupWithoutRegistrationPermissionDoesNotRegister() = runBlocking {
        val requests = mutableListOf<String>()
        val client = PortalClient({ testIdentityStore(IdentityFileFake(), legacyExists = false) }, http(requests))
        assertSame(PortalResult.Unavailable, client.status(false))
        assertTrue(requests.isEmpty())
    }

    @Test fun unwritableStorageNeverContactsPortal() = runBlocking {
        val requests = mutableListOf<String>()
        val file = IdentityFileFake().apply { failOnWrite = 1 }
        val client = PortalClient({ testIdentityStore(file, legacyExists = false) }, http(requests))
        assertSame(PortalResult.StorageUnavailable, client.status(true))
        assertTrue(requests.isEmpty())
    }

    @Test fun registrationWaitsForDurableMarker() = runBlocking {
        val requests = mutableListOf<String>()
        val file = IdentityFileFake().apply { failOnWrite = 2 }
        val client = PortalClient({ testIdentityStore(file, legacyExists = false) }, http(requests))
        assertSame(PortalResult.StorageUnavailable, client.status(true))
        assertTrue(requests.isEmpty())
    }

    @Test fun saveFailureAfterRegistrationDoesNotRegisterAgainAfterRestart() = runBlocking {
        val requests = mutableListOf<String>()
        val file = IdentityFileFake().apply { failOnWrite = 3 }
        fun client() = PortalClient({ testIdentityStore(file, legacyExists = false) }, http(requests))
        assertSame(PortalResult.StorageUnavailable, client().status(true))
        repeat(3) { assertSame(PortalResult.StorageUnavailable, client().status(true)) }
        assertEquals(listOf("/api/apparaat/aanmelden"), requests)
    }

    @Test fun unknownNetworkOutcomeDoesNotRegisterAgain() = runBlocking {
        val requests = mutableListOf<String>()
        val file = IdentityFileFake()
        val http = http(requests) { throw IOException("Simulated lost response") }
        fun client() = PortalClient({ testIdentityStore(file, legacyExists = false) }, http)
        assertSame(PortalResult.Unavailable, client().status(true))
        assertSame(PortalResult.StorageUnavailable, client().status(true))
        assertEquals(1, requests.size)
    }

    @Test fun offlineBeforeConnectingDoesNotPermanentlyBlockRegistration() = runBlocking {
        val requests = mutableListOf<String>()
        val file = IdentityFileFake()
        val offline = http(requests) { throw UnknownHostException("Simulated offline startup") }
        val first = PortalClient({ testIdentityStore(file, legacyExists = false) }, offline)
        assertSame(PortalResult.Unavailable, first.status(true))
        val online = PortalClient({ testIdentityStore(file, legacyExists = false) }, http(requests))
        assertTrue(online.status(true) is PortalResult.Pending)
        assertEquals(2, requests.size)
    }
}

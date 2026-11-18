package nl.streamfix.data.local

import android.content.SharedPreferences
import java.io.IOException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

internal class LegacyPreferencesFake : SharedPreferences by InMemorySharedPreferences()

internal class IdentityFileFake : PortalIdentityFile {
    var bytes: ByteArray? = null
    var writes = 0
    var failOnWrite = 0
    var corruptWrite = false
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) {
        writes++
        if (writes == failOnWrite) throw IOException("Simulated write failure")
        this.bytes = if (corruptWrite) byteArrayOf(0) else bytes.copyOf()
    }
}

internal fun testIdentityStore(
    file: IdentityFileFake,
    prefs: SharedPreferences = LegacyPreferencesFake(),
    legacyExists: Boolean = true,
    open: () -> SharedPreferences = { prefs },
) = PortalIdentityStore(file, PreferencesPortalIdentity({ legacyExists }, open))

internal val testPortalDeviceJson = """{"code":"12:34:56:78:9a:bc","sleutel":"123456","geheim":"${"a".repeat(43)}"}"""

class PortalIdentityStoreTest {
    private fun prefs() = LegacyPreferencesFake().apply {
        edit().putString("device", testPortalDeviceJson).putString("other", "keep").commit()
    }

    @Test fun migratesBothIdentitiesAndOnlyClearsLegacyAfterVerifiedWrite() {
        val old = prefs()
        old.edit().putString("previous", testPortalDeviceJson).commit()
        val file = IdentityFileFake()
        val migrated = testIdentityStore(file, old).load()
        assertEquals("12:34:56:78:9a:bc", migrated.device?.code)
        assertEquals("123456", migrated.previous?.sleutel)
        assertFalse(old.contains("device"))
        assertFalse(old.contains("previous"))
        assertEquals("keep", old.getString("other", null))
        val reopened = testIdentityStore(file, open = { error("Keystore must not be opened") }).load()
        assertEquals(migrated.device?.geheim, reopened.device?.geheim)
        assertEquals(migrated.previous?.geheim, reopened.previous?.geheim)
    }

    @Test fun migrationWriteFailurePreservesOriginal() {
        val old = prefs()
        val file = IdentityFileFake().apply { failOnWrite = 1 }
        assertThrows(PortalIdentityStorageException::class.java) { testIdentityStore(file, old).load() }
        assertEquals(testPortalDeviceJson, old.getString("device", null))
        assertNull(file.bytes)
    }

    @Test fun failedReadbackPreservesOriginal() {
        val old = prefs()
        val file = IdentityFileFake().apply { corruptWrite = true }
        assertThrows(PortalIdentityStorageException::class.java) { testIdentityStore(file, old).load() }
        assertEquals(testPortalDeviceJson, old.getString("device", null))
    }

    @Test fun corruptLegacyIdentityBlocksMigrationInsteadOfCreatingNewIdentity() {
        val old = prefs()
        old.edit().putString("device", "invalid json").commit()
        val file = IdentityFileFake()
        assertThrows(PortalIdentityStorageException::class.java) { testIdentityStore(file, old).load() }
        assertEquals(0, file.writes)
        assertTrue(old.contains("device"))
    }

    @Test fun invalidLegacyPreviousIdentityIsNotSilentlyLost() {
        val old = prefs()
        old.edit().putString("previous", "{}").commit()
        val file = IdentityFileFake()
        assertThrows(PortalIdentityStorageException::class.java) { testIdentityStore(file, old).load() }
        assertEquals(0, file.writes)
    }

    @Test fun corruptNewFileDoesNotFallBackToStaleLegacyIdentity() {
        val old = prefs()
        val file = IdentityFileFake().apply { bytes = "broken".toByteArray() }
        assertThrows(PortalIdentityStorageException::class.java) { testIdentityStore(file, old).load() }
        assertEquals(testPortalDeviceJson, old.getString("device", null))
        assertEquals(0, file.writes)
    }

    @Test fun unknownSchemaAndInvalidDeviceAreRejectedWithoutOverwriting() {
        for (raw in listOf("{}", """{"version":1}""", """{"version":2}""", """{"version":1,"device":{"code":"bad","sleutel":"123456","geheim":"${"a".repeat(43)}"},"previous":null,"registrationPending":false}""")) {
            val file = IdentityFileFake().apply { bytes = raw.toByteArray() }
            assertThrows(PortalIdentityStorageException::class.java) { testIdentityStore(file).load() }
            assertEquals(0, file.writes)
        }
    }

    @Test fun freshInstallDoesNotOpenKeystore() {
        val file = IdentityFileFake()
        val state = testIdentityStore(file, legacyExists = false, open = { error("No legacy file") }).load()
        assertNull(state.device)
        assertFalse(state.registrationPending)
        assertNotNull(file.bytes)
        assertEquals(1, file.writes)
    }

    @Test fun validFileRemainsUsableWhenLegacyCleanupFailed() {
        val file = IdentityFileFake()
        val device = Json.decodeFromString<PortalDevice>(testPortalDeviceJson)
        val store = PortalIdentityStore(file, object : LegacyPortalIdentity {
            override fun exists() = true
            override fun read() = PortalIdentityState(device = device)
            override fun clear() { throw IOException("Simulated cleanup failure") }
        })
        assertEquals(device.code, store.load().device?.code)
        assertEquals(device.code, testIdentityStore(file, open = { error("Must use new file") }).load().device?.code)
    }

    @Test fun registrationMarkerSurvivesNewStoreInstance() {
        val file = IdentityFileFake()
        testIdentityStore(file, legacyExists = false).save(PortalIdentityState(registrationPending = true))
        assertTrue(testIdentityStore(file, legacyExists = false).load().registrationPending)
    }
}

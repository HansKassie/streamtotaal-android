package nl.streamfix.data.local

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortalIdentityDeviceTest {
    @Test
    fun migratesEncryptedIdentityAndSurvivesSimulatedKeystoreFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureName = "portal_migration_test_${UUID.randomUUID()}"
        val path = File(context.noBackupFilesDir, "$fixtureName.json")
        val raw = """{"code":"12:34:56:78:9a:bc","sleutel":"123456","geheim":"${"a".repeat(43)}"}"""
        try {
            val encrypted = createSecurePrefs(context, fixtureName)
            assertFalse(encrypted is InMemorySharedPreferences)
            assertTrue(encrypted.edit().putString("device", raw).putString("previous", raw).commit())
            val store = PortalIdentityStore(
                AtomicPortalIdentityFile(path),
                PreferencesPortalIdentity({ true }, { encrypted }),
            )
            val migrated = store.load()
            assertEquals("12:34:56:78:9a:bc", migrated.device?.code)
            assertEquals("123456", migrated.previous?.sleutel)
            assertFalse(encrypted.contains("device"))
            assertFalse(encrypted.contains("previous"))
            assertEquals(context.noBackupFilesDir.canonicalFile, path.parentFile.canonicalFile)

            val reopened = PortalIdentityStore(
                AtomicPortalIdentityFile(path),
                PreferencesPortalIdentity({ true }, { error("Simulated broken Keystore") }),
            ).load()
            assertEquals(migrated.device?.geheim, reopened.device?.geheim)
            assertEquals(migrated.previous?.code, reopened.previous?.code)

            val atomic = AtomicFile(path)
            val partial = atomic.startWrite()
            partial.write("incomplete replacement".toByteArray())
            atomic.failWrite(partial)
            val afterInterruptedWrite = store.load()
            assertEquals(migrated.device?.geheim, afterInterruptedWrite.device?.geheim)
            assertEquals(migrated.previous?.sleutel, afterInterruptedWrite.previous?.sleutel)
        } finally {
            context.deleteSharedPreferences(fixtureName)
            AtomicFile(path).delete()
        }
    }
}

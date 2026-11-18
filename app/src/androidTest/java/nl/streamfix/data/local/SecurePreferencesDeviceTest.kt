package nl.streamfix.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurePreferencesDeviceTest {
    @Test
    fun encryptedDataSurvivesSimulatedTemporaryOpeningFailures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "maintenance_crypto_test_${UUID.randomUUID()}"
        try {
            val original = createSecurePrefs(context, fileName)
            val secret = "maintenance-test-secret-${UUID.randomUUID()}"
            assertTrue(original.edit().putString("probe", secret).commit())
            val file = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            assertFalse(file.contains("probe"))
            val onDisk = context.applicationInfo.dataDir.let {
                java.io.File(it, "shared_prefs/$fileName.xml")
            }
            val savedBytes = onDisk.readBytes()
            assertFalse(savedBytes.toString(Charsets.UTF_8).contains(secret))

            var attempts = 0
            val recovered = openSecurePrefsWithRetry {
                attempts++
                if (attempts == 1) throw IOException("Simulated temporary opening failure")
                createSecurePrefs(context, fileName)
            }
            assertEquals(2, attempts)
            assertEquals(secret, recovered.getString("probe", null))

            attempts = 0
            val fallback = openSecurePrefsWithRetry {
                attempts++
                throw IOException("Simulated repeated opening failure")
            }
            assertEquals(2, attempts)
            assertTrue(fallback.edit().putString("probe", "temporary-value").commit())
            assertArrayEquals(savedBytes, onDisk.readBytes())
            assertEquals(secret, createSecurePrefs(context, fileName).getString("probe", null))
        } finally {
            context.deleteSharedPreferences(fileName)
        }
    }
}

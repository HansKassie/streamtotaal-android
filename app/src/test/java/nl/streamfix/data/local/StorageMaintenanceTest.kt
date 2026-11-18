package nl.streamfix.data.local

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import nl.streamfix.domain.model.Account
import org.junit.Assert.*
import org.junit.Test

class StorageMaintenanceTest {
    @Test
    fun temporaryOpeningFailureRecoversExistingPreferences() {
        val original = InMemorySharedPreferences()
        original.edit().putString("device", "existing-registration").putString("adult_pin", "1234").apply()
        var attempts = 0
        val opened = openSecurePrefsWithRetry {
            attempts++
            if (attempts == 1) throw IOException("Temporarily unavailable")
            original
        }
        assertSame(original, opened)
        assertEquals(2, attempts)
        assertEquals("existing-registration", opened.getString("device", null))
        assertEquals("1234", opened.getString("adult_pin", null))
    }

    @Test
    fun failedOpeningUsesVolatileStorageAndAllowsLaterRecovery() {
        val original = InMemorySharedPreferences()
        original.edit().putString("device", "existing-registration").apply()
        var attempts = 0
        val fallback = openSecurePrefsWithRetry {
            attempts++
            throw IOException("Temporarily unavailable")
        }
        assertEquals(2, attempts)
        assertTrue(fallback.all.isEmpty())
        fallback.edit().putString("device", "temporary-registration").apply()
        val recovered = openSecurePrefsWithRetry { original }
        assertEquals("existing-registration", recovered.getString("device", null))
    }

    @Test
    fun cancellationDoesNotRetryOrCreateFallback() {
        var attempts = 0
        val cancellation = CancellationException("cancelled")
        try {
            openSecurePrefsWithRetry {
                attempts++
                throw cancellation
            }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertEquals(1, attempts)
        }
    }

    @Test
    fun invalidPinCannotReplacePinOrRelockUnlockedSession() {
        val prefs = InMemorySharedPreferences()
        val store = AppSettingsStore(prefs)
        store.setPin("1234")
        assertTrue(store.unlock("1234"))
        for (invalid in listOf("", " ", "123", "abcd", "12 34", "12a4")) {
            store.setPin(invalid)
            assertEquals("1234", prefs.getString("adult_pin", null))
            assertTrue(store.adultState.value.hasPin)
            assertTrue(store.adultState.value.unlocked)
        }
        store.setPin("5678")
        assertFalse(store.adultState.value.unlocked)
        assertTrue(store.unlock("5678"))
    }

    @Test
    fun concurrentAccountSavesKeepAllAccountsAndConsistentActiveState() {
        val prefs = InMemorySharedPreferences()
        val store = SecureCredentialStore(Json) { prefs }
        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val saves = (1..100).map { id ->
                workers.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    store.saveAndActivate(account(id.toString()))
                }
            }
            start.countDown()
            saves.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals((1..100).map { it.toString() }.toSet(), store.allAccounts().map { it.id }.toSet())
            assertEquals(store.currentActiveAccount(), store.activeAccount.value)
            val reopened = SecureCredentialStore(Json) { prefs }
            assertEquals(store.allAccounts(), reopened.allAccounts())
            assertEquals(store.currentActiveAccount(), reopened.currentActiveAccount())
            store.clear()
            assertTrue(store.allAccounts().isEmpty())
            assertNull(store.activeAccount.value)
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun accountMutationWaitsForReadModifyWriteToFinish() {
        val prefs = InMemorySharedPreferences()
        val store = SecureCredentialStore(Json) { prefs }
        store.saveAndActivate(account("one"))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val rename = workers.submit {
                store.renameAccounts {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    "Renamed"
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val save = workers.submit {
                secondStarted.countDown()
                store.saveAndActivate(account("two"))
                secondFinished.countDown()
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertFalse(secondFinished.await(200, TimeUnit.MILLISECONDS))
            release.countDown()
            rename.get(5, TimeUnit.SECONDS)
            save.get(5, TimeUnit.SECONDS)
            assertEquals("Renamed", store.allAccounts().first { it.id == "one" }.displayName)
            assertEquals("two", store.currentActiveAccount()?.id)
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    private fun account(id: String) = Account.Xtream(id, id, "https://example.test", "user", "password")
}

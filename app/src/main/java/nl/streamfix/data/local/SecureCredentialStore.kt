package nl.streamfix.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.streamfix.domain.model.Account

/**
 * Versleutelde opslag van credentials (briefing 6.2: AES-256 via Android
 * Keystore). Bestandsnaam matcht de backup-exclude in data_extraction_rules.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val prefs: SharedPreferences by lazy { createPrefs(context) }

    private fun createEncrypted(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Keystore-corruptie (bekend na restore/OS-upgrade) mag de app niet
    // laten crashen bij opstart: opruimen en opnieuw, anders degraderen.
    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            createEncrypted(context)
        } catch (e: Exception) {
            runCatching { context.deleteSharedPreferences(PREFS_FILE) }
            try {
                createEncrypted(context)
            } catch (e2: Exception) {
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    private val _activeAccount: MutableStateFlow<Account?> by lazy {
        MutableStateFlow(readState().activeAccount())
    }
    val activeAccount: StateFlow<Account?> by lazy { _activeAccount.asStateFlow() }

    fun currentActiveAccount(): Account? = readState().activeAccount()

    /** Voegt toe of werkt bij op id, en maakt het account actief. */
    fun saveAndActivate(account: Account) {
        val state = readState()
        val stored = account.toStored()
        val updated = state.accounts.filterNot { it.id == stored.id } + stored
        writeState(CredentialState(accounts = updated, activeId = stored.id))
    }

    /** Alle opgeslagen providers (voor de providerkeuze). */
    fun allAccounts(): List<Account> =
        readState().accounts.mapNotNull { it.toDomain() }

    /** Maakt een reeds opgeslagen provider actief. */
    fun setActive(id: String) {
        val state = readState()
        if (state.accounts.any { it.id == id }) {
            writeState(state.copy(activeId = id))
        }
    }

    /** Verwijdert één opgeslagen provider. Was die actief, dan geen actieve meer. */
    fun removeAccount(id: String) {
        val state = readState()
        writeState(
            CredentialState(
                accounts = state.accounts.filterNot { it.id == id },
                activeId = state.activeId?.takeIf { it != id },
            ),
        )
    }

    /** Zet het live-formaat ("auto"/"ts"/"m3u8") voor het actieve account. */
    fun setStreamFormat(format: String) {
        val state = readState()
        val activeId = state.activeId ?: return
        val updated = state.accounts.map {
            if (it.id == activeId) it.copy(streamFormat = format) else it
        }
        writeState(state.copy(accounts = updated))
    }

    /** Beeindigt de sessie maar bewaart opgeslagen providers (keuze gebruiker). */
    fun deactivate() {
        val state = readState()
        writeState(state.copy(activeId = null))
    }

    /** Wist alles (volledige reset). */
    fun clear() {
        prefs.edit().clear().apply()
        _activeAccount.value = null
    }

    private fun CredentialState.activeAccount(): Account? =
        accounts.firstOrNull { it.id == activeId }?.toDomain()

    private fun readState(): CredentialState {
        val raw = prefs.getString(KEY_STATE, null) ?: return CredentialState()
        return runCatching { json.decodeFromString<CredentialState>(raw) }
            .getOrDefault(CredentialState())
    }

    private fun writeState(state: CredentialState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(state)).apply()
        _activeAccount.value = state.activeAccount()
    }

    private companion object {
        const val PREFS_FILE = "streamfix_secure_prefs"
        const val KEY_STATE = "credential_state"
    }
}

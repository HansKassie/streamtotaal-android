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
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _activeAccount = MutableStateFlow(readState().activeAccount())
    val activeAccount: StateFlow<Account?> = _activeAccount.asStateFlow()

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

    /** Wist alles (briefing: logout wist alle gegevens). */
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

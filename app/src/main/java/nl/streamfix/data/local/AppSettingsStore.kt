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

/**
 * Kleine versleutelde app-instellingen los van de credentials. Nu alleen
 * de pincode + zichtbaarheid van volwassen content. Sessie-ontgrendeling
 * staat in geheugen (vervalt bij herstart van de app).
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = createPrefs(context)

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

    private fun createPrefs(context: Context): SharedPreferences =
        try {
            createEncrypted(context)
        } catch (e: Exception) {
            runCatching { context.deleteSharedPreferences(PREFS_FILE) }
            try {
                createEncrypted(context)
            } catch (e2: Exception) {
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }

    private val _adultState = MutableStateFlow(readAdultState())
    val adultState: StateFlow<AdultState> = _adultState.asStateFlow()

    private var sessionUnlocked = false

    private fun readAdultState() = AdultState(
        hasPin = !prefs.getString(KEY_PIN, null).isNullOrBlank(),
        hidden = prefs.getBoolean(KEY_HIDDEN, true),
        unlocked = false,
    )

    private fun emit() {
        _adultState.value = AdultState(
            hasPin = !prefs.getString(KEY_PIN, null).isNullOrBlank(),
            hidden = prefs.getBoolean(KEY_HIDDEN, true),
            unlocked = sessionUnlocked,
        )
    }

    /** True = volwassen content moet nu gefilterd worden. */
    fun adultFilterActive(): Boolean {
        val hidden = prefs.getBoolean(KEY_HIDDEN, true)
        return hidden && !sessionUnlocked
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).putBoolean(KEY_HIDDEN, true).apply()
        sessionUnlocked = false
        emit()
    }

    /** Ontgrendelt voor deze sessie als de pincode klopt. */
    fun unlock(pin: String): Boolean {
        val saved = prefs.getString(KEY_PIN, null)
        if (saved.isNullOrBlank() || saved != pin) return false
        sessionUnlocked = true
        emit()
        return true
    }

    fun hideAgain() {
        sessionUnlocked = false
        prefs.edit().putBoolean(KEY_HIDDEN, true).apply()
        emit()
    }

    private companion object {
        const val PREFS_FILE = "streamtotaal_settings"
        const val KEY_PIN = "adult_pin"
        const val KEY_HIDDEN = "adult_hidden"
    }
}

data class AdultState(
    val hasPin: Boolean,
    val hidden: Boolean,
    val unlocked: Boolean,
)

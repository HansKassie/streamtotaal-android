package nl.streamfix.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Geldige waarden voor het voorkeurs-startscherm. */
const val STARTUP_TAB_LIVE = "live_tv"
const val STARTUP_TAB_FAVORITES = "favorites"
const val STARTUP_TAB_HISTORY = "history"
const val STARTUP_TAB_LAST = "last_channel"

private val VALID_STARTUP_TABS = setOf(
    STARTUP_TAB_LIVE,
    STARTUP_TAB_FAVORITES,
    STARTUP_TAB_HISTORY,
    STARTUP_TAB_LAST,
)

/**
 * Kleine versleutelde app-instellingen los van de credentials. Nu alleen
 * de pincode + zichtbaarheid van volwassen content. Sessie-ontgrendeling
 * staat in geheugen (vervalt bij herstart van de app).
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = createSecurePrefs(context, PREFS_FILE)

    private val _adultState = MutableStateFlow(readAdultState())
    val adultState: StateFlow<AdultState> = _adultState.asStateFlow()

    // "auto" | "tv" | "phone" - weergavemodus-override.
    private val _tvMode =
        MutableStateFlow(prefs.getString(KEY_TV_MODE, "auto") ?: "auto")
    val tvMode: StateFlow<String> = _tvMode.asStateFlow()

    fun setTvMode(mode: String) {
        prefs.edit().putString(KEY_TV_MODE, mode).apply()
        _tvMode.value = mode
    }

    private val _startupTab = MutableStateFlow(readStartupTab())
    /** Tab waarop de hoofdscherm bij koude start opent. */
    val startupTab: StateFlow<String> = _startupTab.asStateFlow()

    private fun readStartupTab(): String {
        val raw = prefs.getString(KEY_STARTUP_TAB, null)
        return if (raw in VALID_STARTUP_TABS) raw!! else STARTUP_TAB_LIVE
    }

    fun setStartupTab(value: String) {
        val safe = if (value in VALID_STARTUP_TABS) value else STARTUP_TAB_LIVE
        prefs.edit().putString(KEY_STARTUP_TAB, safe).apply()
        _startupTab.value = safe
    }

    /** Laatst bekeken live-kanaal, accountgebonden opgeslagen. */
    fun setLastChannel(accountId: String, categoryId: String, channelId: String) {
        prefs.edit()
            .putString(KEY_LAST_CHANNEL, "$accountId|$categoryId|$channelId")
            .apply()
    }

    /** (categoryId, channelId) of null als er voor dit account niets is. */
    fun lastChannel(accountId: String): Pair<String, String>? {
        val raw = prefs.getString(KEY_LAST_CHANNEL, null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 3 || parts[0] != accountId) return null
        return parts[1] to parts[2]
    }

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
        const val KEY_TV_MODE = "tv_mode"
        const val KEY_STARTUP_TAB = "startup_tab"
        const val KEY_LAST_CHANNEL = "last_channel"
    }
}

data class AdultState(
    val hasPin: Boolean,
    val hidden: Boolean,
    val unlocked: Boolean,
)

/** True = volwassen content moet nu gefilterd worden. */
val AdultState.filterActive: Boolean get() = hidden && !unlocked

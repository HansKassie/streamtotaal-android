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

    /**
     * Laatst bekeken live-kanaal, met een eigen sleutel per account zodat
     * elke provider zijn eigen laatste zender onthoudt (wisselen naar
     * provider B wist de onthouden zender van provider A niet).
     */
    fun setLastChannel(accountId: String, categoryId: String, channelId: String) {
        prefs.edit()
            .putString(KEY_LAST_CHANNEL_PREFIX + accountId, "$categoryId|$channelId")
            .apply()
    }

    /** (categoryId, channelId) of null als er voor dit account niets is. */
    fun lastChannel(accountId: String): Pair<String, String>? {
        val raw = prefs.getString(KEY_LAST_CHANNEL_PREFIX + accountId, null)
            ?: return null
        val parts = raw.split('|')
        if (parts.size != 2) return null
        return parts[0] to parts[1]
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

    // Brute-force-rem: na te veel foute pogingen even wachten. In geheugen
    // (vervalt bij herstart); het dreigingsmodel is een kind met de
    // afstandsbediening, geen aanvaller met adb.
    private var failedUnlockAttempts = 0
    private var unlockLockedUntilMs = 0L

    /** Resterende wachttijd in seconden na te veel foute pogingen (0 = vrij). */
    fun unlockRetryWaitSeconds(): Int {
        val left = unlockLockedUntilMs - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    /** Ontgrendelt voor deze sessie als de pincode klopt. */
    fun unlock(pin: String): Boolean {
        if (unlockRetryWaitSeconds() > 0) return false
        val saved = prefs.getString(KEY_PIN, null)
        if (saved.isNullOrBlank() || saved != pin) {
            failedUnlockAttempts++
            if (failedUnlockAttempts >= MAX_UNLOCK_ATTEMPTS) {
                failedUnlockAttempts = 0
                unlockLockedUntilMs =
                    System.currentTimeMillis() + UNLOCK_LOCKOUT_MS
            }
            return false
        }
        failedUnlockAttempts = 0
        unlockLockedUntilMs = 0L
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
        const val KEY_LAST_CHANNEL_PREFIX = "last_channel_"
        const val MAX_UNLOCK_ATTEMPTS = 5
        const val UNLOCK_LOCKOUT_MS = 30_000L
    }
}

data class AdultState(
    val hasPin: Boolean,
    val hidden: Boolean,
    val unlocked: Boolean,
)

/** True = volwassen content moet nu gefilterd worden. */
val AdultState.filterActive: Boolean get() = hidden && !unlocked

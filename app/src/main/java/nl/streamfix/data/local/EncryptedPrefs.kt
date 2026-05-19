package nl.streamfix.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Versleutelde SharedPreferences met een fail-closed fallback.
 *
 * Keystore-corruptie (bekend na restore/OS-upgrade) mag de app niet laten
 * crashen. We proberen eerst opnieuw na het wissen van het bestand. Lukt
 * versleutelen daarna nog niet, dan vallen we NIET terug op een plat
 * MODE_PRIVATE-bestand (dat zou credentials/pincode onversleuteld op schijf
 * zetten), maar op een niet-persistente in-memory store: niets gevoeligs op
 * schijf, geen crash, en bij herstart is er geen sessie meer (herinloggen
 * afgedwongen, adult-filter valt terug op verborgen).
 */
internal fun createSecurePrefs(
    context: Context,
    fileName: String,
): SharedPreferences {
    fun encrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    return try {
        encrypted()
    } catch (e: Exception) {
        runCatching { context.deleteSharedPreferences(fileName) }
        try {
            encrypted()
        } catch (e2: Exception) {
            InMemorySharedPreferences()
        }
    }
}

/**
 * Minimale niet-persistente SharedPreferences. Alleen gebruikt als
 * versleuteling onbeschikbaar is; bewust vluchtig zodat er geen platte
 * credentials achterblijven.
 */
internal class InMemorySharedPreferences : SharedPreferences {

    private val map = ConcurrentHashMap<String, Any?>()
    private val listeners =
        java.util.Collections.newSetFromMap(
            ConcurrentHashMap<
                SharedPreferences.OnSharedPreferenceChangeListener,
                Boolean,
                >(),
        )

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String?, defValue: String?): String? =
        map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? =
        (map[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (map[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (map[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (map[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (map[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.remove(it) }
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?) = apply {
            if (key != null) pending[key] = value
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ) = apply { if (key != null) pending[key] = values?.toSet() }

        override fun putInt(key: String?, value: Int) = apply {
            if (key != null) pending[key] = value
        }

        override fun putLong(key: String?, value: Long) = apply {
            if (key != null) pending[key] = value
        }

        override fun putFloat(key: String?, value: Float) = apply {
            if (key != null) pending[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean) = apply {
            if (key != null) pending[key] = value
        }

        override fun remove(key: String?) = apply {
            if (key != null) pending[key] = REMOVED
        }

        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            val changed = mutableListOf<String>()
            synchronized(map) {
                if (clearAll) {
                    changed += map.keys
                    map.clear()
                }
                for ((k, v) in pending) {
                    if (v === REMOVED) {
                        if (map.remove(k) != null) changed += k
                    } else {
                        map[k] = v
                        changed += k
                    }
                }
            }
            if (changed.isNotEmpty()) {
                val snapshot = listeners.toList()
                for (k in changed) {
                    snapshot.forEach {
                        it.onSharedPreferenceChanged(
                            this@InMemorySharedPreferences,
                            k,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val REMOVED = Any()
    }
}

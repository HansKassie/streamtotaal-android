package nl.streamfix.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val LEGACY_PORTAL_PREFS = "streamfix_portal_device"
internal const val PORTAL_IDENTITY_FILE = "streamfix_portal_device.json"

@Serializable
internal class PortalDevice(val code: String, val sleutel: String, val geheim: String) {
    fun valid() = Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(code) &&
        Regex("[0-9]{6}").matches(sleutel) && Regex("[A-Za-z0-9_-]{43}").matches(geheim)
}

@Serializable
internal data class PortalIdentityState(
    @Required val version: Int = 1,
    @Required val device: PortalDevice? = null,
    @Required val previous: PortalDevice? = null,
    @Required val registrationPending: Boolean = false,
)

internal class PortalIdentityStorageException : IOException("Portal identity storage unavailable")

/** Null betekent uitsluitend: nog geen bestand. Onleesbaar is een fout. */
internal interface PortalIdentityFile {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

internal class AtomicPortalIdentityFile(private val path: File) : PortalIdentityFile {
    private val atomic = AtomicFile(path)

    override fun read(): ByteArray? {
        if (!path.exists() && !File(path.path + ".bak").exists() && !File(path.path + ".new").exists()) {
            return null
        }
        return atomic.openRead().use { input ->
            val buffer = ByteArray(16_385)
            var size = 0
            while (size < buffer.size) {
                val read = input.read(buffer, size, buffer.size - size)
                if (read < 0) break
                size += read
            }
            if (size == buffer.size) throw PortalIdentityStorageException()
            buffer.copyOf(size)
        }
    }

    override fun write(bytes: ByteArray) {
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }
}

internal interface LegacyPortalIdentity {
    fun exists(): Boolean
    fun read(): PortalIdentityState
    fun clear()
}

internal class PreferencesPortalIdentity(
    private val present: () -> Boolean,
    private val open: () -> SharedPreferences,
) : LegacyPortalIdentity {
    private val prefs by lazy { open() }
    private val json = Json { ignoreUnknownKeys = true }

    override fun exists() = present()

    override fun read(): PortalIdentityState {
        if (prefs is InMemorySharedPreferences) throw PortalIdentityStorageException()
        fun read(key: String): PortalDevice? = prefs.getString(key, null)?.let {
            json.decodeFromString<PortalDevice>(it).also { device ->
                if (!device.valid()) throw PortalIdentityStorageException()
            }
        }
        return PortalIdentityState(device = read("device"), previous = read("previous"))
    }

    override fun clear() {
        // Alleen deze twee velden, pas nadat de nieuwe opslag is teruggelezen.
        prefs.edit().remove("device").remove("previous").commit()
    }
}

/** App-private, buiten back-ups, en onafhankelijk van de Android Keystore. */
internal class PortalIdentityStore(
    private val file: PortalIdentityFile,
    private val legacy: LegacyPortalIdentity,
) {
    private val json = Json { encodeDefaults = true }

    @Synchronized
    fun load(): PortalIdentityState = guarded {
        file.read()?.let { return@guarded decode(it) }
        val migrating = legacy.exists()
        val state = if (migrating) legacy.read() else PortalIdentityState()
        save(state)
        if (migrating) {
            try {
                legacy.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // De gecontroleerde nieuwe kopie is al leidend. Oud blijft bewaard.
            }
        }
        state
    }

    @Synchronized
    fun save(state: PortalIdentityState) = guarded {
        validate(state)
        val bytes = json.encodeToString(state).toByteArray(Charsets.UTF_8)
        file.write(bytes)
        val reopened = file.read() ?: throw PortalIdentityStorageException()
        if (!bytes.contentEquals(reopened)) throw PortalIdentityStorageException()
        decode(reopened)
        Unit
    }

    private fun decode(bytes: ByteArray): PortalIdentityState =
        json.decodeFromString<PortalIdentityState>(bytes.toString(Charsets.UTF_8)).also(::validate)

    private fun validate(state: PortalIdentityState) {
        if (state.version != 1 || state.device?.valid() == false || state.previous?.valid() == false) {
            throw PortalIdentityStorageException()
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Geen credentials of JSON in exceptions die een crashdienst kan ontvangen.
        throw PortalIdentityStorageException()
    }
}

internal fun createPortalIdentityStore(context: Context): PortalIdentityStore = try {
    val old = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PORTAL_PREFS.xml")
    PortalIdentityStore(
        AtomicPortalIdentityFile(File(context.noBackupFilesDir, PORTAL_IDENTITY_FILE)),
        PreferencesPortalIdentity(
            { old.exists() || File(old.path + ".bak").exists() },
            { createSecurePrefs(context, LEGACY_PORTAL_PREFS) },
        ),
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    throw PortalIdentityStorageException()
}

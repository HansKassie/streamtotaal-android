package nl.streamfix.data.local

import kotlinx.serialization.Serializable
import nl.streamfix.domain.model.Account

/**
 * Platte, serialiseerbare representatie van een account voor opslag.
 * De type-discriminator houdt de opslag uitbreidbaar zonder polymorfe
 * serialisatie.
 */
@Serializable
data class StoredAccount(
    val id: String,
    val displayName: String,
    val type: String, // "xtream"
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val liveExtension: String = "ts",
    val streamFormat: String = "auto",
)

@Serializable
data class CredentialState(
    val accounts: List<StoredAccount> = emptyList(),
    val activeId: String? = null,
)

fun StoredAccount.toDomain(): Account? {
    return when (type) {
        "xtream" -> Account.Xtream(
            id = id,
            displayName = displayName,
            serverUrl = serverUrl ?: return null,
            username = username ?: return null,
            password = password ?: return null,
            liveExtension = liveExtension,
            streamFormat = streamFormat,
        )

        else -> null
    }
}

fun Account.toStored(): StoredAccount = when (this) {
    is Account.Xtream -> StoredAccount(
        id = id,
        displayName = displayName,
        type = "xtream",
        serverUrl = serverUrl,
        username = username,
        password = password,
        liveExtension = liveExtension,
        streamFormat = streamFormat,
    )
}

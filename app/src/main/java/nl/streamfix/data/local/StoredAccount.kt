package nl.streamfix.data.local

import kotlinx.serialization.Serializable
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.M3uSource

/**
 * Platte, serialiseerbare representatie van een account voor opslag.
 * Bewust geen polymorfe serialisatie: een type-discriminator is simpeler
 * en robuuster bij latere schema-wijzigingen.
 */
@Serializable
data class StoredAccount(
    val id: String,
    val displayName: String,
    val type: String, // "xtream" | "m3u"
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val m3uUrl: String? = null,
    val m3uFileUri: String? = null,
)

@Serializable
data class CredentialState(
    val accounts: List<StoredAccount> = emptyList(),
    val activeId: String? = null,
)

fun StoredAccount.toDomain(): Account? = when (type) {
    "xtream" -> Account.Xtream(
        id = id,
        displayName = displayName,
        serverUrl = serverUrl ?: return null,
        username = username ?: return null,
        password = password ?: return null,
    )

    "m3u" -> {
        val source = when {
            m3uUrl != null -> M3uSource.Url(m3uUrl)
            m3uFileUri != null -> M3uSource.LocalFile(m3uFileUri)
            else -> return null
        }
        Account.M3u(id = id, displayName = displayName, source = source)
    }

    else -> null
}

fun Account.toStored(): StoredAccount = when (this) {
    is Account.Xtream -> StoredAccount(
        id = id,
        displayName = displayName,
        type = "xtream",
        serverUrl = serverUrl,
        username = username,
        password = password,
    )

    is Account.M3u -> StoredAccount(
        id = id,
        displayName = displayName,
        type = "m3u",
        m3uUrl = (source as? M3uSource.Url)?.url,
        m3uFileUri = (source as? M3uSource.LocalFile)?.uri,
    )
}

package nl.streamfix.data.repository

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.remote.PortalClient
import nl.streamfix.data.remote.PortalResult
import nl.streamfix.data.remote.XtreamAuthService
import nl.streamfix.data.remote.XtreamUrls
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.util.AppResult

class PortalAccounts @Inject constructor(
    private val portal: PortalClient,
    private val auth: XtreamAuthService,
    private val store: SecureCredentialStore,
) {
    suspend fun refresh(register: Boolean, activate: Boolean): PortalResult = withContext(Dispatchers.IO) {
        val result = portal.status(register)
        when (result) {
            is PortalResult.Active -> {
                val stored = store.allAccounts().filterIsInstance<Account.Xtream>().associateBy { it.id }
                val accounts = result.playlists.map { playlist ->
                    val id = "portal:${playlist.id}"
                    val server = XtreamUrls.normalizeServerUrl(playlist.server)
                    val old = stored[id]
                    if (old != null && old.serverUrl == server && old.username == playlist.username &&
                        old.password == playlist.password
                    ) {
                        old.copy(displayName = playlist.name.trim())
                    } else {
                        val data = auth.authenticate(server, playlist.username, playlist.password)
                        val formats = (data as? AppResult.Success)?.data?.userInfo?.outputFormats.orEmpty()
                        Account.Xtream(
                            id = id,
                            displayName = playlist.name.trim(),
                            serverUrl = server,
                            username = playlist.username,
                            password = playlist.password,
                            liveExtension = when {
                                "m3u8" in formats -> "m3u8"
                                "ts" in formats -> "ts"
                                else -> formats.firstOrNull() ?: "ts"
                            },
                            streamFormat = old?.streamFormat ?: "auto",
                            supportsHls = formats.isEmpty() || "m3u8" in formats,
                        )
                    }
                }
                store.syncPortalAccounts(accounts, activate)
            }
            is PortalResult.Pending, PortalResult.Blocked -> store.syncPortalAccounts(emptyList(), false)
            else -> Unit
        }
        result
    }
}

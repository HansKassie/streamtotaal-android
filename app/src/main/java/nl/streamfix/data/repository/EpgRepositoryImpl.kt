package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.local.db.CachedProgramme
import nl.streamfix.data.local.db.EpgCacheEntity
import nl.streamfix.data.local.db.EpgDao
import nl.streamfix.data.remote.XtreamEpgService
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.repository.EpgRepository
import nl.streamfix.domain.util.AppResult
import nl.streamfix.domain.util.EpgNowNext

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val epgService: XtreamEpgService,
    private val store: SecureCredentialStore,
    private val dao: EpgDao,
    private val json: Json,
) : EpgRepository {

    @Volatile
    private var pruned = false

    /** Eenmaal per proces: rijen ouder dan 48 uur opruimen (TTL is max 3u). */
    private suspend fun pruneOnce() {
        if (pruned) return
        pruned = true
        runCatching {
            dao.prune(System.currentTimeMillis() - PRUNE_AGE_MS)
        }
    }

    override suspend fun getEpg(streamId: String): AppResult<List<EpgProgramme>> {
        pruneOnce()
        val acc = store.currentActiveAccount() as? Account.Xtream
            ?: return AppResult.Failure(AppError.Unknown)

        val cached = dao.get(acc.id, streamId)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < TTL_MS) {
            // Vers op ophaaltijd is niet hetzelfde als bruikbaar. De korte
            // gids beslaat maar een paar programma's, dus een lijst van
            // 10:00 kan om 12:00 helemaal in het verleden liggen en toch
            // binnen de TTL vallen. Dan toont "Nu en straks" oude titels of
            // ten onrechte geen gids, terwijl opnieuw ophalen het wel weet.
            //
            // Een LEGE lijst blijft wel geldig: die betekent dat deze zender
            // geen gids heeft, en elke keer opnieuw vragen levert een reeks
            // zinloze aanvragen op zonder ooit een ander antwoord.
            val programmes = decode(cached.payload)
            if (programmes.isEmpty() ||
                EpgNowNext.stillRelevant(programmes, now)
            ) {
                return AppResult.Success(programmes)
            }
        }

        return when (
            val r = epgService.shortEpg(
                acc.serverUrl, acc.username, acc.password, streamId,
            )
        ) {
            is AppResult.Success -> {
                runCatching {
                    dao.upsert(
                        EpgCacheEntity(
                            accountId = acc.id,
                            streamId = streamId,
                            payload = encode(r.data),
                            fetchedAt = now,
                        ),
                    )
                }
                r
            }
            is AppResult.Failure ->
                // Offline-val terug op (verlopen) cache als die er is.
                if (cached != null) AppResult.Success(decode(cached.payload))
                else r
        }
    }

    override suspend fun getEpgTable(
        streamId: String,
    ): AppResult<List<EpgProgramme>> {
        pruneOnce()
        val acc = store.currentActiveAccount() as? Account.Xtream
            ?: return AppResult.Failure(AppError.Unknown)

        // Eigen cachesleutel zodat het de korte EPG-cache niet overschrijft.
        val cacheKey = "tbl:$streamId"
        val cached = dao.get(acc.id, cacheKey)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < TABLE_TTL_MS) {
            return AppResult.Success(decode(cached.payload))
        }

        return when (
            val r = epgService.epgTable(
                acc.serverUrl, acc.username, acc.password, streamId,
            )
        ) {
            is AppResult.Success -> {
                runCatching {
                    dao.upsert(
                        EpgCacheEntity(
                            accountId = acc.id,
                            streamId = cacheKey,
                            payload = encode(r.data),
                            fetchedAt = now,
                        ),
                    )
                }
                r
            }
            is AppResult.Failure ->
                if (cached != null) AppResult.Success(decode(cached.payload))
                else r
        }
    }

    private fun encode(list: List<EpgProgramme>): String =
        json.encodeToString(
            list.map { CachedProgramme(it.title, it.description, it.startMs, it.endMs) },
        )

    private fun decode(payload: String): List<EpgProgramme> =
        runCatching {
            json.decodeFromString<List<CachedProgramme>>(payload).map {
                EpgProgramme(it.title, it.description, it.startMs, it.endMs)
            }
        }.getOrDefault(emptyList())

    private companion object {
        const val TTL_MS = 3 * 60 * 60 * 1000L // 3 uur
        const val TABLE_TTL_MS = 60 * 60 * 1000L // 1 uur (catch-up-tabel)
        const val PRUNE_AGE_MS = 48 * 60 * 60 * 1000L // 48 uur
    }
}

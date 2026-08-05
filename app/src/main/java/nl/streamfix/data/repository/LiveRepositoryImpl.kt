package nl.streamfix.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import nl.streamfix.data.local.AdultContent
import nl.streamfix.data.local.AppSettingsStore
import nl.streamfix.data.local.SecureCredentialStore
import nl.streamfix.data.local.filterActive
import nl.streamfix.data.local.db.FavoriteChannelEntity
import nl.streamfix.data.local.db.FavoriteDao
import nl.streamfix.data.local.db.hiddenByAdultFilter
import nl.streamfix.data.remote.XtreamLiveService
import nl.streamfix.data.remote.XtreamUrls
import nl.streamfix.domain.model.Account
import nl.streamfix.domain.model.AppError
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.util.AppResult

@Singleton
class LiveRepositoryImpl @Inject constructor(
    private val liveService: XtreamLiveService,
    private val store: SecureCredentialStore,
    private val favoriteDao: FavoriteDao,
    private val appSettings: AppSettingsStore,
) : LiveRepository {

    private fun activeXtream(): Account.Xtream? =
        store.currentActiveAccount() as? Account.Xtream

    override suspend fun getCategories(): AppResult<List<LiveCategory>> {
        val acc = activeXtream() ?: return AppResult.Failure(AppError.Unknown)
        val r = liveService.categories(acc.serverUrl, acc.username, acc.password)
        return if (r is AppResult.Success && appSettings.adultFilterActive()) {
            AppResult.Success(r.data.filterNot { AdultContent.isAdult(it.name) })
        } else {
            r
        }
    }

    override suspend fun getChannels(
        categoryId: String?,
    ): AppResult<List<LiveChannel>> {
        val acc = activeXtream() ?: return AppResult.Failure(AppError.Unknown)
        val r = liveService.channels(
            acc.serverUrl, acc.username, acc.password, categoryId,
        )
        // Backfill op de ONGEFILTERDE lijst: favorieten uit een volwassen
        // categorie moeten juist geclassificeerd worden, ook (en vooral) als
        // ze straks weggefilterd worden.
        if (r is AppResult.Success) classifyFavorites(acc, r.data)
        if (r !is AppResult.Success || !appSettings.adultFilterActive()) return r
        // "Alle kanalen" (zoeken/gemist): kanalen uit volwassen-categorieen
        // weglaten op basis van de categorie-namen.
        val adultIds = adultCategoryIds(acc)
        return AppResult.Success(
            r.data.filterNot {
                it.categoryId in adultIds || AdultContent.isAdult(it.name)
            },
        )
    }

    // Sessie-cache per account: categorienamen wijzigen zelden, dus de
    // extra categories()-call (Zoeken/Gemist met filter aan) hoeft niet
    // bij elke aanroep. Benigne race: hooguit een dubbele fetch.
    @Volatile
    private var adultIdsCache: Pair<String, Set<String>>? = null

    private suspend fun adultCategoryIds(acc: Account.Xtream): Set<String> {
        adultIdsCache?.let { (accountId, ids) ->
            if (accountId == acc.id) return ids
        }
        val c = liveService.categories(acc.serverUrl, acc.username, acc.password)
        return if (c is AppResult.Success) {
            val ids = c.data.filter { AdultContent.isAdult(it.name) }
                .map { it.id }.toSet()
            adultIdsCache = acc.id to ids
            ids
        } else {
            // Fout niet cachen; volgende aanroep probeert opnieuw.
            emptySet()
        }
    }

    /**
     * Vult ontbrekende volwassen-classificaties van favorieten aan zodra de
     * bijbehorende kanalen toch al opgehaald zijn. Zonder dit blijft een
     * favoriet uit een volwassen categorie met neutrale naam zichtbaar,
     * omdat de opgeslagen rij geen categorie kent.
     */
    private suspend fun classifyFavorites(
        acc: Account.Xtream,
        channels: List<LiveChannel>,
    ) {
        val pending = runCatching { favoriteDao.unclassified(acc.id) }
            .getOrDefault(emptyList())
        if (pending.isEmpty()) return
        val byId = channels.associateBy { it.id }
        val adultIds = adultCategoryIds(acc)
        pending.forEach { fav ->
            val channel = byId[fav.channelId] ?: return@forEach
            val adult = AdultContent.isAdult(channel.name) ||
                (channel.categoryId != null && channel.categoryId in adultIds)
            runCatching {
                favoriteDao.classify(
                    accountId = acc.id,
                    channelId = fav.channelId,
                    categoryId = channel.categoryId,
                    isAdult = adult,
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<LiveChannel>> =
        store.activeAccount.flatMapLatest { account ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                combine(
                    favoriteDao.observe(account.id),
                    appSettings.adultState,
                ) { list, adult ->
                    list
                        .filterNot { it.hiddenByAdultFilter(adult.filterActive) }
                        .map {
                            LiveChannel(
                                id = it.channelId,
                                name = it.name,
                                logoUrl = it.logoUrl,
                                categoryId = null,
                                epgChannelId = null,
                            )
                        }
                }
            }
        }

    override suspend fun setFavorite(channel: LiveChannel, favorite: Boolean) {
        val acc = activeXtream() ?: return
        if (favorite) {
            // Nu classificeren, want hier is de categorie nog bekend; later
            // (vanuit de favorietenlijst) is die informatie weg.
            val adult = AdultContent.isAdult(channel.name) ||
                (
                    channel.categoryId != null &&
                        channel.categoryId in adultCategoryIds(acc)
                    )
            favoriteDao.add(
                FavoriteChannelEntity(
                    accountId = acc.id,
                    channelId = channel.id,
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    categoryId = channel.categoryId,
                    isAdult = adult,
                ),
            )
        } else {
            favoriteDao.remove(acc.id, channel.id)
        }
    }

    override fun streamUrl(channelId: String): String? {
        val acc = activeXtream() ?: return null
        val ext = when (acc.streamFormat) {
            "ts" -> "ts"
            "m3u8" -> "m3u8"
            else -> acc.liveExtension
        }
        return XtreamUrls.liveStream(
            acc.serverUrl, acc.username, acc.password, channelId,
            extension = ext,
        )
    }

    override fun streamUrlForCast(channelId: String): String? {
        val acc = activeXtream() ?: return null
        // Chromecast-ontvanger kan geen rauwe .ts; forceer HLS. Panels die
        // expliciet geen m3u8 leveren: null, zodat de UI de cast-knop
        // verbergt i.p.v. een stil zwart scherm op de ontvanger.
        if (!acc.supportsHls) return null
        return XtreamUrls.liveStream(
            acc.serverUrl, acc.username, acc.password, channelId,
            extension = "m3u8",
        )
    }

    override fun timeshiftUrl(
        channelId: String,
        startMs: Long,
        durationMin: Int,
    ): String? {
        val acc = activeXtream() ?: return null
        return XtreamUrls.timeshift(
            acc.serverUrl, acc.username, acc.password, channelId,
            startMs = startMs,
            durationMin = durationMin,
            extension = "ts",
        )
    }

    override fun rememberLastChannel(categoryId: String, channel: LiveChannel) {
        val acc = activeXtream() ?: return
        // Volwassen kanalen niet onthouden: automatisch afspelen bij start
        // zou anders het PIN-slot omzeilen (sessie-ontgrendeling vervalt
        // bij afsluiten). Naast de naam ook de categorie wegen; die is hier
        // alleen bekend via de sessie-cache (deze functie is niet suspend).
        val adultIds = adultIdsCache
            ?.takeIf { it.first == acc.id }?.second.orEmpty()
        if (AdultContent.isAdult(channel.name) || categoryId in adultIds) return
        appSettings.setLastChannel(acc.id, categoryId, channel.id)
    }

    override fun lastWatchedChannel(): Pair<String, String>? {
        val acc = activeXtream() ?: return null
        return appSettings.lastChannel(acc.id)
    }
}

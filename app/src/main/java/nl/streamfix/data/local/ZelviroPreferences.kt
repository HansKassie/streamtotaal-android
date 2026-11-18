package nl.streamfix.data.local

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.streamfix.BuildConfig
import nl.streamfix.domain.model.LiveCategory

internal val isZelviro get() = BuildConfig.FLAVOR == "zelviro"

enum class CatalogSection { Live, Movies, Series }

data class LanguagePreferences(val app: String, val audio: String, val subtitles: String)

fun appLanguage(choice: String, device: String): String =
    (if (choice == "auto") device else choice).takeIf { it in setOf("nl", "en", "de") } ?: "en"

internal fun catalogKey(id: String?, group: String?): String? =
    id?.takeIf { it.isNotBlank() } ?: group?.trim()?.takeIf { it.isNotEmpty() }

fun LiveCategory.visibilityKey(): String = catalogKey(id, name).orEmpty()

internal fun catalogPreferenceKey(accountId: String, section: CatalogSection) =
    "hidden:${section.name}:$accountId"

internal fun hideCurrentCategories(saved: Set<String>, current: Iterable<String>) = saved + current

internal fun isCatalogItemVisible(ids: Set<String>, current: Set<String>, hidden: Set<String>): Boolean {
    val hiddenNow = hidden.intersect(current)
    if (hiddenNow.isEmpty()) return true
    return ids.any { it in current && it !in hiddenNow }
}

private const val PREFERENCES_NAME = "zelviro_preferences"

internal fun Context.zelviroTextContext(): Context {
    if (!isZelviro) return this
    val choice = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getString("app", null) ?: "auto"
    val current = resources.configuration
    val locale = appLanguage(choice, applicationContext.resources.configuration.locales[0].language)
    if (current.locales[0].language == locale) return this
    return createConfigurationContext(Configuration(current).apply { setLocales(LocaleList(Locale.forLanguageTag(locale))) })
}

@Singleton
class ZelviroPreferences internal constructor(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
    private val _languages = MutableStateFlow(LanguagePreferences(
        prefs.getString("app", "auto") ?: "auto",
        prefs.getString("audio", "auto") ?: "auto",
        prefs.getString("subtitles", "auto") ?: "auto",
    ))
    val languages = _languages.asStateFlow()
    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision = _catalogRevision.asStateFlow()

    fun setLanguages(value: LanguagePreferences) {
        prefs.edit().putString("app", value.app).putString("audio", value.audio)
            .putString("subtitles", value.subtitles).apply()
        _languages.value = value
    }

    fun hidden(accountId: String, section: CatalogSection): Set<String> =
        if (isZelviro) prefs.getStringSet(catalogPreferenceKey(accountId, section), emptySet()).orEmpty().toSet()
        else emptySet()

    fun setHidden(accountId: String, section: CatalogSection, ids: Set<String>) {
        prefs.edit().putStringSet(catalogPreferenceKey(accountId, section), ids.toSet()).apply()
        _catalogRevision.value++
    }
}

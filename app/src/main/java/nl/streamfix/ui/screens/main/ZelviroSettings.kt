package nl.streamfix.ui.screens.main

import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import nl.streamfix.ui.LocalIsTv
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import nl.streamfix.R
import nl.streamfix.data.local.*
import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.repository.*
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.tvFocusable

val LocalZelviroLanguages = staticCompositionLocalOf { LanguagePreferences("en", "auto", "auto") }

@Composable
fun ZelviroLocale(preferences: ZelviroPreferences, content: @Composable () -> Unit) {
    if (!isZelviro) {
        content()
        return
    }
    val current by preferences.languages.collectAsStateWithLifecycle()
    val original = LocalConfiguration.current
    val context = LocalContext.current
    val language = appLanguage(current.app, original.locales[0].language)
    val config = remember(original, language) {
        Configuration(original).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
    }
    val localized = remember(context, config) {
        val configured = context.createConfigurationContext(config)
        // Hilt en navigatie moeten de Activity via de contextketen kunnen vinden.
        object : ContextWrapper(context) {
            override fun getResources() = configured.resources
            override fun getAssets() = configured.assets
        }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides config,
        LocalZelviroLanguages provides current.copy(app = language),
        content = content,
    )
}

@HiltViewModel
class ZelviroSettingsViewModel @Inject constructor(
    val preferences: ZelviroPreferences,
    private val live: LiveRepository,
    private val movies: VodRepository,
    private val series: SeriesRepository,
) : ViewModel() {
    suspend fun categories(section: CatalogSection): AppResult<List<LiveCategory>> = when (section) {
        CatalogSection.Live -> live.getManageableCategories()
        CatalogSection.Movies -> movies.getManageableCategories()
        CatalogSection.Series -> series.getManageableCategories()
    }
}

@Composable
fun ZelviroSettings(accountId: String?, viewModel: ZelviroSettingsViewModel = hiltViewModel()) {
    val prefs = viewModel.preferences
    val value by prefs.languages.collectAsStateWithLifecycle()
    val locale = Locale.forLanguageTag(LocalZelviroLanguages.current.app)
    val automatic = stringResource(R.string.zelviro_auto)
    val off = stringResource(R.string.zelviro_off)
    val languages = remember(locale) {
        val first = listOf("nl", "en", "de", "fr", "es", "it", "pt", "tr", "pl", "ar")
        val collator = Collator.getInstance(locale)
        val rest = Locale.getISOLanguages().filterNot { it in first }
            .sortedWith { a, b -> collator.compare(Locale.forLanguageTag(a).getDisplayLanguage(locale),
                Locale.forLanguageTag(b).getDisplayLanguage(locale)) }
        (first + rest).map { it to Locale.forLanguageTag(it).getDisplayLanguage(locale) }
            .distinctBy { it.second.lowercase(locale) }
    }
    val auto = listOf("auto" to automatic)
    LanguageSetting(stringResource(R.string.zelviro_app_language), value.app,
        auto + listOf("nl" to "Nederlands", "en" to "English", "de" to "Deutsch")) {
        prefs.setLanguages(value.copy(app = it))
    }
    LanguageSetting(stringResource(R.string.zelviro_audio_language), value.audio, auto + languages) {
        prefs.setLanguages(value.copy(audio = it))
    }
    LanguageSetting(stringResource(R.string.zelviro_subtitle_language), value.subtitles,
        auto + ("off" to off) + languages) {
        prefs.setLanguages(value.copy(subtitles = it))
    }
    if (accountId == null) return
    var open by remember(accountId) { mutableStateOf<CatalogSection?>(null) }
    for (section in CatalogSection.entries) {
        val title = sectionTitle(section)
        OutlinedButton(onClick = { open = section }, modifier = Modifier.tvFocusable()) {
            Text(stringResource(R.string.zelviro_hide_section, title))
        }
    }
    open?.let { section ->
        CategoryVisibilityDialog(accountId, section, viewModel, onClose = { open = null })
    }
}

@Composable
private fun sectionTitle(section: CatalogSection): String = stringResource(when (section) {
    CatalogSection.Live -> R.string.tab_live_tv
    CatalogSection.Movies -> R.string.tab_movies
    CatalogSection.Series -> R.string.tab_series_short
})

@Composable
private fun LanguageSetting(title: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.tvFocusable()) {
        Text("$title: ${options.firstOrNull { it.first == value }?.second.orEmpty()}")
    }
    if (!open) return
    val focus = remember { FocusRequester() }
    val isTv = LocalIsTv.current
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(options, key = { it.first }) { (id, name) ->
                    val row = if (id == options.first().first) Modifier.focusRequester(focus) else Modifier
                    Row(row.fillMaxWidth().tvFocusable().selectable(
                        selected = id == value, role = Role.RadioButton,
                        onClick = {
                            onSelect(id)
                            open = false
                        },
                    ).padding(10.dp)) {
                        if (id == options.first().first && isTv) LaunchedEffect(Unit) { focus.requestFocus() }
                        RadioButton(selected = id == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.zelviro_close)) } },
    )
}

@Composable
private fun CategoryVisibilityDialog(accountId: String, section: CatalogSection, viewModel: ZelviroSettingsViewModel, onClose: () -> Unit) {
    val prefs = viewModel.preferences
    var hidden by remember(accountId, section) { mutableStateOf(prefs.hidden(accountId, section)) }
    var result by remember(accountId, section) { mutableStateOf<AppResult<List<LiveCategory>>?>(null) }
    LaunchedEffect(accountId, section) {
        result = viewModel.categories(section)
    }
    val categories = (result as? AppResult.Success)?.data.orEmpty().filter { it.visibilityKey().isNotEmpty() }.distinctBy { it.visibilityKey() }
    fun save(ids: Set<String>) {
        prefs.setHidden(accountId, section, ids)
        hidden = ids
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.zelviro_hide_section, sectionTitle(section))) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { save(hideCurrentCategories(hidden, categories.map { it.visibilityKey() })) },
                        enabled = categories.isNotEmpty()) { Text(stringResource(R.string.zelviro_hide_all)) }
                    TextButton(onClick = { save(emptySet()) }, enabled = hidden.isNotEmpty()) {
                        Text(stringResource(R.string.zelviro_show_all))
                    }
                }
                when (result) {
                    null -> CircularProgressIndicator()
                    is AppResult.Failure -> Text(stringResource(R.string.zelviro_categories_error))
                    else -> LazyColumn(Modifier.heightIn(max = 270.dp)) {
                        items(categories, key = { it.visibilityKey() }) { category ->
                            val key = category.visibilityKey()
                            Row(Modifier.fillMaxWidth().tvFocusable().toggleable(
                                value = key in hidden, role = Role.Checkbox,
                                onValueChange = { checked -> save(if (checked) hidden + key else hidden - key) },
                            ).padding(10.dp)) {
                                Checkbox(checked = key in hidden, onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(category.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.zelviro_close)) } },
    )
}

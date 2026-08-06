package nl.streamfix.ui.screens.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nl.streamfix.BuildConfig
import nl.streamfix.R
import nl.streamfix.data.local.STARTUP_TAB_FAVORITES
import nl.streamfix.data.local.STARTUP_TAB_HISTORY
import nl.streamfix.data.local.STARTUP_TAB_LAST
import nl.streamfix.data.local.STARTUP_TAB_LIVE
import nl.streamfix.domain.model.Account
import nl.streamfix.ui.dpadExitField
import nl.streamfix.ui.formatXtreamExpiry
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.screens.catchup.CatchupScreen
import nl.streamfix.ui.screens.favorites.FavoritesScreen
import nl.streamfix.ui.screens.history.HistoryScreen
import nl.streamfix.ui.screens.history.PlaybackTarget
import nl.streamfix.ui.screens.live.LiveTvScreen
import nl.streamfix.ui.screens.series.SeriesScreen
import nl.streamfix.ui.screens.vod.VodScreen

/** Auto-open "laatste zender" maximaal een keer per Activity-instantie. */
private object StartupAutoOpen {
    var handledActivity: Int? = null
}

private enum class Tab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LiveTv(R.string.tab_live_tv, Icons.Filled.LiveTv),
    Favorites(R.string.tab_favorites_short, Icons.Filled.Star),
    Movies(R.string.tab_movies, Icons.Filled.Movie),
    Series(R.string.tab_series_short, Icons.Filled.Tv),
    Catchup(R.string.tab_catchup, Icons.Filled.Replay),
    History(R.string.tab_history, Icons.Filled.History),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

private enum class SettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Account(R.string.settings_account, Icons.Filled.AccountCircle),
    Preferences(R.string.settings_section_preferences, Icons.Filled.Tune),
    Parental(R.string.settings_section_parental, Icons.Filled.Lock),
    App(R.string.settings_section_app, Icons.Filled.Info),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    onLoggedOut: () -> Unit,
    onAddProvider: () -> Unit,
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    onOpenChannelEpg: (channelId: String, channelName: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    onOpenVod: (vodId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    onResumeMedia: (target: PlaybackTarget) -> Unit,
    onOpenCatchupChannel: (
        channelId: String, channelName: String, days: Int,
    ) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNowOnTv: () -> Unit,
    onOpenConnectionTest: () -> Unit,
    deviceIsTv: Boolean,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adultState by viewModel.adultState.collectAsStateWithLifecycle()
    val tvMode by viewModel.tvMode.collectAsStateWithLifecycle()
    val startupTab by viewModel.startupTab.collectAsStateWithLifecycle()
    val isTv = when (tvMode) {
        "tv" -> true
        "phone" -> false
        else -> deviceIsTv
    }
    // Alleen de initiele waarde uitlezen; daarna respecteert rememberSaveable
    // de keuze van de gebruiker tijdens de sessie.
    val initialTabIndex = remember {
        val entries = Tab.entries
        when (viewModel.startupTab.value) {
            STARTUP_TAB_FAVORITES -> entries.indexOf(Tab.Favorites)
            STARTUP_TAB_HISTORY -> entries.indexOf(Tab.History)
            else -> 0
        }
    }
    var selected by rememberSaveable { mutableIntStateOf(initialTabIndex) }
    val context = LocalContext.current
    var backArmed by remember { mutableStateOf(false) }

    // "Start met laatste zender": eenmalig per Activity-instantie direct de
    // speler openen. De Activity is de juiste sleutel: dubbel-terug-afsluiten
    // beeindigt alleen de Activity (het proces leeft door, dus een procesvlag
    // slaat de volgende start ten onrechte over) en saved-state overleeft op
    // tv-boxen een systeem-kill (dus rememberSaveable sloeg die ook over).
    // Terugkeren uit de speler hergebruikt dezelfde Activity en vuurt nooit.
    val activityKey = System.identityHashCode(context)
    LaunchedEffect(Unit) {
        if (StartupAutoOpen.handledActivity != activityKey) {
            StartupAutoOpen.handledActivity = activityKey
            if (viewModel.startupTab.value == STARTUP_TAB_LAST) {
                viewModel.startupChannel()?.let { (cat, ch) ->
                    onOpenChannel(cat, ch)
                }
            }
        }
    }

    // Op tv verlaat je de app meestal met Home; dat is een hervatting, geen
    // koude start. Daarom ook openen bij terugkeer uit de achtergrond. We
    // luisteren op de Activity-lifecycle en NIET op de nav-entry: die stopt
    // ook bij navigeren naar de speler en zou bij elke terugkeer opnieuw
    // openen. Zat de kijker nog in de speler bij het weggaan, dan is dit
    // scherm niet gecomposed en herstelt Android de speler zelf.
    DisposableEffect(Unit) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        var leftApp = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> leftApp = true
                Lifecycle.Event.ON_START -> {
                    if (leftApp) {
                        leftApp = false
                        if (viewModel.startupTab.value == STARTUP_TAB_LAST) {
                            viewModel.startupChannel()?.let { (cat, ch) ->
                                onOpenChannel(cat, ch)
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    LaunchedEffect(backArmed) {
        if (backArmed) {
            delay(2000)
            backArmed = false
        }
    }

    // Terug-toets: niet meteen de app verlaten. Eerst terug naar Live TV,
    // en op het eerste tabblad pas afsluiten na een tweede druk.
    BackHandler {
        when {
            selected != 0 -> selected = 0
            backArmed -> (context as? Activity)?.finish()
            else -> {
                backArmed = true
                Toast.makeText(
                    context,
                    context.getString(R.string.main_back_to_exit_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val tabs = Tab.entries

    val tabContent: @Composable () -> Unit = {
        when (tabs[selected]) {
            Tab.LiveTv -> LiveTvScreen(
                onOpenChannel = onOpenChannel,
                onOpenChannelEpg = onOpenChannelEpg,
                onOpenGuide = onOpenGuide,
                providers = state.accounts.map { it.id to it.displayName },
                activeProviderId = state.account?.id,
                onSwitchProvider = viewModel::onSwitchProvider,
            )
            Tab.Favorites -> FavoritesScreen(onOpenChannel = onOpenChannel)
            Tab.Movies -> VodScreen(onOpenVod = onOpenVod)
            Tab.Series -> SeriesScreen(onOpenSeries = onOpenSeries)
            Tab.Catchup -> CatchupScreen(
                onOpenChannel = onOpenCatchupChannel,
            )
            Tab.History -> HistoryScreen(onResume = onResumeMedia)
            Tab.Settings -> SettingsContent(
                state = state,
                adult = adultState,
                tvMode = tvMode,
                startupTab = startupTab,
                onSwitchProvider = viewModel::onSwitchProvider,
                onRemoveProvider = viewModel::onRemoveProvider,
                onSetStreamFormat = viewModel::onSetStreamFormat,
                onSetAdultPin = viewModel::onSetAdultPin,
                onUnlockAdult = viewModel::onUnlockAdult,
                unlockWaitSeconds = viewModel::onUnlockWaitSeconds,
                onHideAdult = viewModel::onHideAdult,
                onSetTvMode = viewModel::onSetTvMode,
                onSetStartupTab = viewModel::onSetStartupTab,
                onAddProvider = onAddProvider,
                onOpenConnectionTest = onOpenConnectionTest,
                onLogout = viewModel::onLogout,
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlaceholderContent(stringResource(tabs[selected].labelRes))
            }
        }
    }

    CompositionLocalProvider(LocalIsTv provides isTv) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tabs[selected].labelRes)) },
                actions = {
                    // Bij meerdere abonnementen: altijd zichtbaar welk
                    // abonnement actief is, op elk tabblad.
                    if (state.accounts.size > 1) {
                        Text(
                            text = state.account?.displayName.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .padding(end = 8.dp),
                        )
                    }
                    IconButton(onClick = onOpenNowOnTv) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription =
                                stringResource(R.string.topbar_now_on_tv),
                        )
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription =
                                stringResource(R.string.topbar_search),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!isTv) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription =
                                        stringResource(tab.labelRes),
                                )
                            },
                            label = {
                                Text(stringResource(tab.labelRes), maxLines = 1)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (isTv) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                // Links-drukken vanuit de inhoud hoort terug te komen op het
                // ACTIEVE tabblad. Standaard kiest focus-search het rail-item
                // dat geometrisch het dichtstbij is (onderin Meer beland je
                // dan op Films/Gemist). De onEnter-redirect dwingt af dat
                // binnenkomen van de rail altijd op de actieve tab landt;
                // omhoog/omlaag binnen de rail blijft gewoon werken.
                val railFocus = remember { List(tabs.size) { FocusRequester() } }
                NavigationRail(
                    modifier = Modifier
                        .focusProperties {
                            enter = { railFocus[selected] }
                        }
                        .focusGroup(),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            modifier = Modifier.focusRequester(railFocus[index]),
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription =
                                        stringResource(tab.labelRes),
                                )
                            },
                            label = {
                                Text(stringResource(tab.labelRes), maxLines = 1)
                            },
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Overscan-veilige marge: tv's snijden de randen af.
                        .padding(end = 24.dp, top = 8.dp, bottom = 16.dp),
                ) { tabContent() }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { tabContent() }
        }
    }
    }
}

@Composable
private fun PlaceholderContent(name: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.tab_placeholder_coming_soon, name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsContent(
    state: MainState,
    adult: nl.streamfix.data.local.AdultState,
    tvMode: String,
    startupTab: String,
    onSwitchProvider: (String) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSetStreamFormat: (String) -> Unit,
    onSetAdultPin: (String) -> Unit,
    onUnlockAdult: (String) -> Boolean,
    unlockWaitSeconds: () -> Int,
    onHideAdult: () -> Unit,
    onSetTvMode: (String) -> Unit,
    onSetStartupTab: (String) -> Unit,
    onAddProvider: () -> Unit,
    onOpenConnectionTest: () -> Unit,
    onLogout: () -> Unit,
) {
    val isTv = LocalIsTv.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val sections = SettingsSection.entries
    val current = sections[selected]

    val content: @Composable ColumnScope.() -> Unit = {
        when (current) {
            SettingsSection.Account -> AccountSettings(
                state = state,
                isTv = isTv,
                onSwitchProvider = onSwitchProvider,
                onRemoveProvider = onRemoveProvider,
                onAddProvider = onAddProvider,
                onOpenConnectionTest = onOpenConnectionTest,
            )
            SettingsSection.Preferences -> PreferenceSettings(
                state = state,
                tvMode = tvMode,
                startupTab = startupTab,
                onSetStreamFormat = onSetStreamFormat,
                onSetTvMode = onSetTvMode,
                onSetStartupTab = onSetStartupTab,
            )
            SettingsSection.Parental -> SettingsGroup(
                stringResource(R.string.settings_adult_content),
            ) {
                AdultContentSection(
                    adult = adult,
                    onSetAdultPin = onSetAdultPin,
                    onUnlockAdult = onUnlockAdult,
                    unlockWaitSeconds = unlockWaitSeconds,
                    onHideAdult = onHideAdult,
                )
            }
            SettingsSection.App -> AppSettings(onLogout)
        }
    }

    if (isTv) {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsSectionMenu(
                sections = sections,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            key(current) {
                SettingsSectionPane(
                    titleRes = current.labelRes,
                    modifier = Modifier.weight(1f),
                    content = content,
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = selected,
                edgePadding = 12.dp,
            ) {
                sections.forEachIndexed { index, section ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(stringResource(section.labelRes)) },
                        icon = {
                            Icon(
                                section.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
            key(current) {
                SettingsSectionPane(
                    titleRes = current.labelRes,
                    modifier = Modifier.weight(1f),
                    content = content,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SettingsSectionMenu(
    sections: List<SettingsSection>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember {
        List(sections.size) { FocusRequester() }
    }
    Column(
        modifier = modifier
            .focusProperties { enter = { focusRequesters[selected] } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEachIndexed { index, section ->
            SettingsSectionButton(
                section = section,
                selected = selected == index,
                onClick = { onSelect(index) },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun SettingsSectionButton(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val color = when {
        focused -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(section.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionPane(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(18.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AccountSettings(
    state: MainState,
    isTv: Boolean,
    onSwitchProvider: (String) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onAddProvider: () -> Unit,
    onOpenConnectionTest: () -> Unit,
) {
    val context = LocalContext.current
    SettingsGroup(stringResource(R.string.settings_active_provider)) {
        Text(
            text = state.account?.displayName
                ?: stringResource(R.string.account_unknown),
            style = MaterialTheme.typography.titleMedium,
        )
        state.accountInfo?.let { info ->
            val parts = listOfNotNull(
                info.status?.let {
                    stringResource(R.string.account_status_prefix, it)
                },
                formatXtreamExpiry(context, info.expirationDate)?.let {
                    stringResource(R.string.account_expiry_prefix, it)
                },
                info.maxConnections?.let {
                    stringResource(R.string.account_max_prefix, it)
                },
            )
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = parts.joinToString("  -  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    HorizontalDivider()
    Spacer(Modifier.height(18.dp))
    SettingsGroup(stringResource(R.string.settings_providers)) {
        state.accounts.forEach { acc ->
            val isActive = acc.id == state.account?.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isActive) {
                        onSwitchProvider(acc.id)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isActive,
                    onClick = { if (!isActive) onSwitchProvider(acc.id) },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = acc.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemoveProvider(acc.id) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription =
                            stringResource(R.string.provider_remove_desc),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    if (isTv) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onAddProvider,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.welcome_add_provider)) }
            OutlinedButton(
                onClick = onOpenConnectionTest,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_connection_test)) }
        }
    } else {
        OutlinedButton(
            onClick = onAddProvider,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.welcome_add_provider)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenConnectionTest,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_connection_test)) }
    }
}

@Composable
private fun PreferenceSettings(
    state: MainState,
    tvMode: String,
    startupTab: String,
    onSetStreamFormat: (String) -> Unit,
    onSetTvMode: (String) -> Unit,
    onSetStartupTab: (String) -> Unit,
) {
    RadioSettingsGroup(
        title = stringResource(R.string.settings_startup_screen),
        selectedValue = startupTab,
        options = listOf(
            STARTUP_TAB_LIVE to stringResource(R.string.tab_live_tv),
            STARTUP_TAB_FAVORITES to stringResource(R.string.common_favorites),
            STARTUP_TAB_HISTORY to stringResource(R.string.startup_tab_history),
            STARTUP_TAB_LAST to stringResource(R.string.startup_tab_last),
        ),
        onSelect = onSetStartupTab,
    )
    SettingsDivider()
    RadioSettingsGroup(
        title = stringResource(R.string.settings_display),
        selectedValue = tvMode,
        options = listOf(
            "auto" to stringResource(R.string.tv_mode_auto),
            "tv" to stringResource(R.string.tv_mode_tv),
            "phone" to stringResource(R.string.tv_mode_phone),
        ),
        onSelect = onSetTvMode,
    )
    (state.account as? Account.Xtream)?.let { xt ->
        SettingsDivider()
        RadioSettingsGroup(
            title = stringResource(R.string.settings_stream_format),
            selectedValue = xt.streamFormat,
            options = listOf(
                "auto" to stringResource(R.string.stream_format_auto),
                "ts" to stringResource(R.string.stream_format_ts),
                "m3u8" to stringResource(R.string.stream_format_m3u8),
            ),
            onSelect = onSetStreamFormat,
        )
    }
}

@Composable
private fun RadioSettingsGroup(
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    SettingsGroup(title) {
        options.forEach { (value, label) ->
            val isSelected = selectedValue == value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSelected) { onSelect(value) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { if (!isSelected) onSelect(value) },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(18.dp))
    HorizontalDivider()
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun AppSettings(onLogout: () -> Unit) {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.logout))
    }
}

@Composable
private fun AdultContentSection(
    adult: nl.streamfix.data.local.AdultState,
    onSetAdultPin: (String) -> Unit,
    onUnlockAdult: (String) -> Boolean,
    unlockWaitSeconds: () -> Int,
    onHideAdult: () -> Unit,
) {
    var showSet by remember { mutableStateOf(false) }
    var showEnter by remember { mutableStateOf(false) }

    when {
        !adult.hasPin -> {
            Text(
                stringResource(R.string.adult_no_pin_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showSet = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.adult_set_pin)) }
        }
        adult.unlocked -> {
            Text(
                stringResource(R.string.adult_visible_session),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHideAdult,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.adult_hide_again)) }
            TextButton(onClick = { showSet = true }) {
                Text(stringResource(R.string.adult_change_pin))
            }
        }
        else -> {
            Text(
                stringResource(R.string.adult_hidden),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showEnter = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.adult_show_with_pin)) }
            // Bewust geen "Pincode wijzigen" hier: wijzigen kan pas na
            // ontgrendelen, anders omzeilt iemand het slot met een reset.
        }
    }

    if (showSet) {
        PinDialog(
            title = stringResource(R.string.adult_set_pin),
            confirmLabel = stringResource(R.string.pin_save),
            requireConfirm = true,
            onDismiss = { showSet = false },
            validate = { it.length >= 4 },
        ) { pin ->
            onSetAdultPin(pin)
            showSet = false
            true
        }
    }
    if (showEnter) {
        val context = LocalContext.current
        PinDialog(
            title = stringResource(R.string.pin_enter_title),
            confirmLabel = stringResource(R.string.pin_show),
            requireConfirm = false,
            onDismiss = { showEnter = false },
            validate = { it.isNotEmpty() },
            failMessage = {
                val wait = unlockWaitSeconds()
                if (wait > 0) {
                    context.getString(R.string.pin_error_locked, wait)
                } else {
                    context.getString(R.string.pin_error_incorrect)
                }
            },
        ) { pin ->
            val ok = onUnlockAdult(pin)
            if (ok) showEnter = false
            ok
        }
    }
}

/**
 * Pincode-dialoog. [onConfirm] geeft true terug bij succes (sluit dan), of
 * false (bv. onjuiste pincode) waarna de foutmelding zichtbaar wordt.
 */
@Composable
private fun PinDialog(
    title: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    validate: (String) -> Boolean,
    // Optionele dynamische foutmelding bij een afgewezen pincode
    // (bijv. cooldown-wachttijd); null = standaard "Onjuiste pincode."
    failMessage: (() -> String)? = null,
    onConfirm: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.pin_field_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = numeric,
                    modifier = Modifier.dpadExitField(focusManager),
                )
                if (requireConfirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = {
                            repeat = it.filter { c -> c.isDigit() }
                        },
                        label = {
                            Text(stringResource(R.string.pin_field_repeat))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = numeric,
                        modifier = Modifier.dpadExitField(focusManager),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val errTooShort = stringResource(R.string.pin_error_too_short)
            val errNotEqual = stringResource(R.string.pin_error_not_equal)
            val errIncorrect = stringResource(R.string.pin_error_incorrect)
            TextButton(onClick = {
                when {
                    !validate(pin) -> error = errTooShort
                    requireConfirm && pin != repeat -> error = errNotEqual
                    else -> if (!onConfirm(pin)) {
                        error = failMessage?.invoke() ?: errIncorrect
                    }
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

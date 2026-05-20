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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nl.streamfix.BuildConfig
import nl.streamfix.R
import nl.streamfix.domain.model.Account
import nl.streamfix.ui.formatXtreamExpiry
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.screens.catchup.CatchupScreen
import nl.streamfix.ui.screens.favorites.FavoritesScreen
import nl.streamfix.ui.screens.history.HistoryScreen
import nl.streamfix.ui.screens.live.LiveTvScreen
import nl.streamfix.ui.screens.series.SeriesScreen
import nl.streamfix.ui.screens.vod.VodScreen

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLoggedOut: () -> Unit,
    onAddProvider: () -> Unit,
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    onOpenChannelEpg: (channelId: String, channelName: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    onOpenVod: (vodId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    onResumeMedia: (streamUrl: String, title: String, mediaId: String) -> Unit,
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
    val isTv = when (tvMode) {
        "tv" -> true
        "phone" -> false
        else -> deviceIsTv
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var backArmed by remember { mutableStateOf(false) }

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
            )
            Tab.Favorites -> FavoritesScreen(onOpenChannel = onOpenChannel)
            Tab.Movies -> VodScreen(onOpenVod = onOpenVod)
            Tab.Series -> SeriesScreen(onOpenSeries = onOpenSeries)
            Tab.Catchup -> CatchupScreen(
                onOpenChannel = onOpenCatchupChannel,
            )
            Tab.History -> HistoryScreen(onResume = onResumeMedia)
            Tab.Settings -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingsContent(
                    state = state,
                    adult = adultState,
                    tvMode = tvMode,
                    onSwitchProvider = viewModel::onSwitchProvider,
                    onRemoveProvider = viewModel::onRemoveProvider,
                    onSetStreamFormat = viewModel::onSetStreamFormat,
                    onSetAdultPin = viewModel::onSetAdultPin,
                    onUnlockAdult = viewModel::onUnlockAdult,
                    onHideAdult = viewModel::onHideAdult,
                    onSetTvMode = viewModel::onSetTvMode,
                    onAddProvider = onAddProvider,
                    onOpenConnectionTest = onOpenConnectionTest,
                    onLogout = viewModel::onLogout,
                )
            }
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
                NavigationRail {
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
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
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SettingsContent(
    state: MainState,
    adult: nl.streamfix.data.local.AdultState,
    tvMode: String,
    onSwitchProvider: (String) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSetStreamFormat: (String) -> Unit,
    onSetAdultPin: (String) -> Unit,
    onUnlockAdult: (String) -> Boolean,
    onHideAdult: () -> Unit,
    onSetTvMode: (String) -> Unit,
    onAddProvider: () -> Unit,
    onOpenConnectionTest: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsCard(stringResource(R.string.settings_account)) {
            Text(
                text = state.account?.displayName
                    ?: stringResource(R.string.account_unknown),
                style = MaterialTheme.typography.bodyMedium,
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = parts.joinToString("  -  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_providers),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            state.accounts.forEach { acc ->
                val isActive = acc.id == state.account?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isActive) {
                            onSwitchProvider(acc.id)
                        }
                        .padding(vertical = 2.dp),
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

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddProvider,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.welcome_add_provider))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenConnectionTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_connection_test))
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsCard(stringResource(R.string.settings_display)) {
            listOf(
                "auto" to stringResource(R.string.tv_mode_auto),
                "tv" to stringResource(R.string.tv_mode_tv),
                "phone" to stringResource(R.string.tv_mode_phone),
            ).forEach { (value, label) ->
                val isSelected = tvMode == value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSelected) {
                            onSetTvMode(value)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { if (!isSelected) onSetTvMode(value) },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        (state.account as? Account.Xtream)?.let { xt ->
            Spacer(Modifier.height(12.dp))
            SettingsCard(stringResource(R.string.settings_stream_format)) {
                val formats = listOf(
                    "auto" to stringResource(R.string.stream_format_auto),
                    "ts" to stringResource(R.string.stream_format_ts),
                    "m3u8" to stringResource(R.string.stream_format_m3u8),
                )
                formats.forEach { (value, label) ->
                    val isSelected = xt.streamFormat == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSelected) {
                                onSetStreamFormat(value)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) onSetStreamFormat(value)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsCard(stringResource(R.string.settings_adult_content)) {
            AdultContentSection(
                adult = adult,
                onSetAdultPin = onSetAdultPin,
                onUnlockAdult = onUnlockAdult,
                onHideAdult = onHideAdult,
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.logout))
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdultContentSection(
    adult: nl.streamfix.data.local.AdultState,
    onSetAdultPin: (String) -> Unit,
    onUnlockAdult: (String) -> Boolean,
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
        PinDialog(
            title = stringResource(R.string.pin_enter_title),
            confirmLabel = stringResource(R.string.pin_show),
            requireConfirm = false,
            onDismiss = { showEnter = false },
            validate = { it.isNotEmpty() },
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
    onConfirm: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)

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
                        error = errIncorrect
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

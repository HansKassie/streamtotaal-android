package nl.streamfix.ui.screens.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nl.streamfix.BuildConfig
import nl.streamfix.domain.model.Account
import nl.streamfix.ui.formatXtreamExpiry
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.screens.catchup.CatchupScreen
import nl.streamfix.ui.screens.favorites.FavoritesScreen
import nl.streamfix.ui.screens.history.HistoryScreen
import nl.streamfix.ui.screens.live.LiveTvScreen
import nl.streamfix.ui.screens.series.SeriesScreen
import nl.streamfix.ui.screens.vod.VodScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    LiveTv("Live TV", Icons.Filled.LiveTv),
    Favorites("Fav", Icons.Filled.Star),
    Movies("Films", Icons.Filled.Movie),
    Series("Series", Icons.Filled.Tv),
    Catchup("Gemist", Icons.Filled.Replay),
    History("Verder", Icons.Filled.History),
    Settings("Meer", Icons.Filled.Settings),
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
                    "Nogmaals terug om af te sluiten",
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
                PlaceholderContent(tabs[selected].label)
            }
        }
    }

    CompositionLocalProvider(LocalIsTv provides isTv) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[selected].label) },
                actions = {
                    IconButton(onClick = onOpenNowOnTv) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = "Nu op tv",
                        )
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Zoeken",
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
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label, maxLines = 1) },
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
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label, maxLines = 1) },
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
            text = "$name komt in een volgende fase",
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsCard("Account") {
            Text(
                text = state.account?.displayName ?: "Onbekend",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.accountInfo?.let { info ->
                val parts = listOfNotNull(
                    info.status?.let { "Status: $it" },
                    formatXtreamExpiry(info.expirationDate)
                        ?.let { "Verloopt: $it" },
                    info.maxConnections?.let { "Max: $it" },
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
                "Providers",
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
                            contentDescription = "Provider verwijderen",
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
                Text("Provider toevoegen")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenConnectionTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Verbinding testen")
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsCard("Weergave") {
            listOf(
                "auto" to "Automatisch (aanbevolen)",
                "tv" to "Tv (afstandsbediening)",
                "phone" to "Telefoon of tablet",
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
            SettingsCard("Stream Format") {
                val formats = listOf(
                    "auto" to "Automatisch",
                    "ts" to "MPEGTS (.ts)",
                    "m3u8" to "HLS (.m3u8)",
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
        SettingsCard("Volwassen content") {
            AdultContentSection(
                adult = adult,
                onSetAdultPin = onSetAdultPin,
                onUnlockAdult = onUnlockAdult,
                onHideAdult = onHideAdult,
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Uitloggen")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "StreamTotaal ${BuildConfig.VERSION_NAME}",
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
                "Nog geen pincode. Volwassen content is verborgen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showSet = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pincode instellen") }
        }
        adult.unlocked -> {
            Text(
                "Zichtbaar (deze sessie)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHideAdult,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Weer verbergen") }
            TextButton(onClick = { showSet = true }) {
                Text("Pincode wijzigen")
            }
        }
        else -> {
            Text("Verborgen", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showEnter = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Tonen met pincode") }
            // Bewust geen "Pincode wijzigen" hier: wijzigen kan pas na
            // ontgrendelen, anders omzeilt iemand het slot met een reset.
        }
    }

    if (showSet) {
        PinDialog(
            title = "Pincode instellen",
            confirmLabel = "Opslaan",
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
            title = "Pincode invoeren",
            confirmLabel = "Tonen",
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
                    label = { Text("Pincode") },
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
                        label = { Text("Herhaal pincode") },
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
            TextButton(onClick = {
                when {
                    !validate(pin) ->
                        error = "Pincode is te kort (minimaal 4 cijfers)."
                    requireConfirm && pin != repeat ->
                        error = "De pincodes zijn niet gelijk."
                    else -> if (!onConfirm(pin)) {
                        error = "Onjuiste pincode."
                    }
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuleren") }
        },
    )
}

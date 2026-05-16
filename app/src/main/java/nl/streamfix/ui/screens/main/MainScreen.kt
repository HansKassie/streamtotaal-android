package nl.streamfix.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.streamfix.BuildConfig
import nl.streamfix.domain.model.Account
import nl.streamfix.ui.formatXtreamExpiry
import nl.streamfix.ui.screens.history.HistoryScreen
import nl.streamfix.ui.screens.live.LiveTvScreen
import nl.streamfix.ui.screens.series.SeriesScreen
import nl.streamfix.ui.screens.vod.VodScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    LiveTv("Live TV", Icons.Filled.LiveTv),
    Movies("Films", Icons.Filled.Movie),
    Series("Series", Icons.Filled.Tv),
    History("Verder", Icons.Filled.History),
    Settings("Instellingen", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLoggedOut: () -> Unit,
    onAddProvider: () -> Unit,
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    onOpenChannelEpg: (channelId: String, channelName: String) -> Unit,
    onOpenVod: (vodId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    onResumeMedia: (streamUrl: String, title: String, mediaId: String) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    val tabs = Tab.entries

    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs[selected].label) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tabs[selected]) {
                Tab.LiveTv -> LiveTvScreen(
                    onOpenChannel = onOpenChannel,
                    onOpenChannelEpg = onOpenChannelEpg,
                )
                Tab.Movies -> VodScreen(onOpenVod = onOpenVod)
                Tab.Series -> SeriesScreen(onOpenSeries = onOpenSeries)
                Tab.History -> HistoryScreen(onResume = onResumeMedia)
                Tab.Settings -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SettingsContent(
                        state = state,
                        onSwitchProvider = viewModel::onSwitchProvider,
                        onRemoveProvider = viewModel::onRemoveProvider,
                        onSetStreamFormat = viewModel::onSetStreamFormat,
                        onAddProvider = onAddProvider,
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
private fun SettingsContent(
    state: MainState,
    onSwitchProvider: (String) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSetStreamFormat: (String) -> Unit,
    onAddProvider: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Actieve provider", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.account?.displayName ?: "Onbekend",
            style = MaterialTheme.typography.bodyLarge,
        )

        state.accountInfo?.let { info ->
            Spacer(Modifier.height(8.dp))
            info.status?.let { Text("Status: $it", style = MaterialTheme.typography.bodyMedium) }
            formatXtreamExpiry(info.expirationDate)?.let {
                Text("Vervaldatum: $it", style = MaterialTheme.typography.bodyMedium)
            }
            info.maxConnections?.let {
                Text("Max verbindingen: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }

        (state.account as? Account.Xtream)?.let { xt ->
            Spacer(Modifier.height(28.dp))
            Text("Stream Format", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
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
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) onSetStreamFormat(value)
                        },
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Providers", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        state.accounts.forEach { acc ->
            val isActive = acc.id == state.account?.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isActive) { onSwitchProvider(acc.id) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isActive,
                    onClick = { if (!isActive) onSwitchProvider(acc.id) },
                )
                Text(
                    text = acc.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemoveProvider(acc.id) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Provider verwijderen",
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddProvider, modifier = Modifier.fillMaxWidth()) {
            Text("Provider toevoegen")
        }

        Spacer(Modifier.height(28.dp))
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

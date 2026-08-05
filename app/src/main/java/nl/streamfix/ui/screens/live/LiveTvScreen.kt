package nl.streamfix.ui.screens.live

import androidx.compose.foundation.clickable
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.dpadExitField
import nl.streamfix.ui.tvFocusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import nl.streamfix.R
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveChannel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// kotlin.OptIn: focusProperties.enter is een echte Compose-experimentele API
// (anders dan Media3's UnstableApi, dat om androidx.annotation.OptIn vraagt).
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveTvScreen(
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    onOpenChannelEpg: (channelId: String, channelName: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    // (id, weergavenaam) van alle opgeslagen providers; bij 2+ verschijnt
    // op tv een provider-switch naast de categorie-knop.
    providers: List<Pair<String, String>> = emptyList(),
    activeProviderId: String? = null,
    onSwitchProvider: (String) -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val epgMap by viewModel.epg.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val isTv = LocalIsTv.current
    val rowFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Doel wordt door de ViewModel bepaald (die overleeft het bezoek aan de
    // speler) en hier alleen vastgehouden om de FocusRequester aan de juiste
    // rij te hangen.
    var targetId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        isTv,
        state.selectedCategoryId,
        state.visibleChannels.isNotEmpty(),
    ) {
        if (!isTv || state.visibleChannels.isEmpty()) {
            return@LaunchedEffect
        }
        val target = viewModel.focusTargetId(state.visibleChannels)
        targetId = target
        val idx = state.visibleChannels
            .indexOfFirst { it.id == target }
            .coerceAtLeast(0)
        listState.scrollToItem(idx)
        // Een frame wachten zodat de recompositie de FocusRequester aan de
        // juiste rij heeft gekoppeld voordat we focus vragen.
        withFrameNanos {}
        runCatching { rowFocus.requestFocus() }
    }

    var focusedChannel by remember { mutableStateOf<LiveChannel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent false
                }
                val kc = e.nativeKeyEvent.keyCode
                if (kc == android.view.KeyEvent.KEYCODE_PROG_YELLOW ||
                    kc == android.view.KeyEvent.KEYCODE_GUIDE
                ) {
                    onOpenGuide(state.selectedCategoryId ?: FAVORITES_ID)
                    true
                } else {
                    false
                }
            },
    ) {
        if (!LocalIsTv.current) OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.live_search_channel)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() },
            ),
            modifier = Modifier
                .dpadExitField(focusManager)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        var menuOpen by remember { mutableStateOf(false) }
        val favoritesLabel = stringResource(R.string.common_favorites)
        val categoryPlaceholder = stringResource(R.string.live_category_placeholder)
        val selectedLabel = when (state.selectedCategoryId) {
            FAVORITES_ID -> favoritesLabel
            null -> categoryPlaceholder
            else -> state.categories.find { it.id == state.selectedCategoryId }
                ?.name ?: categoryPlaceholder
        }

        val categoryFocus = remember { FocusRequester() }
        val categoryButton: @Composable () -> Unit = {
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = if (isTv) {
                        Modifier.focusRequester(categoryFocus)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ) {
                    Text(
                        text = selectedLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isTv) {
                            Modifier.widthIn(max = 220.dp)
                        } else {
                            Modifier.weight(1f)
                        },
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_favorites)) },
                        onClick = {
                            menuOpen = false
                            viewModel.selectCategory(FAVORITES_ID)
                        },
                    )
                    state.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                menuOpen = false
                                viewModel.selectCategory(cat.id)
                            },
                        )
                    }
                }
            }
        }

        if (isTv) {
            Row(
                // Vanuit de zenderlijst omhoog hoort de focus op de categorie
                // te landen. Zonder deze regel kiest focus-search de knop die
                // geometrisch het dichtst bij het midden van de brede
                // kanaalrij ligt, en dat is de providerknop rechts.
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .focusProperties { enter = { categoryFocus } }
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                categoryButton()
                // Provider-switch: alleen zinvol met meerdere abonnementen.
                // De gids blijft bereikbaar via de gele/Gids-toets en via
                // het TV-gids-item in de zenderlijst-overlay van de speler.
                if (providers.size > 1) {
                    var provMenuOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        OutlinedButton(onClick = { provMenuOpen = true }) {
                            // Account-icoon: onderscheidt deze knop in een
                            // oogopslag van de categorie-knop ernaast.
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = providers
                                    .firstOrNull { it.first == activeProviderId }
                                    ?.second.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp),
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = provMenuOpen,
                            onDismissRequest = { provMenuOpen = false },
                        ) {
                            providers.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        provMenuOpen = false
                                        if (id != activeProviderId) {
                                            onSwitchProvider(id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                categoryButton()
            }
        }

        Spacer(Modifier.height(8.dp))

        val channelList: @Composable () -> Unit = {
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            state.visibleChannels.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.live_no_channels),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.visibleChannels, key = { it.id }) { channel ->
                    LaunchedEffect(channel.id) { viewModel.ensureEpg(channel.id) }
                    ChannelRow(
                        channel = channel,
                        programmes = epgMap[channel.id],
                        isFavorite = state.favoriteIds.contains(channel.id),
                        focusRequester =
                            if (channel.id == targetId) rowFocus else null,
                        onClick = {
                            onOpenChannel(
                                state.selectedCategoryId ?: FAVORITES_ID,
                                channel.id,
                            )
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(channel) },
                        onInfo = { onOpenChannelEpg(channel.id, channel.name) },
                        onFocused = {
                            focusedChannel = channel
                            viewModel.onChannelFocused(channel.id)
                        },
                    )
                }
            }
        }
        }

        if (isTv) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { channelList() }
                EpgInfoPanel(
                    channel = focusedChannel,
                    programmes = focusedChannel?.id?.let { epgMap[it] },
                    modifier = Modifier
                        .width(360.dp)
                        .padding(end = 24.dp, top = 4.dp, bottom = 16.dp),
                )
            }
        } else {
            channelList()
        }
    }
}

/**
 * Rechterpaneel op tv: programma-informatie van het kanaal met focus.
 * Leest mee uit dezelfde EPG-cache als de lijstrijen, dus geen extra
 * netwerkcalls en (anders dan het oude live-voorbeeldvenster) geen
 * extra stream-verbinding richting de provider tijdens het bladeren.
 */
@Composable
private fun EpgInfoPanel(
    channel: LiveChannel?,
    programmes: List<EpgProgramme>?,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(modifier = modifier) {
        if (channel == null) return@Column

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))

        when {
            programmes == null -> Text(
                text = stringResource(R.string.epg_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            programmes.isEmpty() -> Text(
                text = stringResource(R.string.epg_no_guide),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                val current = programmes.firstOrNull {
                    nowMs in it.startMs until it.endMs
                }
                val upcoming = programmes
                    .filter { it.startMs > nowMs }
                    .sortedBy { it.startMs }
                    .take(4)

                if (current != null) {
                    Text(
                        text = stringResource(R.string.common_now) +
                            "  -  " +
                            timeFmt.format(Date(current.startMs)) +
                            " - " + timeFmt.format(Date(current.endMs)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current.endMs > current.startMs) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                ((nowMs - current.startMs).toFloat() /
                                    (current.endMs - current.startMs))
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (current.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = current.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (current == null && upcoming.isEmpty()) {
                    Text(
                        text = stringResource(R.string.epg_no_guide),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.epg_status_upcoming),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    upcoming.forEach { p ->
                        Row {
                            Text(
                                text = timeFmt.format(Date(p.startMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = p.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    programmes: List<EpgProgramme>?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val now = System.currentTimeMillis()
    val current = programmes?.firstOrNull { now in it.startMs until it.endMs }
    val next = programmes?.firstOrNull { it.startMs > now }
    val progress = current?.let {
        ((now - it.startMs).toFloat() / (it.endMs - it.startMs))
            .coerceIn(0f, 1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(
                horizontal = 16.dp,
                vertical = if (LocalIsTv.current) 7.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged { if (it.isFocused) onFocused() }
                .tvFocusable()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp),
                        )
                    }
                }
                if (next != null) {
                    Text(
                        text = stringResource(
                            R.string.live_upcoming_prefix, next.title,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onInfo) {
            Icon(
                Icons.Filled.Info,
                contentDescription = stringResource(R.string.live_programme_guide_desc),
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star
                else Icons.Outlined.StarBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.live_remove_favorite_desc
                    else R.string.live_add_favorite_desc,
                ),
            )
        }
    }
}

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
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

@Composable
fun LiveTvScreen(
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    onOpenChannelEpg: (channelId: String, channelName: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val epgMap by viewModel.epg.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val isTv = LocalIsTv.current
    val rowFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var lastFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val targetId = state.visibleChannels.firstOrNull { it.id == lastFocusedId }?.id
        ?: state.visibleChannels.firstOrNull()?.id
    LaunchedEffect(
        isTv,
        state.selectedCategoryId,
        state.visibleChannels.isNotEmpty(),
    ) {
        if (!isTv || state.visibleChannels.isEmpty()) {
            return@LaunchedEffect
        }
        val idx = state.visibleChannels
            .indexOfFirst { it.id == targetId }
            .coerceAtLeast(0)
        listState.scrollToItem(idx)
        withFrameNanos {}
        runCatching { rowFocus.requestFocus() }
    }

    var focusedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedChannel?.id, isTv) {
        if (!isTv) {
            previewUrl = null
            return@LaunchedEffect
        }
        val ch = focusedChannel ?: return@LaunchedEffect
        delay(700)
        previewUrl = viewModel.streamUrlFor(ch.id)
    }

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

        val categoryButton: @Composable () -> Unit = {
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = if (isTv) Modifier else Modifier.fillMaxWidth(),
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
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                categoryButton()
                TextButton(
                    onClick = {
                        onOpenGuide(state.selectedCategoryId ?: FAVORITES_ID)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(stringResource(R.string.live_tv_guide_button)) }
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
                            lastFocusedId = channel.id
                        },
                    )
                }
            }
        }
        }

        if (isTv) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { channelList() }
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(end = 24.dp, top = 4.dp, bottom = 16.dp),
                ) {
                    LivePreviewPlayer(
                        streamUrl = previewUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                    focusedChannel?.let { fc ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = fc.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            channelList()
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

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.model.LiveChannel

@Composable
fun LiveTvScreen(
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    onOpenChannelEpg: (channelId: String, channelName: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val epgMap by viewModel.epg.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val isTv = LocalIsTv.current
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(
        isTv,
        state.selectedCategoryId,
        state.visibleChannels.isNotEmpty(),
    ) {
        if (!isTv || state.visibleChannels.isEmpty()) {
            return@LaunchedEffect
        }
        withFrameNanos {}
        runCatching { listFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!LocalIsTv.current) OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Zoek kanaal") },
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
        val selectedLabel = when (state.selectedCategoryId) {
            FAVORITES_ID -> "Favorieten"
            null -> "Categorie"
            else -> state.categories.find { it.id == state.selectedCategoryId }
                ?.name ?: "Categorie"
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selectedLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Favorieten") },
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

        Spacer(Modifier.height(8.dp))

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
                    text = "Geen kanalen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .focusRequester(listFocus)
                    .focusGroup(),
            ) {
                items(state.visibleChannels, key = { it.id }) { channel ->
                    LaunchedEffect(channel.id) { viewModel.ensureEpg(channel.id) }
                    ChannelRow(
                        channel = channel,
                        programmes = epgMap[channel.id],
                        isFavorite = state.favoriteIds.contains(channel.id),
                        onClick = {
                            onOpenChannel(
                                state.selectedCategoryId ?: FAVORITES_ID,
                                channel.id,
                            )
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(channel) },
                        onInfo = { onOpenChannelEpg(channel.id, channel.name) },
                    )
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
                vertical = if (LocalIsTv.current) 16.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
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
                    style = MaterialTheme.typography.bodyLarge,
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
                        text = "Straks: ${next.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Filled.Info, contentDescription = "Programmagids")
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star
                else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Verwijder favoriet"
                else "Maak favoriet",
            )
        }
    }
}

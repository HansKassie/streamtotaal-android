package nl.streamfix.ui.screens.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import nl.streamfix.R
import nl.streamfix.domain.model.LiveChannel
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.screens.live.FAVORITES_ID
import nl.streamfix.ui.tvFocusable

@Composable
fun FavoritesScreen(
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Zelfde herstel als in Live TV: terug uit de speler hoort op de zender
    // te landen waar je vandaan kwam, niet bovenaan de lijst.
    var targetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isTv, favorites.isNotEmpty()) {
        if (!isTv || favorites.isEmpty()) return@LaunchedEffect
        val target = viewModel.focusTargetId(favorites)
        targetId = target
        val idx = favorites.indexOfFirst { it.id == target }.coerceAtLeast(0)
        listState.scrollToItem(idx)
        withFrameNanos {}
        runCatching { listFocus.requestFocus() }
    }

    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.favorites_empty_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        items(favorites, key = { it.id }) { channel ->
            FavoriteRow(
                channel = channel,
                // De aanvrager hangt aan de doelrij zelf, niet aan de lijst:
                // anders bepaalt Compose welke rij focus krijgt en is dat
                // altijd de bovenste.
                focusRequester =
                    if (channel.id == targetId) listFocus else null,
                onClick = { onOpenChannel(FAVORITES_ID, channel.id) },
                onFocused = { viewModel.onChannelFocused(channel.id) },
                onRemove = { viewModel.removeFavorite(channel) },
            )
        }
    }
}

@Composable
private fun FavoriteRow(
    channel: LiveChannel,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onRemove: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusable()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = if (LocalIsTv.current) 16.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.live_remove_favorite_desc),
            )
        }
    }
}

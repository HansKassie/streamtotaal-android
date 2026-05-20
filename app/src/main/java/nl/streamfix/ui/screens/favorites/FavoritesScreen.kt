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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

    LaunchedEffect(isTv, favorites.isNotEmpty()) {
        if (!isTv || favorites.isEmpty()) return@LaunchedEffect
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
        modifier = Modifier.fillMaxSize()
            .focusRequester(listFocus)
            .focusGroup(),
    ) {
        items(favorites, key = { it.id }) { channel ->
            FavoriteRow(
                channel = channel,
                onClick = { onOpenChannel(FAVORITES_ID, channel.id) },
                onRemove = { viewModel.removeFavorite(channel) },
            )
        }
    }
}

@Composable
private fun FavoriteRow(
    channel: LiveChannel,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

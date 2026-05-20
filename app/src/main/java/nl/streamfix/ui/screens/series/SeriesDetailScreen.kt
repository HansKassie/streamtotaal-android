package nl.streamfix.ui.screens.series

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import nl.streamfix.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenEpisode: (seriesId: String, season: Int, episodeId: String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFav by viewModel.isFavorite.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.detail?.name
                            ?: stringResource(R.string.series_title_fallback),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isFav) R.string.favorite_remove_desc
                                    else R.string.favorite_add_desc,
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            else -> {
                val d = state.detail ?: return@Scaffold
                val season = d.seasons.find { it.number == state.selectedSeason }
                var menuOpen by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row {
                        AsyncImage(
                            model = d.posterUrl,
                            contentDescription = d.name,
                            modifier = Modifier.size(width = 120.dp, height = 180.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(d.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            d.year?.let {
                                Text(stringResource(R.string.series_meta_year_prefix, it))
                            }
                            d.genre?.let {
                                Text(stringResource(R.string.series_meta_genre_prefix, it))
                            }
                            d.rating?.let {
                                Text(stringResource(R.string.series_meta_rating_prefix, it))
                            }
                            d.director?.let {
                                Text(stringResource(R.string.series_meta_director_prefix, it))
                            }
                        }
                    }
                    d.plot?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(16.dp))
                    Box {
                        OutlinedButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.selectedSeason
                                    ?.let {
                                        stringResource(R.string.series_season_format, it)
                                    } ?: stringResource(R.string.series_season_placeholder),
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            d.seasons.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.series_season_format, s.number))
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.selectSeason(s.number)
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    season?.episodes?.forEach { ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenEpisode(
                                        viewModel.seriesId,
                                        ep.seasonNumber,
                                        ep.id,
                                    )
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.series_episode_format,
                                    ep.episodeNumber, ep.title,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

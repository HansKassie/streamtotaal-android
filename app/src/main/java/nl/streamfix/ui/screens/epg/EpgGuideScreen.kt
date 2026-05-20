package nl.streamfix.ui.screens.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import nl.streamfix.R
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.screens.live.FAVORITES_ID
import nl.streamfix.ui.tvFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL = 132.dp
private val ROW_HEIGHT = 50.dp
private val PX_PER_MIN = 3.dp
private const val WINDOW_HOURS = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgGuideScreen(
    onBack: () -> Unit,
    onOpenChannel: (categoryId: String, channelId: String) -> Unit,
    viewModel: EpgGuideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val epgMap by viewModel.epg.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val cat = viewModel.categoryId.ifBlank { FAVORITES_ID }

    val openNow = remember { System.currentTimeMillis() }
    var nowMs by remember { mutableStateOf(openNow) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val timeScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val half = 30L * 60_000L
    val windowStart = remember(openNow) { (openNow / half) * half - half }
    val windowEnd = remember(openNow) {
        windowStart + WINDOW_HOURS * 3_600_000L
    }
    val totalWidth = PX_PER_MIN * (WINDOW_HOURS * 60).toFloat()
    val hourFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    fun msToDp(ms: Long): Dp = PX_PER_MIN * ((ms - windowStart) / 60_000f)

    val firstFocus = remember { FocusRequester() }
    var focusedProg by remember { mutableStateOf<EpgProgramme?>(null) }
    var focusedChannelName by remember { mutableStateOf("") }
    val nowFocus = remember { FocusRequester() }
    var didNowFocus by remember { mutableStateOf(false) }
    LaunchedEffect(isTv, state.channels.isNotEmpty()) {
        if (!isTv || state.channels.isEmpty()) return@LaunchedEffect
        withFrameNanos {}
        runCatching { firstFocus.requestFocus() }
    }

    val firstChannelId = state.channels.firstOrNull()?.id
    LaunchedEffect(isTv, firstChannelId, epgMap[firstChannelId]) {
        if (!isTv || didNowFocus) return@LaunchedEffect
        val id = firstChannelId ?: return@LaunchedEffect
        val progs = epgMap[id] ?: return@LaunchedEffect
        withFrameNanos {}
        val hasNow = progs.any { nowMs >= it.startMs && nowMs < it.endMs }
        runCatching {
            if (hasNow) nowFocus.requestFocus()
            else firstFocus.requestFocus()
        }
        didNowFocus = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.epg_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch { timeScroll.animateScrollTo(0) }
                        },
                    ) { Text(stringResource(R.string.common_now)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.errorMessage != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.channels.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.epg_no_channels),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    val density = LocalDensity.current

                    Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                    Row {
                        Spacer(Modifier.width(CHANNEL_COL))
                        Box(
                            modifier = Modifier
                                .horizontalScroll(timeScroll)
                                .width(totalWidth)
                                .height(24.dp),
                        ) {
                            for (h in 0..WINDOW_HOURS) {
                                val tickMs = windowStart + h * 3_600_000L
                                Text(
                                    text = hourFmt.format(Date(tickMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                    modifier = Modifier
                                        .offset(x = msToDp(tickMs))
                                        .padding(start = 4.dp),
                                )
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.channels, key = { it.id }) { ch ->
                            LaunchedEffect(ch.id) { viewModel.ensureEpg(ch.id) }
                            val isFirst = ch.id == state.channels.first().id
                            Row(modifier = Modifier.height(ROW_HEIGHT)) {
                                Row(
                                    modifier = Modifier
                                        .width(CHANNEL_COL)
                                        .fillMaxHeight()
                                        .then(
                                            if (isFirst) {
                                                Modifier.focusRequester(
                                                    firstFocus,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .tvFocusable()
                                        .clickable { onOpenChannel(cat, ch.id) }
                                        .padding(8.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = ch.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography
                                            .bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .horizontalScroll(timeScroll)
                                        .width(totalWidth)
                                        .fillMaxHeight(),
                                ) {
                                    val progs = epgMap[ch.id]
                                    if (progs == null) {
                                        Text(
                                            text = stringResource(R.string.epg_loading),
                                            style = MaterialTheme.typography
                                                .bodySmall,
                                            color = MaterialTheme.colorScheme
                                                .onSurfaceVariant,
                                            modifier = Modifier
                                                .offset(x = 4.dp)
                                                .padding(top = 8.dp),
                                        )
                                    } else if (progs.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.epg_no_guide),
                                            style = MaterialTheme.typography
                                                .bodySmall,
                                            color = MaterialTheme.colorScheme
                                                .onSurfaceVariant,
                                            modifier = Modifier
                                                .offset(x = 4.dp)
                                                .padding(top = 8.dp),
                                        )
                                    } else {
                                        progs.filter {
                                            it.endMs > windowStart &&
                                                it.startMs < windowEnd
                                        }.forEach { p ->
                                            val s = p.startMs
                                                .coerceAtLeast(windowStart)
                                            val e = p.endMs
                                                .coerceAtMost(windowEnd)
                                            val w = (PX_PER_MIN *
                                                ((e - s) / 60_000f))
                                            val isNowP = nowMs >= p.startMs &&
                                                nowMs < p.endMs
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = msToDp(s))
                                                    .width(w)
                                                    .fillMaxHeight()
                                                    .padding(2.dp)
                                                    .then(
                                                        if (
                                                            isFirst && isNowP
                                                        ) {
                                                            Modifier
                                                                .focusRequester(
                                                                    nowFocus,
                                                                )
                                                        } else {
                                                            Modifier
                                                        },
                                                    )
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            focusedProg = p
                                                            focusedChannelName =
                                                                ch.name
                                                        }
                                                    }
                                                    .tvFocusable()
                                                    .clickable {
                                                        onOpenChannel(
                                                            cat, ch.id,
                                                        )
                                                    }
                                                    .background(
                                                        if (isNowP) {
                                                            MaterialTheme
                                                                .colorScheme
                                                                .primaryContainer
                                                        } else {
                                                            MaterialTheme
                                                                .colorScheme
                                                                .surfaceVariant
                                                        },
                                                    )
                                                    .padding(
                                                        horizontal = 6.dp,
                                                        vertical = 4.dp,
                                                    ),
                                            ) {
                                                Text(
                                                    text = p.title,
                                                    style = MaterialTheme
                                                        .typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow =
                                                        TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                    }
                    val nowX = CHANNEL_COL + msToDp(nowMs) -
                        with(density) { timeScroll.value.toDp() }
                    if (nowX >= CHANNEL_COL) {
                        Box(
                            modifier = Modifier
                                .offset(x = nowX)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    }
                    focusedProg?.let { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            val status = when {
                                nowMs >= p.startMs && nowMs < p.endMs ->
                                    stringResource(R.string.common_now)
                                p.startMs > nowMs ->
                                    stringResource(R.string.epg_status_upcoming)
                                else ->
                                    stringResource(R.string.epg_status_earlier)
                            }
                            val statusNowLabel = stringResource(R.string.common_now)
                            Text(
                                text = status + "  -  " +
                                    hourFmt.format(Date(p.startMs)) +
                                    " - " + hourFmt.format(Date(p.endMs)) +
                                    "  -  " + focusedChannelName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            )
                            Text(
                                text = p.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (status == statusNowLabel && p.endMs > p.startMs) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        ((nowMs - p.startMs).toFloat() /
                                            (p.endMs - p.startMs))
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (p.description.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = p.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant,
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
    }
}

package nl.streamfix.ui.screens.player

import android.app.Activity
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nl.streamfix.R
import nl.streamfix.domain.model.EpgProgramme
import nl.streamfix.domain.util.EpgNowNext
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.screens.live.FAVORITES_ID
import nl.streamfix.ui.tvFocusable
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val activity = context as? Activity
    var chromeVisible by remember { mutableStateOf(true) }
    var channelListOpen by remember { mutableStateOf(false) }
    // Staat open tussen de KeyDown en de KeyUp van een OK-druk die wij
    // hebben afgehandeld, zodat die KeyUp niet alsnog iets anders doet.
    var okConsumed by remember { mutableStateOf(false) }
    // Tweede laag binnen het overzicht: de groepkeuze.
    var groupPickerOpen by remember { mutableStateOf(false) }
    // "Nu en straks" van de spelende zender; leest alleen, krijgt bewust
    // geen focus, zodat de afstandsbediening normaal blijft werken.
    var infoBarOpen by remember { mutableStateOf(false) }
    // Hoogt op bij elke aanleiding om de balk te tonen, zodat de acht
    // seconden opnieuw beginnen terwijl hij al open staat.
    var infoTick by remember { mutableIntStateOf(0) }
    var wakeTick by remember { mutableIntStateOf(0) }
    val chromeFocus = remember { FocusRequester() }
    LaunchedEffect(wakeTick, isTv) {
        if (!isTv) return@LaunchedEffect
        chromeVisible = true
        delay(4000)
        chromeVisible = false
    }
    LaunchedEffect(isTv) { if (isTv) chromeFocus.requestFocus() }
    LaunchedEffect(channelListOpen) {
        if (isTv && !channelListOpen) runCatching { chromeFocus.requestFocus() }
        if (!channelListOpen) {
            // Sluit het overzicht, dan hoort het bij heropenen weer de
            // groep te laten zien die speelt, en niet de groep waar de
            // kijker toevallig in rondkeek.
            groupPickerOpen = false
            viewModel.resetBrowseToPlaying()
        }
    }
    // Acht seconden, ruimer dan de vier van de bedieningsbalk: twee
    // programmatitels lezen kost meer tijd dan een zendernaam. Opnieuw
    // openen begint de tijd van voren.
    LaunchedEffect(infoBarOpen, infoTick) {
        if (!infoBarOpen) return@LaunchedEffect
        delay(8000)
        infoBarOpen = false
    }
    // Elke zenderwissel laat de balk kort zien, zoals een settopbox doet:
    // na het zappen wil je weten wat je nu kijkt. Dit verving de losse
    // zendernaam linksboven, die dezelfde naam nog eens toonde maar zonder
    // het programma erbij.
    LaunchedEffect(state.currentChannelId) {
        if (!isTv || state.currentChannelId == null) return@LaunchedEffect
        infoBarOpen = true
        infoTick++
        // Even wachten met ophalen. Wie doorzapt vuurt anders voor elke
        // tussenliggende zender een gidsaanvraag af die hij nooit ziet;
        // deze effect wordt bij de volgende wissel afgebroken.
        delay(400)
        viewModel.loadEpgForCurrentChannel()
    }
    val scope = rememberCoroutineScope()

    val player = rememberStreamFixExoPlayer()
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    val errorTimer = remember(scope) { PlaybackErrorTimer(scope) }
    val errorPhase by errorTimer.phase.collectAsStateWithLifecycle()
    val showError = errorPhase != PlaybackErrorPhase.Hidden
    val persistentError = errorPhase == PlaybackErrorPhase.Persistent
    // Een foutmelding beslaat het hele beeld; de balk eronder zou alleen
    // maar in de weg zitten.
    LaunchedEffect(showError) { if (showError) infoBarOpen = false }
    val cast = rememberCastController(player)
    PauseLocalWhenBackgrounded(cast, isLive = true)
    KeepScreenOnWhilePlaying(cast)

    // Alles wat bij een storing hoort in een klap opruimen, zodat er geen
    // vertraagde melding meer binnenvalt nadat het beeld alweer speelt.
    fun clearErrorState() {
        errorTimer.reset()
        retryJob?.cancel()
        retryJob = null
        retryAttempt = 0
    }

    // Markeer dat we in de speler zitten zodat MainActivity PiP kan starten.
    DisposableEffect(Unit) {
        PlayerActive.inPlayer = true
        onDispose {
            PlayerActive.inPlayer = false
            cast.release()
            player.release()
        }
    }

    // Auto-retry met exponentiele backoff (briefing: herstel binnen ~10s).
    //
    // De melding komt bewust NIET meteen in beeld. Een netwerkdip, een
    // read-timeout of een bronwissel bij de provider is bij IPTV dagelijkse
    // kost en lost zichzelf meestal binnen een seconde op. Direct melden
    // dat de zender niet beschikbaar is, is dan onwaar en precies waarover
    // klanten belden. Tijdens de gratieperiode ziet de kijker alleen de
    // bufferspinner die de PlayerView al toont.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) clearErrorState()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Tijdens casten speelt de ontvanger; een fout van de
                // lokale speler zegt dan niets over wat de kijker ziet.
                if (!cast.isLocalActive) return

                val backoffMs =
                    (1000L shl retryAttempt.coerceAtMost(3)).coerceAtMost(8000L)
                retryAttempt++
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(backoffMs)
                    cast.retryLocal(isLive = true)
                }
                errorTimer.onError()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Gaat het casten over, dan hoort een openstaande lokale melding weg.
    LaunchedEffect(cast.isLocalActive) {
        if (!cast.isLocalActive) clearErrorState()
    }

    // Wissel de bron als het kanaal verandert (vorige/volgende).
    LaunchedEffect(state.streamUrl) {
        clearErrorState()
        val url = state.streamUrl ?: return@LaunchedEffect
        cast.load(
            url,
            state.title,
            0L,
            isLive = true,
            castUri = state.castStreamUrl ?: url,
        )
    }

    // Terug sluit eerst wat eroverheen ligt en pas daarna de speler. De
    // film- en seriespelers doen dit al; de live-speler was overgeslagen.
    BackHandler(enabled = channelListOpen || infoBarOpen || showError) {
        if (channelListOpen) {
            // Van binnen naar buiten afpellen: eerst de groepkeuze, dan het
            // overzicht, dan de informatiebalk, en pas daarna de speler.
            if (groupPickerOpen) groupPickerOpen = false else channelListOpen = false
        } else if (infoBarOpen) {
            infoBarOpen = false
        } else {
            errorTimer.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isTv) Modifier
                    .focusRequester(chromeFocus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) {
                            // De KeyUp van een door ons afgehandelde
                            // OK-druk mag niet doorlekken. Compose
                            // activeert een gefocust item namelijk op
                            // KeyUp, en Media3 zou zijn bedieningsbalk
                            // openen; anders doet een druk op OK twee
                            // dingen tegelijk.
                            if (okConsumed &&
                                isOkKeyCode(e.nativeKeyEvent.keyCode)
                            ) {
                                okConsumed = false
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        if (channelListOpen) {
                            if (e.key == Key.Back) {
                                if (groupPickerOpen) {
                                    groupPickerOpen = false
                                } else {
                                    channelListOpen = false
                                }
                                return@onPreviewKeyEvent true
                            }
                            // Een volgende druk terwijl de lijst al open
                            // staat: de druk die hem opende is afgehandeld,
                            // dus de poort mag dicht. Blijft die anders
                            // open doordat een KeyUp ons niet bereikte,
                            // dan was er daarna geen zender meer te kiezen.
                            okConsumed = false
                            return@onPreviewKeyEvent false
                        }
                        // Ligt er een melding overheen, dan horen de toetsen
                        // bij die knop en niet bij zappen. Zonder dit slikt
                        // de speler OK op en is de knop onbedienbaar.
                        if (showError || state.channelListEmpty ||
                            state.channelLoadFailed
                        ) {
                            return@onPreviewKeyEvent false
                        }
                        if (e.key == Key.Back) return@onPreviewKeyEvent false
                        val ekc = e.nativeKeyEvent.keyCode
                        if (ekc ==
                            android.view.KeyEvent.KEYCODE_PROG_YELLOW ||
                            ekc == android.view.KeyEvent.KEYCODE_GUIDE
                        ) {
                            onOpenGuide(viewModel.guideCategoryId)
                            return@onPreviewKeyEvent true
                        }
                        if (ekc ==
                            android.view.KeyEvent.KEYCODE_LAST_CHANNEL
                        ) {
                            viewModel.lastChannel()
                            return@onPreviewKeyEvent true
                        }
                        // OK opent de "Nu en straks"-balk. Zo blijft de
                        // uitzending zichtbaar, en op een settopbox is OK
                        // de infotoets. Het zenderoverzicht blijft op
                        // omhoog en omlaag staan, de volledige gids op de
                        // gele toets en GUIDE.
                        //
                        // Dit pad wordt alleen bereikt tijdens gewoon
                        // kijken: ligt het overzicht, de groepkeuze of een
                        // foutmelding eroverheen, dan is hierboven al
                        // teruggegeven zodat OK de gefocuste knop bedient.
                        if (isOkKeyCode(ekc)) {
                            // Een ingedrukt gehouden OK stuurt herhalingen;
                            // zonder deze grens sluit de balk zich meteen
                            // weer via de tweede druk.
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                if (infoBarOpen) {
                                    infoBarOpen = false
                                } else {
                                    infoBarOpen = true
                                    infoTick++
                                    viewModel.loadEpgForCurrentChannel()
                                }
                            }
                            okConsumed = true
                            return@onPreviewKeyEvent true
                        }
                        // Zappen en het overzicht sluiten de balk: anders
                        // blijven de gegevens van de vorige zender staan.
                        when (e.key) {
                            Key.DirectionRight -> {
                                if (state.hasNext) viewModel.next()
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionLeft -> {
                                if (state.hasPrevious) viewModel.previous()
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionUp, Key.DirectionDown -> {
                                infoBarOpen = false
                                channelListOpen = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        val wasHidden = !chromeVisible
                        wakeTick++
                        wasHidden
                    }
                else Modifier,
            ),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            leftSide = offset.x < size.width / 2f
                        },
                    ) { _, dragAmount ->
                        // Omhoog (negatief) = meer.
                        val step = -dragAmount / size.height
                        if (leftSide) adjustBrightness(activity, step)
                        else adjustVolume(context, step)
                    }
                }
                .pointerInput(Unit) {
                    var totalDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onDragEnd = {
                            val threshold = size.width / 4f
                            if (totalDx <= -threshold) viewModel.next()
                            else if (totalDx >= threshold) viewModel.previous()
                        },
                    ) { _, dragAmount -> totalDx += dragAmount }
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    if (isTv) {
                        setUseController(false)
                        isFocusable = false
                    } else {
                        isFocusable = true
                        // Titel en terugknop meebewegen met de bediening
                        // van Media3, zodat ze samen verschijnen en samen
                        // weer weggaan.
                        followControllerVisibility { chromeVisible = it }
                        post { requestFocus() }
                    }
                }
            },
            update = { if (it.player !== cast.current) it.player = cast.current },
        )


        // Ook op telefoon aan chromeVisible: die volgt daar de bediening van
        // Media3. Begint op true, dus mocht die luisteraar niet afgaan, dan
        // blijft de balk staan en blijft de terugknop bereikbaar.
        if (chromeVisible) Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White,
                )
            }
            Text(
                text = state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // Verberg de cast-knop als de provider geen HLS levert
            // (castStreamUrl is dan null); rauwe .ts kan de ontvanger niet.
            if (cast.castAvailable && state.castStreamUrl != null) {
                CastButton()
            }
        }

        if (showError) {
            // Twee trappen. Eerst de eerlijke boodschap dat het herstel nog
            // loopt. Pas als de storing aanhoudt de groeptip: dezelfde zender
            // zit vaak in meerdere groepen en werkt daar wel. Die tip trekt
            // een conclusie over de zender, en die is bij een hapering onjuist.
            PlayerErrorOverlay(
                messageRes = if (persistentError) {
                    R.string.player_channel_group_problem
                } else {
                    R.string.player_connection_problem
                },
                hintRes = R.string.player_channel_group_hint
                    .takeIf { persistentError },
                onRetry = {
                    clearErrorState()
                    cast.retryLocal(isLive = true)
                },
            )
        }

        if (state.channelLoadFailed) {
            PlayerErrorOverlay(
                messageRes = R.string.player_channel_load_failed,
                onRetry = viewModel::retryLoad,
            )
        }

        if (state.channelListEmpty) {
            // Herstelbaar: een leeg antwoord bewijst niet dat de zender weg
            // is. Daarom dezelfde overlay met een knop, niet een doodlopende
            // mededeling.
            PlayerErrorOverlay(
                messageRes = R.string.player_channel_list_empty,
                hintRes = R.string.player_channel_list_empty_hint,
                onRetry = viewModel::retryLoad,
            )
        }

        if (!isTv && chromeVisible) Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = viewModel::previous, enabled = state.hasPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.player_prev_channel_desc),
                    tint = Color.White,
                )
            }
            IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.player_next_channel_desc),
                    tint = Color.White,
                )
            }
        }

        if (isTv && infoBarOpen && !channelListOpen) {
            NowNextBar(
                channelId = state.currentChannelId,
                channelTitle = state.title,
                programmes = state.epgProgrammes,
                loading = state.epgLoading,
                failed = state.epgFailed,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            )
        }

        if (isTv && channelListOpen) {
            val listState = rememberLazyListState()
            val currentFocus = remember { FocusRequester() }
            val headerFocus = remember { FocusRequester() }
            val groupFocus = remember { FocusRequester() }
            val favoritesLabel = stringResource(R.string.common_favorites)
            val groupPlaceholder =
                stringResource(R.string.live_category_placeholder)
            val groupLabel = when (state.browseCategoryId) {
                FAVORITES_ID -> favoritesLabel
                "" -> groupPlaceholder
                else -> state.categories
                    .find { it.id == state.browseCategoryId }?.name
                    ?: groupPlaceholder
            }
            val groupEntries = remember(state.categories, favoritesLabel) {
                listOf(FAVORITES_ID to favoritesLabel) +
                    state.categories.map { it.id to it.name }
            }
            // Zelfde terugval als bij de zenders: staat de bekeken groep
            // niet in de lijst (vanuit zoeken is er geen groep), dan moet
            // de focus naar de eerste regel en niet nergens heen.
            val groupFocusId = groupEntries
                .firstOrNull { it.first == state.browseCategoryId }?.first
                ?: groupEntries.firstOrNull()?.first
            // Kop-items boven de zenders. De groepkeuze valt weg als de
            // groepen niet opgehaald konden worden, dus meetellen en niet
            // een vast getal gebruiken.
            val headerCount = (if (state.categories.isEmpty()) 0 else 1) +
                1 + (if (state.hasLast) 1 else 0)
            // Waar de focus heen gaat: de spelende zender als die in deze
            // lijst staat, anders de eerste. Zonder die terugval raakt de
            // focus kwijt zodra je een andere groep opent (daar staat niets
            // te spelen) en doet de afstandsbediening niets meer.
            val focusRowId = state.currentChannelId
                ?.takeIf { id -> state.channels.any { it.id == id } }
                ?: state.channels.firstOrNull()?.id
            LaunchedEffect(
                groupPickerOpen,
                state.browseCategoryId,
                focusRowId,
            ) {
                if (groupPickerOpen) {
                    withFrameNanos {}
                    runCatching { groupFocus.requestFocus() }
                    return@LaunchedEffect
                }
                if (focusRowId == null) {
                    // Laden, mislukt of leeg: focus op de kop, zodat de
                    // afstandsbediening blijft werken en de groepkeuze
                    // bereikbaar is.
                    withFrameNanos {}
                    runCatching { headerFocus.requestFocus() }
                    return@LaunchedEffect
                }
                val idx = state.channels
                    .indexOfFirst { it.id == focusRowId }
                    .takeIf { it >= 0 } ?: 0
                listState.scrollToItem(idx + headerCount)
                withFrameNanos {}
                runCatching { currentFocus.requestFocus() }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color.Black.copy(alpha = 0.85f)),
            ) {
                if (groupPickerOpen) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    ) {
                        items(groupEntries, key = { it.first }) { entry ->
                            val isCurrent = entry.first == state.browseCategoryId
                            Text(
                                text = entry.second,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (entry.first == groupFocusId) {
                                            Modifier.focusRequester(groupFocus)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .tvFocusable()
                                    .clickable {
                                        groupPickerOpen = false
                                        viewModel.browseCategory(entry.first)
                                    }
                                    .background(
                                        if (isCurrent) {
                                            MaterialTheme.colorScheme
                                                .primaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp,
                                    ),
                            )
                        }
                    }
                } else LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    if (state.categories.isNotEmpty()) {
                        item(key = "__groups__") {
                            Text(
                                text = stringResource(
                                    R.string.player_overlay_groups,
                                    groupLabel,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(headerFocus)
                                    .tvFocusable()
                                    .clickable { groupPickerOpen = true }
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp,
                                    ),
                            )
                        }
                    }
                    item(key = "__guide__") {
                        Text(
                            text = stringResource(R.string.player_overlay_tv_guide),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusable()
                                .clickable {
                                    channelListOpen = false
                                    onOpenGuide(viewModel.guideCategoryId)
                                }
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp,
                                ),
                        )
                    }
                    if (state.hasLast) {
                        item(key = "__last__") {
                            Text(
                                text = stringResource(R.string.player_overlay_last_channel),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusable()
                                    .clickable {
                                        viewModel.lastChannel()
                                        channelListOpen = false
                                    }
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp,
                                    ),
                            )
                        }
                    }
                    if (state.browseLoading || state.browseFailed) {
                        item(key = "__browse_status__") {
                            Text(
                                text = stringResource(
                                    if (state.browseFailed) {
                                        R.string.player_overlay_group_failed
                                    } else {
                                        R.string.player_overlay_group_loading
                                    },
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp,
                                ),
                            )
                        }
                    }
                    items(state.channels, key = { it.id }) { ch ->
                        val isCurrent = ch.id == state.currentChannelId
                        Text(
                            text = ch.name,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (ch.id == focusRowId) {
                                        Modifier.focusRequester(currentFocus)
                                    } else {
                                        Modifier
                                    },
                                )
                                .tvFocusable()
                                .clickable {
                                    viewModel.playFromOverlay(ch.id)
                                    channelListOpen = false
                                }
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme
                                            .primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun adjustBrightness(activity: Activity?, step: Float) {
    activity ?: return
    val lp = activity.window.attributes
    val current = if (lp.screenBrightness in 0f..1f) lp.screenBrightness else 0.5f
    lp.screenBrightness = (current + step).coerceIn(0.05f, 1f)
    activity.window.attributes = lp
}

private fun adjustVolume(context: android.content.Context, step: Float) {
    val am = context.getSystemService(android.content.Context.AUDIO_SERVICE)
        as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val target = (cur + (step * max).toInt())
    if (abs(step) > 0.01f) {
        am.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            target.coerceIn(0, max),
            0,
        )
    }
}

/**
 * "Nu en straks" van de spelende zender, onderin over het beeld.
 *
 * Krijgt bewust geen focus en geen knoppen: lezen kan zonder, en elk
 * focusbaar element in de speler moet daarna met de d-pad bereikbaar
 * blijven. Laden, geen gids en mislukt zijn drie verschillende meldingen,
 * want "geen gids" terwijl het ophalen gewoon faalde is onjuist en zet een
 * kijker op het verkeerde been.
 */
@Composable
private fun NowNextBar(
    channelId: String?,
    channelTitle: String,
    programmes: List<EpgProgramme>?,
    loading: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    // Per zender vastgezet. Een tikkende klok zou alleen onrust in de
    // voortgangsbalk geven, maar doorzappen houdt de balk wel open, en dan
    // moet de tijd wel bij de nieuwe zender horen.
    val nowMs = remember(channelId) { System.currentTimeMillis() }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val nowNext = remember(programmes, nowMs) {
        EpgNowNext.of(programmes.orEmpty(), nowMs)
    }

    Column(modifier = modifier) {
        Text(
            text = channelTitle,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))

        val statusRes = when {
            failed -> R.string.epg_load_failed
            // null betekent nog niet binnen. Dat is tijdens het zappen het
            // normale geval, en dan is "Geen gids" tonen onjuist: er is nog
            // niets gevraagd.
            loading || programmes == null -> R.string.epg_loading
            nowNext.now == null && nowNext.next == null -> R.string.epg_no_guide
            else -> null
        }
        if (statusRes != null) {
            Text(
                text = stringResource(statusRes),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        nowNext.now?.let { current ->
            Text(
                text = stringResource(R.string.common_now) + "  " +
                    timeFmt.format(Date(current.startMs)) + " - " +
                    timeFmt.format(Date(current.endMs)) + "   " +
                    current.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current.endMs > current.startMs) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        ((nowMs - current.startMs).toFloat() /
                            (current.endMs - current.startMs)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        nowNext.next?.let { upcoming ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.epg_status_upcoming) + "  " +
                    timeFmt.format(Date(upcoming.startMs)) + "   " +
                    upcoming.title,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Gedeelde vlag zodat MainActivity weet wanneer PiP zinvol is. */
object PlayerActive {
    @Volatile
    var inPlayer: Boolean = false

    /**
     * Centrale tv-toetsafhandeling voor films en series. Null betekent dat
     * de normale Activity- en Compose-afhandeling van kracht blijft.
     */
    @Volatile
    var onTvKeyEvent: ((android.view.KeyEvent) -> Boolean)? = null
}

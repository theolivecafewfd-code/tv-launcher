package dev.wiptv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.Glass
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.tv.material3.Glow
import coil.request.ImageRequest
import dev.wiptv.design.focusOutline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * A film's or a series' details page, over the catalogue. Films: Play (or Resume and Start over),
 * Trailer and My List. Series: the same buttons for where you're up to, then the seasons and
 * their episodes; OK on an episode plays it, and the next one follows on when it ends.
 */
@Composable
fun VodDetailScreen(
    item: VodItem,
    login: Login,
    store: VodStore,
    onPlay: (ResumePoint, (() -> ResumePoint?)?) -> Unit,
    onListChanged: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var movie by remember { mutableStateOf<MovieDetails?>(null) }
    var series by remember { mutableStateOf<SeriesDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var inList by remember { mutableStateOf(store.favourites().any { it.key == item.key }) }
    var season by remember { mutableIntStateOf(-1) }
    // Bumped when playback ends, so Resume buttons and progress bars refresh.
    var refresh by remember { mutableIntStateOf(0) }
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(item.key) {
        try {
            if (item.kind == VodKind.MOVIE) movie = Xtream.movieDetails(login, item)
            else series = Xtream.seriesDetails(login, item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load the details."
        }
    }

    // Focus starts on the main action. My List holds it while the details load, then it moves to
    // Play or Resume once they arrive (before you'd have had time to move it yourself).
    LaunchedEffect(movie != null || series != null) {
        repeat(40) {
            if (runCatching { firstButton.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    // Where you're up to: the film's own position, or the latest episode you started.
    val resume = remember(refresh, item.key) {
        if (item.kind == VodKind.MOVIE) store.resumePoint("movie:${item.id}")
        else store.resumePoints().firstOrNull { it.seriesId == item.id }
    }
    val watched = remember(refresh) { store.watched() }

    fun moviePoint(details: MovieDetails, from: Long) = ResumePoint(
        key = "movie:${item.id}",
        title = item.name,
        subtitle = null,
        poster = item.poster,
        url = Xtream.movieUrl(login, item.id, details.extension),
        position = from,
        duration = resume?.duration ?: 0,
        updated = System.currentTimeMillis(),
        image = details.backdrop ?: item.backdrop,
    )

    fun episodePoint(episode: Episode, from: Long) = ResumePoint(
        key = "episode:${episode.id}",
        title = item.name,
        subtitle = "S${episode.season} E${episode.number} · ${EpisodeTitles.name(episode, item.name)}",
        poster = item.poster,
        url = Xtream.episodeUrl(login, episode.id, episode.extension),
        position = from,
        duration = store.resumePoint("episode:${episode.id}")?.duration ?: 0,
        updated = System.currentTimeMillis(),
        seriesId = item.id,
        image = episode.image ?: series?.backdrop ?: item.backdrop,
    )

    /** Plays an episode; when it ends, the next one (or the next season's first) follows. */
    fun playEpisode(details: SeriesDetails, episode: Episode, from: Long) {
        val all = details.seasons.flatMap { details.episodes[it].orEmpty() }
        onPlay(episodePoint(episode, from), chain(all, episode, ::episodePoint) { refresh++ })
    }

    val m = movie
    val s = series
    val loaded = m != null || s != null || error != null
    val plot = (m?.plot ?: s?.plot)?.ifBlank { null } ?: item.plot
    val meta = when {
        m != null -> listOfNotNull(
            m.year.ifBlank { null } ?: item.year,
            m.durationMinutes?.let(::runtimeOf),
            m.genre.ifBlank { null },
            scoreOf(m.rating),
        )
        s != null -> listOfNotNull(
            s.year.ifBlank { null } ?: item.year,
            if (s.seasons.isEmpty()) null else "${s.seasons.size} season" + if (s.seasons.size == 1) "" else "s",
            s.genre.ifBlank { null },
            scoreOf(s.rating),
        )
        else -> listOfNotNull(item.year, scoreOf(item.rating))
    }
    // The year shows in the details line, so a matching "(1987)" comes off the name.
    val title = DisplayTitles.withoutYear(item.name, m?.year?.ifBlank { null } ?: s?.year?.ifBlank { null } ?: item.year)
    val cast = (m?.cast ?: s?.cast)?.ifBlank { null }
    val director = m?.director?.ifBlank { null }
    var plotCut by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val moreInfoButton = remember { FocusRequester() }
    var refocusMoreInfo by remember { mutableStateOf(false) }
    // Closing More info puts focus back on its button.
    LaunchedEffect(refocusMoreInfo) {
        if (!refocusMoreInfo) return@LaunchedEffect
        repeat(20) {
            if (runCatching { moreInfoButton.requestFocus() }.isSuccess) {
                refocusMoreInfo = false
                return@LaunchedEffect
            }
            delay(50)
        }
        refocusMoreInfo = false
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(WiptvColors.background)) {
            // Full-screen artwork, faded on the left and at the bottom. Waits for the details so it
            // doesn't show the poster wash and then swap straight to the backdrop.
            CinematicBackdrop(
                backdrop = m?.backdrop ?: s?.backdrop ?: item.backdrop,
                poster = if (loaded) item.poster else null,
                modifier = Modifier.fillMaxSize(),
                delayMillis = 0,
                leftFade = 0.98f,
                textScrim = 0.58f,
            )

            // Rows already on screen stay put; one coming on from below settles near the top, so
            // the header scrolls away and about five episodes show. Up on the buttons brings it back.
            PivotScrolling(pivot = 32.dp) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Back on the buttons: show the whole header again.
                                .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } }
                                .padding(start = 48.dp, end = 48.dp, top = 44.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            // Poster, sharp, with a faint edge.
                            Box(
                                modifier = Modifier
                                    .size(width = 150.dp, height = 225.dp)
                                    .clip(WiptvShapes.card)
                                    .background(Brush.verticalGradient(listOf(WiptvColors.elevated, WiptvColors.surface)))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), WiptvShapes.card),
                            ) {
                                Text(
                                    text = item.name,
                                    maxLines = 4,
                                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                                    style = WiptvType.metadata,
                                )
                                if (item.poster != null) {
                                    AsyncImage(
                                        model = item.poster,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = WiptvType.hero.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
                                )
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta.joinToString("  ·  "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 8.dp),
                                        style = WiptvType.metadata.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                    )
                                }
                                when {
                                    error != null -> Text(
                                        text = error!!,
                                        modifier = Modifier.padding(top = 14.dp),
                                        style = WiptvType.body.copy(color = WiptvColors.warning),
                                    )
                                    !loaded -> Text(
                                        text = "Loading…",
                                        modifier = Modifier.padding(top = 14.dp),
                                        style = WiptvType.body.copy(color = WiptvColors.quiet),
                                    )
                                    !plot.isNullOrBlank() -> Text(
                                        text = plot,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { plotCut = it.hasVisualOverflow },
                                        modifier = Modifier.padding(top = 14.dp).widthIn(max = 540.dp),
                                        style = WiptvType.body.copy(lineHeight = 23.sp, color = WiptvColors.text.copy(alpha = 0.9f)),
                                    )
                                }

                                // A film you've started: how far through, just above Resume.
                                if (m != null && resume != null && resume.progress > 0f) {
                                    Row(
                                        modifier = Modifier.padding(top = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        ProgressLine(progress = resume.progress, modifier = Modifier.width(200.dp))
                                        timeLeft(resume)?.let { Text(text = it, style = WiptvType.metadata) }
                                    }
                                }

                                Row(
                                    modifier = Modifier.padding(top = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    when {
                                        m != null && resume != null -> {
                                            ActionButton(
                                                text = "▶  Resume",
                                                primary = true,
                                                onClick = { onPlay(moviePoint(m, resume.position), null) },
                                                modifier = Modifier.focusRequester(firstButton),
                                            )
                                            ActionButton(text = "Start over", onClick = { onPlay(moviePoint(m, 0), null) })
                                        }
                                        m != null -> ActionButton(
                                            text = "▶  Play",
                                            primary = true,
                                            onClick = { onPlay(moviePoint(m, 0), null) },
                                            modifier = Modifier.focusRequester(firstButton),
                                        )
                                        s != null && s.seasons.isNotEmpty() -> {
                                            val all = s.seasons.flatMap { s.episodes[it].orEmpty() }
                                            val resumeEpisode = resume?.let { r -> all.firstOrNull { "episode:${it.id}" == r.key } }
                                            if (resumeEpisode != null && resume != null) {
                                                ActionButton(
                                                    text = "▶  Resume S${resumeEpisode.season} E${resumeEpisode.number}",
                                                    primary = true,
                                                    onClick = { playEpisode(s, resumeEpisode, resume.position) },
                                                    modifier = Modifier.focusRequester(firstButton),
                                                )
                                            } else {
                                                // Next unwatched episode, or the very first.
                                                val next = all.firstOrNull { "episode:${it.id}" !in watched } ?: all.first()
                                                ActionButton(
                                                    text = "▶  Play S${next.season} E${next.number}",
                                                    primary = true,
                                                    onClick = { playEpisode(s, next, 0) },
                                                    modifier = Modifier.focusRequester(firstButton),
                                                )
                                            }
                                        }
                                    }
                                    ActionButton(
                                        text = if (inList) "✓  My List" else "+  My List",
                                        onClick = {
                                            inList = store.toggleFavourite(item)
                                            onListChanged()
                                        },
                                        modifier = if (m == null && (s == null || s.seasons.isEmpty())) Modifier.focusRequester(firstButton) else Modifier,
                                    )
                                    val trailer = m?.trailer ?: s?.trailer
                                    if (trailer != null) {
                                        ActionButton(text = "Trailer", onClick = { openTrailer(context, trailer) })
                                    }
                                    if (plotCut || cast != null || director != null) {
                                        ActionButton(
                                            text = "More info",
                                            onClick = { showMore = true },
                                            modifier = Modifier.focusRequester(moreInfoButton),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Series: seasons, then that season's episodes.
                    if (s != null && s.seasons.isNotEmpty()) {
                        // The season you're up to, until you pick another.
                        val shownSeason = if (season in s.seasons) season else {
                            resume?.let { r ->
                                s.seasons.firstOrNull { sn -> s.episodes[sn].orEmpty().any { "episode:${it.id}" == r.key } }
                            } ?: s.seasons.first()
                        }
                        val seasonEpisodes = s.episodes[shownSeason].orEmpty()
                        item(key = "seasons") {
                            Row(
                                modifier = Modifier.padding(start = 48.dp - 3.dp, end = 48.dp, top = 22.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (number in s.seasons) {
                                    SeasonTab(text = "Season $number", selected = number == shownSeason, onClick = { season = number })
                                }
                                Text(
                                    text = "${seasonEpisodes.size} episode" + if (seasonEpisodes.size == 1) "" else "s",
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = WiptvType.hint,
                                )
                            }
                        }
                        val fallbackArt = s.backdrop ?: item.backdrop ?: item.poster
                        items(seasonEpisodes, key = { "episode:" + it.id }) { episode ->
                            val point = remember(refresh, episode.id) { store.resumePoint("episode:${episode.id}") }
                            Box(modifier = Modifier.padding(start = 48.dp - 10.dp, end = 48.dp).widthIn(max = 820.dp)) {
                                EpisodeRow(
                                    episode = episode,
                                    title = EpisodeTitles.display(episode, item.name),
                                    fallbackArt = fallbackArt,
                                    point = point,
                                    watched = "episode:${episode.id}" in watched,
                                    onClick = { playEpisode(s, episode, point?.position ?: 0) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMore) {
        MoreInfo(
            title = title,
            meta = meta.joinToString("  ·  "),
            plot = plot,
            cast = cast,
            director = director,
            onClose = {
                showMore = false
                refocusMoreInfo = true
            },
        )
    }
}

/**
 * The whole synopsis, cast and director, in a dark panel on the right, about 42% of the screen
 * wide and only as tall as its text (long text scrolls within the screen). Up and Down scroll
 * it; Back closes it and focus goes back to More info.
 */
@Composable
private fun MoreInfo(title: String, meta: String, plot: String?, cast: String?, director: String?, onClose: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).padding(24.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(400.dp)
                    .glass(WiptvShapes.panel, Glass.Sheet)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val step = when (event.key) {
                            Key.DirectionDown -> 160f
                            Key.DirectionUp -> -160f
                            else -> return@onKeyEvent false
                        }
                        scope.launch { scroll.animateScrollBy(step) }
                        true
                    }
                    .verticalScroll(scroll)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = title, style = WiptvType.screen)
                if (meta.isNotBlank()) Text(text = meta, style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium))
                if (!plot.isNullOrBlank()) {
                    Text(
                        text = plot,
                        modifier = Modifier.padding(top = 6.dp),
                        style = WiptvType.body.copy(lineHeight = 25.sp),
                    )
                }
                if (cast != null) {
                    Text(text = "Starring", modifier = Modifier.padding(top = 10.dp), style = WiptvType.hint)
                    Text(text = cast, style = WiptvType.metadata.copy(color = WiptvColors.text))
                }
                if (director != null) {
                    Text(text = "Directed by", modifier = Modifier.padding(top = 6.dp), style = WiptvType.hint)
                    Text(text = director, style = WiptvType.metadata.copy(color = WiptvColors.text))
                }
                Text(text = "Back to close", modifier = Modifier.padding(top = 14.dp), style = WiptvType.hint)
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
}

/** What plays after [current]: the next episode in order, across seasons. */
private fun chain(
    all: List<Episode>,
    current: Episode,
    pointFor: (Episode, Long) -> ResumePoint,
    onAdvance: () -> Unit,
): (() -> ResumePoint?) {
    var at = current
    return {
        onAdvance()
        val next = all.getOrNull(all.indexOfFirst { it.id == at.id } + 1)
        if (next != null) at = next
        next?.let { pointFor(it, 0) }
    }
}

/**
 * A season in the row of seasons. The season being shown keeps a soft wash and an underline, so
 * it stays marked when focus moves down into its episodes; focus is the light fill and outline.
 * OK picks the season (focus alone doesn't switch).
 */
@Composable
private fun SeasonTab(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = WiptvShapes.pill
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) WiptvColors.focus.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (selected) WiptvColors.text else WiptvColors.secondary,
            focusedContainerColor = WiptvColors.focus,
            focusedContentColor = WiptvColors.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = focusOutline(shape),
    ) {
        Column(
            modifier = Modifier.height(36.dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = text, style = WiptvType.control.copy(fontSize = 15.sp, lineHeight = 18.sp))
            if (selected) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .width(18.dp)
                        .height(2.dp)
                        .background(if (focused) WiptvColors.onLight else WiptvColors.focus, WiptvShapes.pill),
                )
            }
        }
    }
}

/**
 * One episode: a small still (or the series artwork, softened), the clean title,
 * a quieter description, and on the right its length and how far you've got. No box at rest,
 * just a hairline underneath. Focus is a soft fill across the whole row and a short bright bar
 * at its left edge, with no outline, glow or scaling (like the Live TV guide).
 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    title: String,
    fallbackArt: String?,
    point: ResumePoint?,
    watched: Boolean,
    onClick: () -> Unit,
) {
    val shape = WiptvShapes.small
    val progress = point?.progress?.takeIf { it > 0f }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        targetValue = if (focused) WiptvColors.rowFocus else WiptvColors.rowFocus.copy(alpha = 0f),
        animationSpec = tween(120),
        label = "episodeFill",
    )
    val marker by animateFloatAsState(if (focused) 1f else 0f, animationSpec = tween(120), label = "episodeMarker")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(10.dp.toPx(), size.height - 0.5f),
                    end = Offset(size.width - 10.dp.toPx(), size.height - 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        tonalElevation = 0.dp,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        // The fill is drawn below (so it can fade); the Surface itself paints nothing.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiptvColors.text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = WiptvColors.text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = WiptvColors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(fill, cornerRadius = CornerRadius(8.dp.toPx()))
                    if (marker > 0f) {
                        // 3 × 24 dp, centred, in the gap left of the still.
                        val h = 24.dp.toPx()
                        drawRoundRect(
                            color = WiptvColors.rowFocusMarker.copy(alpha = marker),
                            topLeft = Offset(3.dp.toPx(), (size.height - h) / 2),
                            size = Size(3.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                }
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The still, 16:9.
            Box(
                modifier = Modifier
                    .size(width = 136.dp, height = 77.dp)
                    .clip(WiptvShapes.small)
                    .background(Brush.verticalGradient(listOf(WiptvColors.elevated, WiptvColors.surface))),
            ) {
                if (episode.image != null) {
                    AsyncImage(
                        model = episode.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // No still: the series artwork, softened.
                    if (fallbackArt != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(fallbackArt).size(48, 27).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.6f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (watched && progress == null) {
                    Text(
                        text = "✓",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), WiptvShapes.pill)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WiptvColors.positive),
                    )
                }
                if (progress != null) {
                    ProgressLine(
                        progress = progress,
                        height = 3.dp,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.control.copy(color = WiptvColors.text),
                )
                if (episode.plot.isNotBlank()) {
                    Text(
                        text = episode.plot,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = WiptvColors.quiet),
                    )
                }
            }

            Column(modifier = Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
                episode.durationMinutes?.let { Text(text = "$it min", style = WiptvType.hint.copy(fontSize = 13.sp)) }
                when {
                    progress != null && point != null -> timeLeft(point)?.let {
                        Text(text = it, style = WiptvType.hint.copy(color = WiptvColors.progress))
                    }
                    watched -> Text(text = "Watched", style = WiptvType.hint.copy(color = WiptvColors.positive))
                }
            }
        }
    }
}

/** Opens a trailer in the YouTube app, or the browser if that's not there. */
private fun openTrailer(context: Context, trailer: String) {
    val id = Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{6,})").find(trailer)?.groupValues?.get(1) ?: trailer.trim()
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Try the next way.
        }
    }
    Toast.makeText(context, "Couldn't open the trailer", Toast.LENGTH_SHORT).show()
}

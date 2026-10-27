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

    LaunchedEffect(Unit) {
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
        subtitle = "S${episode.season} E${episode.number} · ${episode.title}",
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
    val cast = (m?.cast ?: s?.cast)?.ifBlank { null }
    val director = m?.director?.ifBlank { null }
    var plotCut by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(WiptvColors.background)) {
            // Full-screen artwork, faded on the left and at the bottom. Waits for the details so it
            // doesn't show the poster wash and then swap straight to the backdrop.
            CinematicBackdrop(
                backdrop = m?.backdrop ?: s?.backdrop ?: item.backdrop,
                poster = if (loaded) item.poster else null,
                modifier = Modifier.fillMaxSize(),
                delayMillis = 0,
                leftFade = 0.8f,
            )

            PivotScrolling(pivot = 110.dp) {
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
                                    text = item.name,
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
                                        modifier = Modifier.padding(top = 14.dp).widthIn(max = 580.dp),
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
                                        ActionButton(text = "More info", onClick = { showMore = true })
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
                        item(key = "seasons") {
                            Row(
                                modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (number in s.seasons) {
                                    SeasonTab(text = "Season $number", selected = number == shownSeason, onClick = { season = number })
                                }
                            }
                        }
                        items(s.episodes[shownSeason].orEmpty(), key = { "episode:" + it.id }) { episode ->
                            val point = remember(refresh, episode.id) { store.resumePoint("episode:${episode.id}") }
                            Box(modifier = Modifier.padding(horizontal = 48.dp, vertical = 2.dp).widthIn(max = 760.dp)) {
                                EpisodeRow(
                                    episode = episode,
                                    progress = point?.progress,
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
            title = item.name,
            meta = meta.joinToString("  ·  "),
            plot = plot,
            cast = cast,
            director = director,
            onClose = { showMore = false },
        )
    }
}

/**
 * The whole synopsis, cast and director, in a panel on the right. Up and Down scroll it; Back
 * closes it and focus goes back to More info.
 */
@Composable
private fun MoreInfo(title: String, meta: String, plot: String?, cast: String?, director: String?, onClose: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(500.dp)
                    .fillMaxHeight()
                    .glass(WiptvShapes.panel, Glass.Drawer)
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
                    .padding(28.dp),
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

@Composable
private fun SeasonTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.glow else Palette.tile,
            contentColor = if (selected) Palette.background else Palette.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: Float?, watched: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.tile.copy(alpha = 0.6f),
            contentColor = Palette.text,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "E${episode.number}",
                    modifier = Modifier.width(34.dp),
                    style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                )
                Text(
                    text = episode.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                val tail = listOfNotNull(episode.durationMinutes?.let { "$it min" }, if (watched) "✓ Watched" else null)
                Text(text = tail.joinToString(" · "), style = TextStyle(fontSize = 11.sp, color = Palette.quiet))
            }
            if (episode.plot.isNotBlank()) {
                Text(
                    text = episode.plot,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 44.dp),
                    style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
                )
            }
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .padding(start = 44.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Palette.background, RoundedCornerShape(2.dp)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Palette.glow, RoundedCornerShape(2.dp)))
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

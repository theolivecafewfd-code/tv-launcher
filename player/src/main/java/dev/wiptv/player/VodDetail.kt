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
import dev.wiptv.design.ListRow
import dev.wiptv.design.SectionRow
import dev.wiptv.design.PosterCard
import dev.wiptv.design.ActionButton
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.InfoSheet
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
    /** A title from "More like this": its page replaces this one. */
    onOpenOther: (VodItem) -> Unit = {},
) {
    val context = LocalContext.current
    // More like this: other titles from the same provider category, from the player's own list
    // on the TV (no download), as on the launcher's details pages.
    var similar by remember(item.key) { mutableStateOf<List<VodItem>>(emptyList()) }
    LaunchedEffect(item.key) {
        similar = withContext(Dispatchers.IO) {
            CatalogIndex.similar(context, item.kind, item.id, item.categoryId, item.name).map {
                VodItem(
                    kind = it.kind,
                    id = it.id,
                    name = it.name,
                    poster = it.poster,
                    categoryId = it.categoryId,
                    rating = null,
                    extension = it.extension,
                    year = it.year,
                )
            }
        }
    }
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
        extension = details.extension,
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
        extension = episode.extension,
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
            // Back up to the buttons or season tabs: one smooth scroll to the top, header included.
            PivotScrolling(pivot = 32.dp, upToTopBelow = 64.dp) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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

                    // More like this, under the buttons for a film and under the episodes for a series.
                    if (similar.isNotEmpty()) {
                        item(key = "similar") {
                            SectionRow(title = "More like this", edge = 48.dp, modifier = Modifier.padding(top = 18.dp)) {
                                items(similar, key = { it.key }) { other ->
                                    PosterCard(name = other.name, posterUrl = other.poster, onClick = { onOpenOther(other) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMore) {
        InfoSheet(
            title = title,
            meta = meta.joinToString("  ·  "),
            body = plot,
            sections = listOfNotNull(cast?.let { "Starring" to it }, director?.let { "Directed by" to it }),
            onClose = {
                showMore = false
                refocusMoreInfo = true
            },
        )
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
    val progress = point?.progress?.takeIf { it > 0f }
    // The shared list row: no box at rest, a hairline underneath, and the guide's focus.
    ListRow(
        onClick = onClick,
        hairline = true,
        contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
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

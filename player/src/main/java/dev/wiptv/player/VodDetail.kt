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
    )

    /** Plays an episode; when it ends, the next one (or the next season's first) follows. */
    fun playEpisode(details: SeriesDetails, episode: Episode, from: Long) {
        val all = details.seasons.flatMap { details.episodes[it].orEmpty() }
        onPlay(episodePoint(episode, from), chain(all, episode, ::episodePoint) { refresh++ })
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Palette.background)) {
            val backdrop = movie?.backdrop ?: series?.backdrop ?: item.backdrop
            if (backdrop != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).size(width = 640.dp, height = 360.dp)) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.background, Color.Transparent))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))))
                }
            }

            Row(
                modifier = Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                // Poster.
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.tile),
                ) {
                    if (item.poster != null) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                    )
                    val meta = when {
                        movie != null -> listOfNotNull(
                            movie!!.year.ifBlank { null },
                            movie!!.durationMinutes?.let { "${it / 60}h ${it % 60}m".removePrefix("0h ") },
                            movie!!.genre.ifBlank { null },
                            movie!!.rating?.let { "★ %.1f".format(it) },
                        )
                        series != null -> listOfNotNull(
                            series!!.year.ifBlank { null },
                            "${series!!.seasons.size} season" + if (series!!.seasons.size == 1) "" else "s",
                            series!!.genre.ifBlank { null },
                            series!!.rating?.let { "★ %.1f".format(it) },
                        )
                        else -> emptyList()
                    }
                    Text(text = meta.joinToString(" · "), style = TextStyle(fontSize = 13.sp, color = Palette.quiet))
                    val plot = movie?.plot ?: series?.plot ?: item.plot
                    when {
                        error != null -> Text(text = error!!, style = TextStyle(fontSize = 13.sp, color = Palette.warn))
                        movie == null && series == null -> Text(text = "Loading…", style = TextStyle(fontSize = 13.sp, color = Palette.quiet))
                        !plot.isNullOrBlank() -> Text(
                            text = plot,
                            maxLines = if (item.kind == VodKind.MOVIE) 5 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(560.dp),
                            style = TextStyle(fontSize = 13.sp, color = Palette.text),
                        )
                    }
                    val people = movie?.let { listOfNotNull(it.cast.ifBlank { null }?.let { c -> "With $c" }, it.director.ifBlank { null }?.let { d -> "Directed by $d" }) }
                        ?: series?.cast?.ifBlank { null }?.let { listOf("With $it") }
                        ?: emptyList()
                    for (line in people) {
                        Text(
                            text = line,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(560.dp),
                            style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val m = movie
                        val s = series
                        when {
                            m != null && resume != null -> {
                                PlayerButton(
                                    text = "▶ Resume from ${clockOf(resume.position)}",
                                    onClick = { onPlay(moviePoint(m, resume.position), null) },
                                    primary = true,
                                    modifier = Modifier.focusRequester(firstButton),
                                )
                                PlayerButton(text = "Start over", onClick = { onPlay(moviePoint(m, 0), null) })
                            }
                            m != null -> PlayerButton(
                                text = "▶ Play",
                                onClick = { onPlay(moviePoint(m, 0), null) },
                                primary = true,
                                modifier = Modifier.focusRequester(firstButton),
                            )
                            s != null && s.seasons.isNotEmpty() -> {
                                val all = s.seasons.flatMap { s.episodes[it].orEmpty() }
                                val resumeEpisode = resume?.let { r -> all.firstOrNull { "episode:${it.id}" == r.key } }
                                if (resumeEpisode != null && resume != null) {
                                    PlayerButton(
                                        text = "▶ Resume S${resumeEpisode.season} E${resumeEpisode.number}",
                                        onClick = { playEpisode(s, resumeEpisode, resume.position) },
                                        primary = true,
                                        modifier = Modifier.focusRequester(firstButton),
                                    )
                                } else {
                                    // Next unwatched episode, or the very first.
                                    val next = all.firstOrNull { "episode:${it.id}" !in watched } ?: all.first()
                                    PlayerButton(
                                        text = "▶ Play S${next.season} E${next.number}",
                                        onClick = { playEpisode(s, next, 0) },
                                        primary = true,
                                        modifier = Modifier.focusRequester(firstButton),
                                    )
                                }
                            }
                        }
                        val trailer = movie?.trailer ?: series?.trailer
                        if (trailer != null) {
                            PlayerButton(text = "Trailer", onClick = { openTrailer(context, trailer) })
                        }
                        PlayerButton(
                            text = if (inList) "✓ My List" else "+ My List",
                            onClick = {
                                inList = store.toggleFavourite(item)
                                onListChanged()
                            },
                            modifier = if (movie == null && series == null) Modifier.focusRequester(firstButton) else Modifier,
                        )
                    }

                    // Series: seasons, then that season's episodes.
                    val s = series
                    if (s != null && s.seasons.isNotEmpty()) {
                        // The season you're up to, until you pick another.
                        val shownSeason = if (season in s.seasons) season else {
                            resume?.let { r ->
                                s.seasons.firstOrNull { sn -> s.episodes[sn].orEmpty().any { "episode:${it.id}" == r.key } }
                            } ?: s.seasons.first()
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (number in s.seasons) {
                                SeasonTab(text = "Season $number", selected = number == shownSeason, onClick = { season = number })
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f).width(600.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(s.episodes[shownSeason].orEmpty(), key = { it.id }) { episode ->
                                val point = remember(refresh, episode.id) { store.resumePoint("episode:${episode.id}") }
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

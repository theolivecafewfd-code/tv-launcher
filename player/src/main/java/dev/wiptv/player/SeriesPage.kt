package dev.wiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.wiptv.design.ActionButton
import dev.wiptv.design.Glass
import dev.wiptv.design.ListRow
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import dev.wiptv.design.glass
import kotlinx.coroutines.CancellationException

/**
 * A TV show's page, laid out as one screen over its artwork: on the left the show's title logo
 * (TMDB's, or its name in large type), the year, seasons, episode length and score, the synopsis,
 * genres and cast as small pills, and the buttons (Resume or Play, Trailer, My List, More info);
 * on the right a glass panel with ‹ Season › and that season's episodes. Right from the buttons
 * goes into the episodes, Left comes back. The data and playing are VodDetailScreen's.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SeriesPage(
    item: VodItem,
    series: SeriesDetails?,
    error: String?,
    title: String,
    plot: String?,
    shownSeason: Int?,
    onSeason: (Int) -> Unit,
    resume: ResumePoint?,
    watched: Set<String>,
    refresh: Int,
    store: VodStore,
    inList: Boolean,
    onToggleList: () -> Unit,
    onPlayEpisode: (Episode, Long) -> Unit,
    onTrailer: (() -> Unit)?,
    onMoreInfo: () -> Unit,
    showMoreInfo: Boolean,
    firstButton: FocusRequester,
    moreInfoButton: FocusRequester,
    focusEpisode: String?,
    episodeFocus: FocusRequester,
    episodesState: LazyListState,
    onPlotCut: (Boolean) -> Unit,
) {
    val s = series
    val year = s?.year?.ifBlank { null } ?: item.year

    // The title logo from TMDB, when there is one.
    var logo by remember(item.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.key, title) {
        logo = try {
            Tmdb.seriesLogo(searchName(title), year?.take(4))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // ---- Left: the show ----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 48.dp, end = 24.dp, top = 40.dp, bottom = 36.dp)
                // Left from the episodes comes back to Resume or Play.
                .focusProperties { enter = { firstButton } }
                .focusGroup(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (logo != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(logo).size(840, 300).build(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.widthIn(max = 400.dp).heightIn(max = 120.dp),
                )
            } else {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.hero.copy(fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
            Spacer(Modifier.height(18.dp))

            // 2005 · 3 seasons · 45 min · score: spaced apart, no dots.
            val facts = listOfNotNull(
                year,
                s?.seasons?.size?.takeIf { it > 0 }?.let { "$it season" + if (it == 1) "" else "s" },
                s?.let { d -> d.seasons.firstNotNullOfOrNull { sn -> d.episodes[sn].orEmpty().firstNotNullOfOrNull { it.durationMinutes } } }?.let { "$it min" },
                scoreOf(s?.rating ?: item.rating),
            )
            if (facts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    facts.forEach { Text(text = it, style = WiptvType.metadata.copy(color = WiptvColors.text, fontWeight = FontWeight.SemiBold)) }
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                error != null -> Text(text = error, style = WiptvType.body.copy(color = WiptvColors.warning))
                s == null -> Text(text = "Loading…", style = WiptvType.body.copy(color = WiptvColors.quiet))
                !plot.isNullOrBlank() -> Text(
                    text = plot,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { onPlotCut(it.hasVisualOverflow) },
                    modifier = Modifier.widthIn(max = 480.dp),
                    style = WiptvType.metadata.copy(lineHeight = 21.sp, color = WiptvColors.text.copy(alpha = 0.88f)),
                )
            }

            val genres = s?.genre.orEmpty().split(',', '/', '&').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
            val cast = s?.cast.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            if (genres.isNotEmpty() || cast.isNotEmpty()) Spacer(Modifier.height(14.dp))
            if (genres.isNotEmpty()) PillLine(label = "GENRES", values = genres)
            if (cast.isNotEmpty()) {
                if (genres.isNotEmpty()) Spacer(Modifier.height(8.dp))
                PillLine(label = "CAST", values = cast)
            }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val all = s?.let { d -> d.seasons.flatMap { d.episodes[it].orEmpty() } }.orEmpty()
                if (all.isNotEmpty()) {
                    val resumeEpisode = resume?.let { r -> all.firstOrNull { "episode:${it.id}" == r.key } }
                    if (resumeEpisode != null && resume != null) {
                        ActionButton(
                            text = "▶  Resume S${resumeEpisode.season} E${resumeEpisode.number}",
                            primary = true,
                            onClick = { onPlayEpisode(resumeEpisode, resume.position) },
                            modifier = Modifier.focusRequester(firstButton),
                        )
                    } else {
                        // The next one you haven't watched, or the very first.
                        val next = all.firstOrNull { "episode:${it.id}" !in watched } ?: all.first()
                        val started = all.any { "episode:${it.id}" in watched }
                        ActionButton(
                            text = if (started) "▶  Play S${next.season} E${next.number}" else "▶  Play",
                            primary = true,
                            onClick = { onPlayEpisode(next, 0) },
                            modifier = Modifier.focusRequester(firstButton),
                        )
                    }
                }
                if (onTrailer != null) ActionButton(text = "Trailer", onClick = onTrailer)
                ActionButton(
                    text = if (inList) "✓  My List" else "+  My List",
                    onClick = onToggleList,
                    modifier = if (all.isEmpty()) Modifier.focusRequester(firstButton) else Modifier,
                )
                if (showMoreInfo) {
                    ActionButton(text = "More info", onClick = onMoreInfo, modifier = Modifier.focusRequester(moreInfoButton))
                }
            }
        }

        // ---- Right: seasons and episodes ----
        if (s != null && s.seasons.isNotEmpty() && shownSeason != null) {
            EpisodesPanel(
                item = item,
                series = s,
                shownSeason = shownSeason,
                onSeason = onSeason,
                watched = watched,
                refresh = refresh,
                store = store,
                onPlayEpisode = onPlayEpisode,
                startKey = focusEpisode ?: resume?.key,
                focusEpisode = focusEpisode,
                episodeFocus = episodeFocus,
                state = episodesState,
            )
        }
    }
}

/** "GENRES  [Reality] [Game show]": a quiet label and small glass pills. */
@Composable
private fun PillLine(label: String, values: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(66.dp),
            style = WiptvType.hint.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .glass(WiptvShapes.pill, Glass.Nav)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    style = WiptvType.hint.copy(fontSize = 12.sp, color = WiptvColors.text),
                )
            }
        }
    }
}

/**
 * The glass panel on the right: ‹ Season 2 · 10 episodes › at the top (the arrows change season),
 * then the season's episodes, each with its still, number and name, and its length or how far
 * you've got. The list scrolls inside the panel.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodesPanel(
    item: VodItem,
    series: SeriesDetails,
    shownSeason: Int,
    onSeason: (Int) -> Unit,
    watched: Set<String>,
    refresh: Int,
    store: VodStore,
    onPlayEpisode: (Episode, Long) -> Unit,
    /** The episode you're up to: the list opens at it. */
    startKey: String?,
    focusEpisode: String?,
    episodeFocus: FocusRequester,
    state: LazyListState,
) {
    val seasons = series.seasons
    val index = seasons.indexOf(shownSeason)
    val episodes = series.episodes[shownSeason].orEmpty()
    val fallbackArt = series.backdrop ?: item.backdrop ?: item.poster
    // The list opens at the episode you're up to; another season starts at its top.
    val firstShow = remember { booleanArrayOf(true) }
    LaunchedEffect(shownSeason) {
        val start = if (firstShow[0]) episodes.indexOfFirst { "episode:${it.id}" == startKey } else -1
        firstShow[0] = false
        state.scrollToItem(start.coerceAtLeast(0))
    }
    // Right from the buttons lands on the episode you're up to if it's showing, else the top one.
    val entry = remember { FocusRequester() }
    val entryKey by remember(episodes, startKey) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo.map { it.key }
            if (startKey != null && startKey in visible) startKey else visible.firstOrNull() as? String
        }
    }
    Column(
        modifier = Modifier
            .width(372.dp)
            .fillMaxHeight()
            .padding(top = 36.dp, bottom = 28.dp, end = 32.dp)
            .glass(WiptvShapes.panel, Glass.Drawer)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundArrow(text = "‹", enabled = index > 0, onClick = { onSeason(seasons[index - 1]) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(WiptvColors.focus.copy(alpha = 0.08f), WiptvShapes.pill)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Season $shownSeason",
                    maxLines = 1,
                    style = WiptvType.control.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                )
            }
            RoundArrow(text = "›", enabled = index < seasons.size - 1, onClick = { onSeason(seasons[index + 1]) })
        }
        Text(
            text = "${episodes.size} episode" + (if (episodes.size == 1) "" else "s") +
                if (seasons.size > 1) "  ·  season ${index + 1} of ${seasons.size}" else "",
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp),
            style = WiptvType.hint,
        )
        PivotScrolling(pivot = 8.dp) {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusProperties { enter = { if (entryKey != null) entry else FocusRequester.Default } }
                    .focusGroup(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(episodes, key = { _, e -> "episode:" + e.id }) { _, episode ->
                    val point = remember(refresh, episode.id) { store.resumePoint("episode:${episode.id}") }
                    PanelEpisode(
                        episode = episode,
                        title = EpisodeTitles.display(episode, item.name),
                        fallbackArt = fallbackArt,
                        point = point,
                        watched = "episode:${episode.id}" in watched,
                        onClick = { onPlayEpisode(episode, point?.position ?: 0) },
                        modifier = Modifier
                            .then(if ("episode:${episode.id}" == focusEpisode) Modifier.focusRequester(episodeFocus) else Modifier)
                            .then(if ("episode:${episode.id}" == entryKey) Modifier.focusRequester(entry) else Modifier),
                    )
                }
            }
        }
    }
}

/**
 * A round ‹ or › button beside the season. At either end it's dimmed and does nothing, but stays
 * focusable, so focus never drops off it when you reach the last season.
 */
@Composable
private fun RoundArrow(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = WiptvShapes.pill
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(38.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiptvColors.focus.copy(alpha = 0.08f),
            contentColor = if (enabled) WiptvColors.text else WiptvColors.quiet.copy(alpha = 0.5f),
            focusedContainerColor = if (enabled) WiptvColors.focus else WiptvColors.focus.copy(alpha = 0.35f),
            focusedContentColor = WiptvColors.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = focusOutline(shape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = text, style = TextStyle(fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = androidx.tv.material3.LocalContentColor.current))
        }
    }
}

/**
 * One episode in the panel: a small still (or the show's artwork, softened) with the progress bar
 * or a ✓, then "3. Name" and its length, time left or Watched. Focus is the list row's soft fill
 * and short bar on the left, as in the guide.
 */
@Composable
private fun PanelEpisode(
    episode: Episode,
    title: String,
    fallbackArt: String?,
    point: ResumePoint?,
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = point?.progress?.takeIf { it > 0f }
    ListRow(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 58.dp)
                .clip(WiptvShapes.small)
                .background(Brush.verticalGradient(listOf(WiptvColors.elevated, WiptvColors.surface))),
        ) {
            val art = episode.image
            if (art != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(art).size(312, 174).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (fallbackArt != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(fallbackArt).size(48, 27).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (watched && progress == null) {
                Text(
                    text = "✓",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f), WiptvShapes.pill)
                        .padding(horizontal = 6.dp),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WiptvColors.positive),
                )
            }
            if (progress != null) {
                ProgressLine(
                    progress = progress,
                    height = 3.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.number}. $title",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.control.copy(fontSize = 14.sp, lineHeight = 18.sp, color = WiptvColors.text),
            )
            val note = when {
                progress != null && point != null -> timeLeft(point)
                watched -> "Watched"
                else -> episode.durationMinutes?.let { "$it min" }
            }
            if (note != null) {
                Text(
                    text = note,
                    modifier = Modifier.padding(top = 2.dp),
                    style = WiptvType.hint.copy(
                        color = when {
                            progress != null -> WiptvColors.progress
                            watched -> WiptvColors.positive
                            else -> WiptvColors.quiet
                        },
                    ),
                )
            }
        }
    }
}

/** The show's name as TMDB would know it: without a leading "EN - " or "UK: " tag. */
private fun searchName(title: String): String = title.replace(Regex("^[A-Z]{2,3}\\s*[-:|]\\s*"), "").trim()

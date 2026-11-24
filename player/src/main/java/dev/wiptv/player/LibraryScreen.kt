package dev.wiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.wiptv.design.ActionButton
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.ContinueCard
import dev.wiptv.design.ContinueGap
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.PosterCard
import dev.wiptv.design.SectionRow
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.rememberRowStart
import dev.wiptv.design.rememberWideCard
import dev.wiptv.design.wiptvBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A trending title your provider has: what TMDB says about it, and where it is in your list. */
data class LibraryPick(val trending: TrendingTitle, val title: IndexedTitle)

/** This week's trending picks, kept while the player runs (refetched after three hours). */
private object LibraryCache {
    var picks: List<LibraryPick>? = null
    var at = 0L
}

private val Edge = 32.dp

/**
 * Library, the player's home (version 0.3, after the max player). At the top, a banner of
 * what's trending this week on TMDB that your provider has, one at a time with its artwork,
 * details, Play and More info; Left on Play and Right on More info move through them, and they
 * move on by themselves every few seconds otherwise. Below, Continue watching and My List
 * (films, then TV shows). Play starts a film (a series opens its page); More info and the My
 * List posters open the details page; a Continue watching card resumes.
 */
@Composable
fun LibraryScreen(
    store: VodStore,
    header: @Composable () -> Unit,
    onPlay: (IndexedTitle) -> Unit,
    onDetails: (VodKind, Long) -> Unit,
    onResume: (ResumePoint) -> Unit,
) {
    val context = LocalContext.current
    var picks by remember { mutableStateOf(LibraryCache.picks) }
    LaunchedEffect(Unit) {
        if (!Tmdb.hasKey) {
            picks = emptyList()
            return@LaunchedEffect
        }
        if (picks != null && System.currentTimeMillis() - LibraryCache.at < 3 * 60 * 60_000L) return@LaunchedEffect
        val found = try {
            val films = Tmdb.trending(VodKind.MOVIE)
            val shows = Tmdb.trending(VodKind.SERIES)
            withContext(Dispatchers.IO) {
                fun available(list: List<TrendingTitle>) = list.mapNotNull { t ->
                    CatalogIndex.match(context, t.kind, t.name, t.year, t.tmdbId)?.let { LibraryPick(t, it) }
                }
                // Films and shows in turn, the most popular first.
                val a = available(films)
                val b = available(shows)
                (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }.take(10)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            picks ?: emptyList()
        }
        LibraryCache.picks = found
        LibraryCache.at = System.currentTimeMillis()
        picks = found
    }

    val resume = remember { store.resumePoints() }
    val myList = remember { store.favourites() }
    val myFilms = myList.filter { it.kind == VodKind.MOVIE }
    val myShows = myList.filter { it.kind == VodKind.SERIES }

    var slide by remember { mutableIntStateOf(0) }
    val shown = picks.orEmpty()
    val current = shown.getOrNull(slide.coerceIn(0, (shown.size - 1).coerceAtLeast(0)))
    var heroFocused by remember { mutableStateOf(false) }
    // The banner moves on by itself, except while you're on its buttons.
    LaunchedEffect(shown.size, heroFocused, slide) {
        if (shown.size < 2 || heroFocused) return@LaunchedEffect
        delay(9_000)
        slide = (slide + 1) % shown.size
    }

    val play = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Focus starts on Play once the banner is there.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(current != null) {
        if (placed || current == null) return@LaunchedEffect
        repeat(20) {
            if (runCatching { play.requestFocus() }.isSuccess) {
                placed = true
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().wiptvBackground()) {
        // The banner's artwork, large at the top right, while the banner is on screen.
        if (current != null && listState.firstVisibleItemIndex == 0) {
            CinematicBackdrop(
                backdrop = current.trending.backdrop,
                poster = current.trending.poster,
                modifier = Modifier.align(Alignment.TopEnd).size(width = 780.dp, height = 439.dp),
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Box(modifier = Modifier.padding(horizontal = Edge)) { header() }
            PivotScrolling(pivot = 34.dp, alwaysAlign = true) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "hero") {
                        Hero(
                            pick = current,
                            index = slide.coerceIn(0, (shown.size - 1).coerceAtLeast(0)),
                            count = shown.size,
                            loading = picks == null,
                            playRequester = play,
                            onFocusChange = { heroFocused = it },
                            onPrevious = { if (shown.isNotEmpty()) slide = (slide - 1 + shown.size) % shown.size },
                            onNext = { if (shown.isNotEmpty()) slide = (slide + 1) % shown.size },
                            onPlay = { current?.let { onPlay(it.title) } },
                            onMoreInfo = { current?.let { onDetails(it.title.kind, it.title.id) } },
                        )
                    }
                    if (resume.isNotEmpty()) {
                        item(key = "resume") {
                            val wideCard = rememberWideCard()
                            val rowStart = rememberRowStart()
                            SectionRow(
                                title = "Continue watching",
                                edge = Edge,
                                contentStart = Edge,
                                spacing = ContinueGap,
                                rowModifier = rowStart.rowModifier(resume.firstOrNull()?.key),
                            ) {
                                items(resume, key = { it.key }) { point ->
                                    ContinueCard(
                                        title = point.title,
                                        episode = point.subtitle?.substringBefore(" · "),
                                        left = timeLeft(point),
                                        posterUrl = point.poster,
                                        imageUrl = point.image,
                                        progress = point.progress,
                                        wide = wideCard.wide == point.key,
                                        focused = wideCard.focused == point.key,
                                        onFocusChange = { wideCard.onFocus(point.key, it) },
                                        onClick = { onResume(point) },
                                        modifier = rowStart.card(point.key),
                                    )
                                }
                            }
                        }
                    }
                    listOf("My List: films" to myFilms, "My List: TV shows" to myShows).forEach { (title, list) ->
                        if (list.isNotEmpty()) {
                            item(key = title) {
                                val wideCard = rememberWideCard()
                                val rowStart = rememberRowStart()
                                SectionRow(title = title, edge = Edge, rowModifier = rowStart.rowModifier(list.firstOrNull()?.key)) {
                                    items(list, key = { it.key }) { item ->
                                        PosterCard(
                                            name = item.name,
                                            posterUrl = item.poster,
                                            backdropUrl = item.backdrop,
                                            onClick = { onDetails(item.kind, item.id) },
                                            wide = wideCard.wide == item.key,
                                            onFocus = { wideCard.onFocus(item.key, it) },
                                            modifier = rowStart.card(item.key),
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
}

/** The banner's text: #n TRENDING, the title, its details and synopsis, Play and More info, the dots. */
@Composable
private fun Hero(
    pick: LibraryPick?,
    index: Int,
    count: Int,
    loading: Boolean,
    playRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = Edge, top = 26.dp).width(500.dp).height(330.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pick == null) {
            Text(
                text = when {
                    loading -> "Finding what's trending…"
                    !Tmdb.hasKey -> "Library"
                    else -> "Nothing trending from your provider right now."
                },
                style = if (loading || !Tmdb.hasKey) WiptvType.hero else WiptvType.metadata,
            )
            return@Column
        }
        val t = pick.trending
        Text(
            text = "#${index + 1} TRENDING",
            modifier = Modifier.background(WiptvColors.accent, WiptvShapes.small).padding(horizontal = 9.dp, vertical = 3.dp),
            style = WiptvType.hint.copy(color = WiptvColors.onLight, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        )
        Text(
            text = t.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = WiptvType.hero.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = listOfNotNull(
                if (t.kind == VodKind.MOVIE) "Movie" else "TV show",
                t.genres.joinToString(", ").ifBlank { null },
                t.year,
                t.rating.takeIf { it > 0 }?.let { "TMDB %.1f".format(it) },
            ).joinToString(" · "),
            style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium),
        )
        if (t.overview.isNotBlank()) {
            Text(
                text = t.overview,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.metadata.copy(color = WiptvColors.text.copy(alpha = 0.86f)),
            )
        }
        var playFocused by remember { mutableStateOf(false) }
        var moreFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .onFocusChanged { onFocusChange(it.hasFocus) }
                // Left on Play and Right on More info move through the banner.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionLeft && playFocused -> { onPrevious(); true }
                        event.key == Key.DirectionRight && moreFocused -> { onNext(); true }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionButton(
                text = if (t.kind == VodKind.MOVIE) "▶  Play" else "▶  Watch",
                onClick = onPlay,
                primary = true,
                modifier = Modifier.focusRequester(playRequester).onFocusChanged { playFocused = it.isFocused },
            )
            ActionButton(text = "More info", onClick = onMoreInfo, modifier = Modifier.onFocusChanged { moreFocused = it.isFocused })
        }
        if (count > 1) {
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .size(width = if (i == index) 18.dp else 5.dp, height = 5.dp)
                            .background(if (i == index) WiptvColors.text else WiptvColors.quiet.copy(alpha = 0.6f), WiptvShapes.pill),
                    )
                }
            }
        }
    }
}

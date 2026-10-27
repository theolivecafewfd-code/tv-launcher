package dev.wiptv.player

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.RowScrolling
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.focusOutline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the big area at the top describes: the poster you're on. */
private data class Hero(
    val title: String,
    val meta: String,
    val plot: String?,
    val backdrop: String?,
    val poster: String?,
)

private fun heroOf(item: VodItem, details: MovieDetails? = null): Hero {
    val year = details?.year?.ifBlank { null } ?: item.year
    return Hero(
        title = DisplayTitles.withoutYear(item.name, year),
        meta = listOfNotNull(
            year,
            details?.durationMinutes?.let(::runtimeOf),
            details?.genre?.ifBlank { null }?.split(',', '/')?.take(2)?.joinToString(", ") { it.trim() },
            scoreOf(details?.rating ?: item.rating),
        ).joinToString(" · "),
        plot = details?.plot?.ifBlank { null } ?: item.plot,
        backdrop = item.backdrop ?: details?.backdrop,
        poster = item.poster,
    )
}

/**
 * Something you've started: its title once, then where you're up to and how long is left. The
 * picture and synopsis come from the catalogue entry (and its cached details) when the saved
 * point has no wide artwork of its own, so the top area is never left empty.
 */
private fun heroOf(point: ResumePoint, item: VodItem?, details: MovieDetails?) = Hero(
    title = point.title,
    meta = listOfNotNull(
        point.subtitle?.takeIf { it.isNotBlank() },
        "Resume from ${clockOf(point.position)}",
        timeLeft(point),
    ).joinToString(" · "),
    plot = details?.plot?.ifBlank { null } ?: item?.plot,
    backdrop = point.image ?: item?.backdrop ?: details?.backdrop,
    poster = point.poster ?: item?.poster,
)

/** Poster and row sizes: about six posters across, the seventh peeking in. */
private val PosterWidth = 126.dp
private val PosterHeight = 189.dp
private val PosterGap = 16.dp
private val ScreenEdge = 32.dp

/** The gap between artwork and the focus outline (the shared [ArtFrame]'s). */
private val FocusGap = ArtFocusGap

/**
 * Films or series, Netflix style: Continue watching and My List first, then a row per category
 * from your provider (hide and reorder them with Edit categories at the bottom). OK on a poster
 * opens its details; OK on Continue watching picks up where you stopped.
 */
@Composable
fun CatalogScreen(
    kind: VodKind,
    login: Login,
    update: UpdateHandle,
    store: VodStore,
    tabs: @Composable () -> Unit,
    /** A title the launcher asked for: a film plays at once, a series opens its page. */
    openId: Long? = null,
    /** A Continue watching key the launcher asked to resume: plays straight away. */
    openResume: String? = null,
    /** Called when that title is closed, so the player can hand back to the launcher. */
    onOpenFinished: () -> Unit = {},
    /** A surprise pick (the header's dice): its details page opens, and [onSurpriseShown] clears it. */
    surpriseId: Long? = null,
    onSurpriseShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val noun = if (kind == VodKind.MOVIE) "films" else "series"
    var categories by remember(kind) { mutableStateOf<List<Group>?>(null) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val itemsByCategory = remember(kind) { mutableStateMapOf<String, List<VodItem>>() }
    var hero by remember(kind) { mutableStateOf<Hero?>(null) }
    // Films' rows rarely carry a backdrop or synopsis, so once focus rests on one its details are
    // fetched (once) to fill the top area. Never per key press: moving on cancels the wait.
    var restingOn by remember(kind) { mutableStateOf<VodItem?>(null) }
    // Set while focus is on a Continue watching card, so its details fill in the resume hero.
    var restingPoint by remember(kind) { mutableStateOf<ResumePoint?>(null) }
    val movieDetails = remember(kind) { mutableStateMapOf<Long, MovieDetails>() }
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var playing by remember { mutableStateOf<ResumePoint?>(null) }
    var playingNext by remember { mutableStateOf<(() -> ResumePoint?)?>(null) }
    // Bumped after watching or changing My List, so those rows refresh.
    var refresh by remember { mutableIntStateOf(0) }

    val prefs = remember(kind) { GroupPrefs(context, if (kind == VodKind.MOVIE) "movie-groups" else "series-groups") }
    var hidden by remember(kind) { mutableStateOf(prefs.hidden()) }
    var order by remember(kind) { mutableStateOf(prefs.order()) }
    var editing by remember { mutableStateOf(false) }
    val arranged = categories?.let { GroupPrefs.arrange(it, order) }
    val shown = arranged?.filter { it.id !in hidden }

    val resumePrefix = if (kind == VodKind.MOVIE) "movie:" else "episode:"
    val resume = remember(refresh, kind) { store.resumePoints().filter { it.key.startsWith(resumePrefix) } }
    val myList = remember(refresh, kind) { store.favourites().filter { it.kind == kind } }
    // The launcher can change My List while the player is in the background: check on return.
    val currentList by rememberUpdatedState(myList)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, kind) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && store.favourites().filter { it.kind == kind } != currentList) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val firstPoster = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(restingOn) {
        val item = restingOn ?: return@LaunchedEffect
        if (item.kind != VodKind.MOVIE || movieDetails.containsKey(item.id)) return@LaunchedEffect
        if (item.backdrop != null && !item.plot.isNullOrBlank()) return@LaunchedEffect
        delay(700)
        val details = try {
            Xtream.movieDetails(login, item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
        movieDetails[item.id] = details
        hero = restingPoint?.let { heroOf(it, item, details) } ?: heroOf(item, details)
    }

    fun focusItem(item: VodItem) {
        hero = heroOf(item, movieDetails[item.id])
        restingPoint = null
        restingOn = item
    }

    /** The catalogue entry a Continue watching card belongs to, from what's already loaded. */
    fun catalogueItemFor(point: ResumePoint): VodItem? {
        val id = (if (kind == VodKind.MOVIE) point.key.removePrefix("movie:").toLongOrNull() else point.seriesId) ?: return null
        return myList.firstOrNull { it.id == id }
            ?: itemsByCategory.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
    }

    fun focusResume(point: ResumePoint) {
        val item = catalogueItemFor(point)
        hero = heroOf(point, item, item?.let { movieDetails[it.id] })
        restingPoint = point
        restingOn = item
    }

    // Opened from the launcher: once that film or series page is closed, go back there.
    var openedFromLauncher by remember { mutableStateOf(false) }
    LaunchedEffect(openResume) {
        val key = openResume ?: return@LaunchedEffect
        val point = store.resumePoint(key) ?: return@LaunchedEffect
        openedFromLauncher = true
        playingNext = null
        playing = point
    }
    LaunchedEffect(openId) {
        val id = openId ?: return@LaunchedEffect
        val entry = withContext(Dispatchers.IO) { CatalogIndex.find(context, kind, id) }
        val item = VodItem(
            kind = kind,
            id = id,
            name = entry?.name.orEmpty(),
            poster = entry?.poster,
            categoryId = entry?.categoryId.orEmpty(),
            rating = null,
            extension = entry?.extension,
            year = entry?.year,
        )
        openedFromLauncher = true
        if (kind == VodKind.SERIES) {
            detail = item
            return@LaunchedEffect
        }
        // A film plays straight away, from where you stopped if you'd started it.
        val details = try {
            Xtream.movieDetails(login, item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            detail = item // its page explains what's wrong and offers Play
            return@LaunchedEffect
        }
        val saved = store.resumePoint("movie:$id")
        playingNext = null
        playing = ResumePoint(
            key = "movie:$id",
            title = item.name,
            subtitle = null,
            poster = item.poster,
            url = Xtream.movieUrl(login, id, details.extension),
            position = saved?.position ?: 0,
            duration = saved?.duration ?: 0,
            updated = System.currentTimeMillis(),
            image = details.backdrop,
        )
    }

    LaunchedEffect(surpriseId) {
        val id = surpriseId ?: return@LaunchedEffect
        val entry = withContext(Dispatchers.IO) { CatalogIndex.find(context, kind, id) }
        onSurpriseShown()
        detail = VodItem(
            kind = kind,
            id = id,
            name = entry?.name.orEmpty(),
            poster = entry?.poster,
            categoryId = entry?.categoryId.orEmpty(),
            rating = null,
            extension = entry?.extension,
            year = entry?.year,
        )
    }

    LaunchedEffect(kind, attempt) {
        error = null
        try {
            categories = Xtream.vodCategories(login, kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load $noun."
        }
    }

    // Start on the first poster once something is showing.
    var focused by remember(kind) { mutableStateOf(false) }
    val anyPoster = resume.isNotEmpty() || myList.isNotEmpty() ||
        shown.orEmpty().any { !itemsByCategory[it.id].isNullOrEmpty() }
    LaunchedEffect(anyPoster) {
        if (!anyPoster || focused) return@LaunchedEffect
        delay(100)
        repeat(20) {
            if (runCatching { firstPoster.requestFocus() }.isSuccess) {
                focused = true
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WiptvColors.background)) {
        // The focused title's artwork, large, top right, fading into the dark behind the text.
        CinematicBackdrop(
            backdrop = hero?.backdrop,
            poster = hero?.poster,
            modifier = Modifier.align(Alignment.TopEnd).size(width = 780.dp, height = 439.dp),
        )

        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Box(modifier = Modifier.padding(horizontal = ScreenEdge)) { tabs() }

            // What the focused poster is: title, year and score, a short synopsis.
            HeroText(
                hero = hero,
                message = when {
                    error != null -> error
                    categories == null -> "Loading $noun…"
                    else -> null
                },
                isError = error != null,
                onRetry = { attempt++ },
            )

            // The focused row always moves to the top of the list, one row per press, so the row
            // above never shows its leftover captions.
            PivotScrolling(pivot = 34.dp, alwaysAlign = true) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    var firstAssigned = false
                    fun firstModifier(): Modifier =
                        if (!firstAssigned) Modifier.focusRequester(firstPoster).also { firstAssigned = true } else Modifier

                    if (resume.isNotEmpty()) {
                        val first = firstModifier()
                        item(key = "resume") {
                            // The title and where you're up to show once, in the hero above; the
                            // cards carry the picture, progress and "OK to resume".
                            PosterRow(title = "Continue watching") {
                                items(resume, key = { it.key }) { point ->
                                    ResumeCard(
                                        point = point,
                                        onFocus = { focusResume(point) },
                                        onClick = { playing = point; playingNext = null },
                                        modifier = if (point == resume.first()) first else Modifier,
                                    )
                                }
                            }
                        }
                    }

                    if (myList.isNotEmpty()) {
                        val first = firstModifier()
                        item(key = "mylist") {
                            PosterRow(title = "My List") {
                                items(myList, key = { it.key }) { item ->
                                    PosterCard(
                                        title = item.name,
                                        poster = item.poster,
                                        onFocus = { focusItem(item) },
                                        onClick = { detail = item },
                                        modifier = if (item == myList.first()) first else Modifier,
                                    )
                                }
                            }
                        }
                    }

                    val firstCategoryModifier = firstModifier()
                    // Rows that turned out empty are left out, so Down never lands on nothing.
                    val visibleCategories = shown.orEmpty().filter { itemsByCategory[it.id]?.isEmpty() != true }
                    visibleCategories.forEachIndexed { index, category ->
                        item(key = "cat:" + category.id) {
                            // Each row loads its own titles when it first comes on screen.
                            LaunchedEffect(category.id) {
                                if (itemsByCategory[category.id] == null) {
                                    itemsByCategory[category.id] = try {
                                        Xtream.vodItems(login, kind, category.id)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }
                            }
                            val rowItems = itemsByCategory[category.id]
                            // While a row loads it shows placeholder posters that can take focus, so
                            // Down moves exactly one row instead of jumping to the next loaded one.
                            // If focus is on a placeholder when the titles arrive, it moves to the first.
                            val firstInRow = remember { FocusRequester() }
                            var waiting by remember { mutableStateOf(false) }
                            LaunchedEffect(rowItems) {
                                if (rowItems.isNullOrEmpty() || !waiting) return@LaunchedEffect
                                repeat(20) {
                                    if (runCatching { firstInRow.requestFocus() }.isSuccess) return@LaunchedEffect
                                    delay(25)
                                }
                            }
                            PosterRow(title = category.name) {
                                if (rowItems == null) {
                                    items(6) { i ->
                                        PosterPlaceholder(
                                            onFocusChange = { focused -> if (focused) waiting = true },
                                            modifier = if (index == 0 && i == 0) firstCategoryModifier else Modifier,
                                        )
                                    }
                                } else {
                                    items(rowItems, key = { it.key }) { item ->
                                        val first = item == rowItems.firstOrNull()
                                        PosterCard(
                                            title = item.name,
                                            poster = item.poster,
                                            onFocus = { waiting = false; focusItem(item) },
                                            onClick = { detail = item },
                                            modifier = if (first) {
                                                Modifier.focusRequester(firstInRow).then(if (index == 0) firstCategoryModifier else Modifier)
                                            } else Modifier,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (categories != null) {
                        item(key = "edit") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(start = ScreenEdge, top = 4.dp),
                            ) {
                                PlayerButton(text = "Edit categories", onClick = { editing = true })
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { item ->
        VodDetailScreen(
            item = item,
            login = login,
            store = store,
            onPlay = { point, next ->
                playing = point
                playingNext = next
            },
            onListChanged = { refresh++ },
            onClose = {
                detail = null
                if (openedFromLauncher) onOpenFinished()
            },
        )
    }

    playing?.let { point ->
        key(point.key) {
            VodPlayerScreen(
                start = point,
                store = store,
                onFinished = {
                    // Straight on to the next episode, if there is one.
                    val next = playingNext?.invoke()
                    playing = next
                    refresh++
                    // A film opened from the launcher: back there when it ends.
                    if (next == null && detail == null && openedFromLauncher) onOpenFinished()
                },
                onClose = {
                    playing = null
                    refresh++
                    if (detail == null && openedFromLauncher) onOpenFinished()
                },
            )
        }
    }

    if (editing && arranged != null) {
        GroupEditor(
            groups = arranged,
            hidden = hidden,
            title = "Edit categories",
            onToggle = { group ->
                hidden = if (group.id in hidden) hidden - group.id else hidden + group.id
                prefs.setHidden(hidden)
            },
            onSetAllHidden = { hide ->
                hidden = if (hide) arranged.map { it.id }.toSet() else emptySet()
                prefs.setHidden(hidden)
            },
            onMove = { group, step ->
                val ids = arranged.map { it.id }.toMutableList()
                val from = ids.indexOf(group.id)
                val to = from + step
                if (from >= 0 && to in ids.indices) {
                    ids.add(to, ids.removeAt(from))
                    order = ids
                    prefs.setOrder(ids)
                }
            },
            onMoveToTop = { group ->
                val ids = arranged.map { it.id }.toMutableList()
                if (ids.remove(group.id)) {
                    ids.add(0, group.id)
                    order = ids
                    prefs.setOrder(ids)
                }
            },
            onDone = { editing = false },
        )
    }
}

/** The title, details and synopsis of the focused poster, over the artwork. Fixed height, so the rows never move. */
@Composable
private fun HeroText(hero: Hero?, message: String?, isError: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(start = ScreenEdge, top = 14.dp)
            .width(470.dp)
            .height(144.dp),
    ) {
        if (hero != null) {
            var titleLines by remember { mutableIntStateOf(1) }
            Text(
                text = hero.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { titleLines = it.lineCount },
                style = WiptvType.hero,
            )
            if (hero.meta.isNotBlank()) {
                Text(
                    text = hero.meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                    style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium),
                )
            }
            if (!hero.plot.isNullOrBlank()) {
                Text(
                    text = hero.plot,
                    maxLines = if (titleLines > 1) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                    style = WiptvType.metadata.copy(color = WiptvColors.text.copy(alpha = 0.86f)),
                )
            }
        } else if (message != null) {
            Text(text = message, style = WiptvType.metadata.copy(color = if (isError) WiptvColors.warning else WiptvColors.quiet))
            if (isError) {
                Spacer(Modifier.height(10.dp))
                PlayerButton(text = "Try again", onClick = onRetry)
            }
        }
    }
}

/** A row's heading, lined up with the posters below it. */
@Composable
private fun RowTitle(title: String, note: String? = null) {
    Row(
        modifier = Modifier.padding(start = ScreenEdge, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = title, style = WiptvType.control.copy(fontSize = 17.sp, color = WiptvColors.text))
        if (note != null) Text(text = note, modifier = Modifier.padding(bottom = 2.dp), style = WiptvType.hint)
    }
}

/** A titled row of posters, running from edge to edge of the screen. */
@Composable
private fun PosterRow(
    title: String,
    note: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column {
        RowTitle(title, note)
        RowScrolling(start = ScreenEdge, end = 72.dp) {
            LazyRow(
                contentPadding = PaddingValues(start = ScreenEdge - FocusGap, end = ScreenEdge, top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(PosterGap - FocusGap * 2),
                content = content,
            )
        }
    }
}

/** A grey poster shown while a row loads. It can take focus, so the remote moves one row at a time. */
@Composable
private fun PosterPlaceholder(onFocusChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = Modifier.width(PosterWidth + FocusGap * 2)) {
        ArtFrame(
            width = PosterWidth,
            height = PosterHeight,
            onFocusChange = onFocusChange,
            onClick = {},
            modifier = modifier,
        ) {}
        Spacer(Modifier.padding(top = 6.dp).height(36.dp))
    }
}

/** A poster with its title underneath (up to two lines, brighter when focused). */
@Composable
private fun PosterCard(
    title: String,
    poster: String?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(PosterWidth + FocusGap * 2)) {
        ArtFrame(
            width = PosterWidth,
            height = PosterHeight,
            onFocusChange = { focused = it; if (it) onFocus() },
            onClick = onClick,
            modifier = modifier,
        ) {
            // The title, for posters that are missing or still loading.
            Text(
                text = title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                style = WiptvType.metadata.copy(color = WiptvColors.secondary),
            )
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = FocusGap, end = FocusGap, top = 6.dp).height(36.dp),
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                color = if (focused) WiptvColors.text else WiptvColors.secondary,
            ),
        )
    }
}

/** Wide picture for something you've started: the episode still or backdrop, else the poster. */
@Composable
private fun ResumeArt(point: ResumePoint, showProgress: Boolean = true) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            point.image != null -> AsyncImage(
                model = point.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            point.poster != null -> {
                // A soft wash of the poster behind the poster itself.
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current).data(point.poster).size(12, 18).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                    modifier = Modifier.fillMaxSize(),
                )
                AsyncImage(
                    model = point.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterStart).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                )
            }
        }
        if (showProgress && point.progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
            )
            ProgressLine(
                progress = point.progress,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
    }
}

/** "Title · S2 E5", or the title and time left for a film. */
private fun resumeLine(point: ResumePoint, withTitle: Boolean): String =
    listOfNotNull(
        point.title.takeIf { withTitle },
        point.subtitle?.substringBefore(" · ")?.takeIf { it.isNotBlank() } ?: timeLeft(point),
    ).joinToString(" · ")

/** A Continue watching card: wide artwork with its progress, the title and how much is left. */
@Composable
private fun ResumeCard(point: ResumePoint, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(ResumeWidth + FocusGap * 2)) {
        ArtFrame(
            width = ResumeWidth,
            height = ResumeHeight,
            onFocusChange = { focused = it; if (it) onFocus() },
            onClick = onClick,
            modifier = modifier,
        ) { ResumeArt(point) }
        // One line: the title at rest; on the focused card, the hint and time left (the title is in the hero).
        Text(
            text = if (focused) listOfNotNull("OK to resume", timeLeft(point)).joinToString(" · ") else resumeLine(point, withTitle = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = FocusGap, end = FocusGap, top = 6.dp),
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (focused) WiptvColors.text else WiptvColors.secondary,
            ),
        )
    }
}

private val ResumeWidth = 236.dp
private val ResumeHeight = 133.dp

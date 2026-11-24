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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.SectionDim
import dev.wiptv.design.sectionCaption
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
 * Which rows ("shelves") Library shows, and in what order, kept on the TV. The built-in ones
 * are below; a provider category is "cat:MOVIE:<id>" or "cat:SERIES:<id>". Shelves never placed
 * follow in their own order; only [DEFAULT_SHOWN] show until you choose otherwise.
 */
class LibraryPrefs(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("library-shelves", android.content.Context.MODE_PRIVATE)

    fun shown(): Set<String> = prefs.getStringSet("shown", null)?.toSet() ?: DEFAULT_SHOWN

    fun setShown(ids: Set<String>) {
        prefs.edit().putStringSet("shown", ids.toSet()).apply()
    }

    fun order(): List<String> = prefs.getString("order", "").orEmpty().split(',').filter { it.isNotBlank() }

    fun setOrder(ids: List<String>) {
        prefs.edit().putString("order", ids.joinToString(",")).apply()
    }

    companion object {
        val BUILT_IN = listOf(
            Group("hero", "Trending banner"),
            Group("resume", "Continue watching"),
            Group("mylist_films", "My List: films"),
            Group("mylist_shows", "My List: TV shows"),
            Group("channels", "Favourite channels"),
            Group("new_films", "New films on your provider"),
            Group("new_shows", "New TV shows on your provider"),
            Group("trend_films", "Trending films"),
            Group("trend_shows", "Trending TV shows"),
        )
        val DEFAULT_SHOWN = setOf("hero", "resume", "mylist_films", "mylist_shows", "channels")
    }
}

/**
 * Library, the player's home (version 0.3, after the max player), made of the rows you choose
 * (Edit Library at the bottom): the trending banner — what's trending this week on TMDB that
 * your provider has, with Play and More info, Left on Play and Right on More info moving
 * through it, and moving on by itself otherwise — Continue watching, My List, your favourite
 * channels, what's new on your provider, trending films or shows, and any of your provider's
 * categories. Play starts a film (a series opens its page); More info and posters open the
 * details page; a Continue watching card resumes; a channel plays full screen.
 */
@Composable
fun LibraryScreen(
    login: Login,
    store: VodStore,
    header: @Composable () -> Unit,
    onPlay: (IndexedTitle) -> Unit,
    onDetails: (VodKind, Long) -> Unit,
    onResume: (ResumePoint) -> Unit,
    onWatchChannel: (Channel) -> Unit,
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
                (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }.take(20)
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

    // Your rows: the built-in ones and your provider's categories (films, then series).
    val prefs = remember { LibraryPrefs(context) }
    var shownIds by remember { mutableStateOf(prefs.shown()) }
    var order by remember { mutableStateOf(prefs.order()) }
    var editing by remember { mutableStateOf(false) }
    val filmCache = remember(login) { SessionCache.of(login, VodKind.MOVIE.name) }
    val showCache = remember(login) { SessionCache.of(login, VodKind.SERIES.name) }
    var filmCategories by remember { mutableStateOf(filmCache.categories) }
    var showCategories by remember { mutableStateOf(showCache.categories) }
    LaunchedEffect(login) {
        suspend fun load(kind: VodKind): List<Group>? = try {
            Xtream.vodCategories(login, kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (filmCategories == null) load(VodKind.MOVIE)?.let { filmCache.categories = it; filmCategories = it }
        if (showCategories == null) load(VodKind.SERIES)?.let { showCache.categories = it; showCategories = it }
    }
    val allShelves = LibraryPrefs.BUILT_IN +
        filmCategories.orEmpty().map { Group("cat:MOVIE:${it.id}", "Films: ${it.name}") } +
        showCategories.orEmpty().map { Group("cat:SERIES:${it.id}", "TV shows: ${it.name}") }
    val arranged = GroupPrefs.arrange(allShelves, order)
    val shelves = arranged.filter { it.id in shownIds }

    val resume = remember(editing) { store.resumePoints() }
    val myList = remember(editing) { store.favourites() }
    val channels = remember(editing) { FavouritePrefs(context).load() }

    var slide by remember { mutableIntStateOf(0) }
    val shown = picks.orEmpty()
    val current = shown.getOrNull(slide.coerceIn(0, (shown.size - 1).coerceAtLeast(0)))
    val heroOn = shelves.any { it.id == "hero" }
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
    LaunchedEffect(current != null, heroOn) {
        if (placed || current == null || !heroOn) return@LaunchedEffect
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
        if (heroOn && current != null && listState.firstVisibleItemIndex == 0 && shelves.firstOrNull()?.id == "hero") {
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
                    for (shelf in shelves) {
                        when {
                            shelf.id == "hero" -> item(key = "hero") {
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
                            shelf.id == "resume" -> if (resume.isNotEmpty()) item(key = "resume") {
                                ResumeShelf(resume, onResume)
                            }
                            shelf.id == "mylist_films" || shelf.id == "mylist_shows" -> {
                                val kind = if (shelf.id == "mylist_films") VodKind.MOVIE else VodKind.SERIES
                                val list = myList.filter { it.kind == kind }
                                if (list.isNotEmpty()) item(key = shelf.id) {
                                    PosterShelf(shelf.name, list.map { ShelfTitle(it.key, it.kind, it.id, it.name, it.poster, it.backdrop) }, onDetails)
                                }
                            }
                            shelf.id == "channels" -> if (channels.isNotEmpty()) item(key = "channels") {
                                ChannelShelf(channels, onWatchChannel)
                            }
                            shelf.id == "new_films" || shelf.id == "new_shows" -> item(key = shelf.id) {
                                val kind = if (shelf.id == "new_films") VodKind.MOVIE else VodKind.SERIES
                                var titles by remember { mutableStateOf<List<IndexedTitle>?>(null) }
                                LaunchedEffect(kind) { titles = withContext(Dispatchers.IO) { CatalogIndex.newest(context, kind) } }
                                PosterShelf(shelf.name, titles?.map { ShelfTitle(it.kind.name + it.id, it.kind, it.id, it.name, it.poster, null) }, onDetails)
                            }
                            shelf.id == "trend_films" || shelf.id == "trend_shows" -> {
                                val kind = if (shelf.id == "trend_films") VodKind.MOVIE else VodKind.SERIES
                                item(key = shelf.id) {
                                    PosterShelf(
                                        shelf.name,
                                        picks?.filter { it.title.kind == kind }?.map {
                                            ShelfTitle(it.title.kind.name + it.title.id, kind, it.title.id, it.trending.name, it.trending.poster, it.trending.backdrop)
                                        },
                                        onDetails,
                                    )
                                }
                            }
                            shelf.id.startsWith("cat:") -> item(key = shelf.id) {
                                val kind = if (shelf.id.startsWith("cat:MOVIE:")) VodKind.MOVIE else VodKind.SERIES
                                val categoryId = shelf.id.substringAfter(':').substringAfter(':')
                                val cache = if (kind == VodKind.MOVIE) filmCache else showCache
                                LaunchedEffect(shelf.id) {
                                    if (cache.items[categoryId] != null) return@LaunchedEffect
                                    try {
                                        cache.items[categoryId] = Xtream.vodItems(login, kind, categoryId)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        cache.items[categoryId] = emptyList()
                                    }
                                }
                                PosterShelf(
                                    shelf.name,
                                    cache.items[categoryId]?.map { ShelfTitle(it.key, it.kind, it.id, it.name, it.poster, it.backdrop) },
                                    onDetails,
                                )
                            }
                        }
                    }
                    // Choosing the rows.
                    item(key = "edit") {
                        Box(Modifier.padding(start = Edge, top = 6.dp)) {
                            PlayerButton(
                                text = "Edit Library",
                                onClick = { editing = true },
                                modifier = if (!heroOn || current == null) Modifier.focusRequester(play) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        GroupEditor(
            groups = arranged,
            hidden = arranged.map { it.id }.toSet() - shownIds,
            title = "Edit Library",
            onToggle = { shelf ->
                shownIds = if (shelf.id in shownIds) shownIds - shelf.id else shownIds + shelf.id
                prefs.setShown(shownIds)
            },
            onSetAllHidden = { hide ->
                shownIds = if (hide) emptySet() else arranged.map { it.id }.toSet()
                prefs.setShown(shownIds)
            },
            onMove = { shelf, step ->
                val ids = arranged.map { it.id }.toMutableList()
                val from = ids.indexOf(shelf.id)
                val to = from + step
                if (from >= 0 && to in ids.indices) {
                    ids.add(to, ids.removeAt(from))
                    order = ids
                    prefs.setOrder(ids)
                }
            },
            onMoveToTop = { shelf ->
                val ids = arranged.map { it.id }.toMutableList()
                if (ids.remove(shelf.id)) {
                    ids.add(0, shelf.id)
                    order = ids
                    prefs.setOrder(ids)
                }
            },
            onDone = { editing = false },
        )
    }
}

/** A title in a Library row, whichever list it came from. */
private data class ShelfTitle(val key: String, val kind: VodKind, val id: Long, val name: String, val poster: String?, val backdrop: String?)

@Composable
private fun ResumeShelf(resume: List<ResumePoint>, onResume: (ResumePoint) -> Unit) {
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

/** A row of posters (null while it loads: placeholders, so the remote still moves one row at a time). */
@Composable
private fun PosterShelf(title: String, titles: List<ShelfTitle>?, onDetails: (VodKind, Long) -> Unit) {
    if (titles != null && titles.isEmpty()) return
    val wideCard = rememberWideCard()
    val rowStart = rememberRowStart()
    SectionRow(title = title, edge = Edge, rowModifier = rowStart.rowModifier(titles?.firstOrNull()?.key)) {
        if (titles == null) {
            items(6) { dev.wiptv.design.PosterPlaceholder(onFocusChange = {}) }
        } else {
            items(titles, key = { it.key }) { t ->
                PosterCard(
                    name = t.name,
                    posterUrl = t.poster,
                    backdropUrl = t.backdrop,
                    onClick = { onDetails(t.kind, t.id) },
                    wide = wideCard.wide == t.key,
                    onFocus = { wideCard.onFocus(t.key, it) },
                    modifier = rowStart.card(t.key),
                )
            }
        }
    }
}

/** Your favourite channels: the logo on a soft panel, the name underneath. OK plays it full screen. */
@Composable
private fun ChannelShelf(channels: List<Channel>, onWatch: (Channel) -> Unit) {
    val rowStart = rememberRowStart()
    SectionRow(title = "Favourite channels", edge = Edge, rowModifier = rowStart.rowModifier(channels.firstOrNull()?.id)) {
        items(channels, key = { it.id }) { channel ->
            val label = remember(channel.name) { ChannelLabels.of(channel.name) }
            Column(modifier = Modifier.width(192.dp + ArtFocusGap * 2)) {
                ArtFrame(
                    width = 192.dp,
                    height = 108.dp,
                    onFocusChange = {},
                    onClick = { onWatch(channel) },
                    modifier = rowStart.card(channel.id),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
                    if (channel.logo != null) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = label.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
                        )
                    } else {
                        Text(text = label.name, modifier = Modifier.align(Alignment.Center).padding(10.dp), style = WiptvType.metadata)
                    }
                    SectionDim()
                }
                Text(
                    text = label.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ArtFocusGap, end = ArtFocusGap, top = 6.dp),
                    style = WiptvType.caption.copy(color = sectionCaption()),
                )
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

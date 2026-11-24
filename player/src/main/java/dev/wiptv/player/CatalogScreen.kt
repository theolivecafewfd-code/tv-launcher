package dev.wiptv.player

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.PosterHeight
import dev.wiptv.design.PosterWidth
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The side margin. */
private val ScreenEdge = 32.dp

/** How the posters in a category are ordered. */
private enum class CatalogSort(val label: String, val note: String?) {
    PROVIDER("Provider's order", null),
    NEWEST("Recently added", "newest first"),
    NAME("Name A–Z", "A–Z"),
    YEAR("Year, newest first", "by year"),
    RATING("Score, highest first", "by score"),
}

private fun List<VodItem>.sortedFor(sort: CatalogSort): List<VodItem> = when (sort) {
    CatalogSort.PROVIDER -> this
    // Providers number titles in the order they add them.
    CatalogSort.NEWEST -> sortedByDescending { it.id }
    CatalogSort.NAME -> sortedBy { DisplayTitles.withoutYear(it.name, it.year).lowercase() }
    CatalogSort.YEAR -> sortedByDescending { it.year?.toIntOrNull() ?: 0 }
    CatalogSort.RATING -> sortedByDescending { it.rating ?: -1.0 }
}

/** The category and sort last chosen for films and for series, kept on the device. */
private class CatalogChoice(context: Context, kind: VodKind) {
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
    private val prefix = kind.name

    fun category(): String? = prefs.getString("$prefix.category", null)
    fun setCategory(id: String) = prefs.edit().putString("$prefix.category", id).apply()

    fun sort(): CatalogSort = runCatching { CatalogSort.valueOf(prefs.getString("$prefix.sort", null).orEmpty()) }
        .getOrDefault(CatalogSort.PROVIDER)
    fun setSort(sort: CatalogSort) = prefs.edit().putString("$prefix.sort", sort.name).apply()
}

/**
 * Films or series as a grid of posters, one category at a time. "Filter and sort" at the top
 * right picks the category and the order (and hides or reorders categories with Edit categories).
 * OK on a poster opens its details. Continue watching and My List are in Library.
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
    /** A Continue watching key the launcher asked to resume: offers the same choice as Library. */
    openResume: String? = null,
    /** Called when that title is closed, so the player can hand back to the launcher. */
    onOpenFinished: () -> Unit = {},
    /** A surprise pick (the header's dice): its details page opens, and [onSurpriseShown] clears it. */
    surpriseId: Long? = null,
    onSurpriseShown: () -> Unit = {},
    /** That surprise page was closed: the player goes back to where the dice was pressed. */
    onSurpriseClosed: () -> Unit = {},
) {
    val context = LocalContext.current
    val noun = if (kind == VodKind.MOVIE) "films" else "series"
    val section = if (kind == VodKind.MOVIE) "Movies" else "TV Shows"
    // Kept between tabs while the player runs (SessionCache), so coming back is instant.
    val cache = remember(kind, login) { SessionCache.of(login, kind.name) }
    var categories by remember(kind) { mutableStateOf(cache.categories) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val itemsByCategory = cache.items
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var playing by remember { mutableStateOf<ResumePoint?>(null) }
    var playingNext by remember { mutableStateOf<NextUp?>(null) }
    // The Continue / Episodes choice for something opened from the launcher's Continue watching.
    var choosing by remember { mutableStateOf<ResumePoint?>(null) }
    // The episode a series page opened from that choice lands on.
    var focusEpisode by remember { mutableStateOf<String?>(null) }

    val prefs = remember(kind) { GroupPrefs(context, if (kind == VodKind.MOVIE) "movie-groups" else "series-groups") }
    var hidden by remember(kind) { mutableStateOf(prefs.hidden()) }
    var order by remember(kind) { mutableStateOf(prefs.order()) }
    var editing by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    val arranged = categories?.let { GroupPrefs.arrange(it, order) }
    val shown = arranged?.filter { it.id !in hidden }

    val choice = remember(kind) { CatalogChoice(context, kind) }
    var chosenId by remember(kind) { mutableStateOf(choice.category()) }
    var sort by remember(kind) { mutableStateOf(choice.sort()) }
    // The category on screen: the one you chose, or the first showing if that's gone or hidden.
    val category = shown?.let { list -> list.firstOrNull { it.id == chosenId } ?: list.firstOrNull() }
    // The category whose titles couldn't be loaded (it says so, with Try again).
    var failedId by remember(kind) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val loaded = category?.let { itemsByCategory[it.id] }
    val posters = remember(loaded, sort) { loaded?.sortedFor(sort) }

    /** The catalogue entry a Continue watching key belongs to, from what's already loaded. */
    fun catalogueItemFor(point: ResumePoint): VodItem? {
        val id = (if (kind == VodKind.MOVIE) point.key.removePrefix("movie:").toLongOrNull() else point.seriesId) ?: return null
        return itemsByCategory.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
    }

    /** Just enough of a catalogue entry, from the saved point, to open its page. */
    fun resumeItemFor(point: ResumePoint): VodItem? {
        val id = (if (kind == VodKind.MOVIE) point.key.removePrefix("movie:").toLongOrNull() else point.seriesId) ?: return null
        return VodItem(kind = kind, id = id, name = point.title, poster = point.poster, categoryId = "", rating = null, extension = null)
    }

    // Opened from the launcher: once that film or series page is closed, go back there.
    var openedFromLauncher by remember { mutableStateOf(false) }
    // Opened by the header's dice: once that page is closed, back to where the dice was pressed.
    var openedBySurprise by remember { mutableStateOf(false) }
    // Resuming from Continue watching (the launcher's): an episode then looks up its series so the
    // next episode follows, and Play Next shows, as from the series' page.
    val resumeScope = rememberCoroutineScope()
    fun resumePlaying(point: ResumePoint) {
        playingNext = null
        playing = point
        val seriesId = point.seriesId ?: return
        resumeScope.launch {
            val details = try {
                Xtream.seriesDetails(login, VodItem(VodKind.SERIES, seriesId, point.title, point.poster, "", null, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch // no next episode this time; the episode still plays
            }
            // Still the same episode playing (not closed or changed meanwhile).
            if (playing?.key == point.key) playingNext = nextUpAfter(login, store, point, details) {}
        }
    }
    LaunchedEffect(openResume) {
        val key = openResume ?: return@LaunchedEffect
        val point = store.resumePoint(key) ?: return@LaunchedEffect
        openedFromLauncher = true
        choosing = point
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
            extension = details.extension,
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
        openedBySurprise = true
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
        // Already loaded this session (and not "try again"): nothing to fetch.
        if (attempt == 0 && categories != null) return@LaunchedEffect
        error = null
        try {
            categories = Xtream.vodCategories(login, kind).also { cache.categories = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load $noun."
        }
    }

    // The category's titles, once (kept for the session).
    LaunchedEffect(kind, category?.id, retry) {
        val id = category?.id ?: return@LaunchedEffect
        if (itemsByCategory[id] != null) return@LaunchedEffect
        failedId = null
        try {
            itemsByCategory[id] = Xtream.vodItems(login, kind, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedId = id
        }
    }

    // Focus goes to the first poster when the screen first fills, and after choosing a category
    // or an order in Filter and sort.
    val firstPoster = remember { FocusRequester() }
    var wantFirst by remember(kind) { mutableIntStateOf(1) }
    LaunchedEffect(wantFirst, posters) {
        if (wantFirst == 0 || posters.isNullOrEmpty()) return@LaunchedEffect
        delay(100)
        repeat(20) {
            if (runCatching { firstPoster.requestFocus() }.isSuccess) {
                wantFirst = 0
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WiptvColors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Box(modifier = Modifier.padding(horizontal = ScreenEdge)) { tabs() }

            // "Movies: Comedy  312 films", and Filter and sort on the right.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = ScreenEdge, end = ScreenEdge, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = category?.let { "$section: ${it.name}" } ?: section,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                    )
                    val count = posters?.let { "${it.size} $noun" }
                    val note = listOfNotNull(count, sort.note).joinToString(" · ")
                    if (note.isNotEmpty()) {
                        Text(
                            text = note,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                            style = TextStyle(fontSize = 14.sp, color = WiptvColors.quiet),
                        )
                    }
                }
                if (categories != null) FilterButton(onClick = { filtering = true })
            }

            val message = when {
                error != null -> error
                categories == null -> "Loading $noun…"
                shown.isNullOrEmpty() -> "Every category is hidden. Show some with Filter and sort, then Edit categories."
                category != null && failedId == category.id -> "Couldn't load ${category.name}."
                posters == null -> "Loading ${category?.name ?: noun}…"
                posters.isEmpty() -> "Nothing in ${category?.name ?: "this category"} yet."
                else -> null
            }
            if (message != null) {
                Column(modifier = Modifier.padding(horizontal = ScreenEdge, vertical = 12.dp)) {
                    val isError = error != null || (category != null && failedId == category.id)
                    Text(text = message, style = WiptvType.metadata.copy(color = if (isError) WiptvColors.warning else WiptvColors.quiet))
                    if (isError) {
                        Spacer(Modifier.height(10.dp))
                        PlayerButton(text = "Try again", onClick = { if (error != null) attempt++ else retry++ })
                    }
                }
            } else if (posters != null) {
                // A fresh grid, at the top, for each category and order.
                val grid = remember(kind, category?.id, sort) { LazyGridState() }
                // The focused row moves to the top, one row per press.
                PivotScrolling(pivot = 6.dp, alwaysAlign = true) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        state = grid,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = ScreenEdge - ArtFocusGap,
                            end = ScreenEdge - ArtFocusGap,
                            top = 6.dp,
                            bottom = 40.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(posters, key = { _, item -> item.key }) { index, item ->
                            GridPoster(
                                item = item,
                                onClick = { detail = item },
                                modifier = if (index == 0) Modifier.focusRequester(firstPoster) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }

    if (filtering && shown != null) {
        FilterPanel(
            categories = shown,
            current = category?.id,
            count = { itemsByCategory[it]?.size },
            sort = sort,
            onSort = {
                filtering = false
                sort = it
                choice.setSort(it)
                wantFirst++
            },
            onCategory = {
                filtering = false
                chosenId = it.id
                choice.setCategory(it.id)
                wantFirst++
            },
            onEdit = {
                filtering = false
                editing = true
            },
            onClose = { filtering = false },
        )
    }

    detail?.let { item ->
        // A fresh page for each title (More like this swaps one for another).
        key(item.key) {
            VodDetailScreen(
                item = item,
                login = login,
                store = store,
                onPlay = { point, next ->
                    playing = point
                    playingNext = next
                },
                onListChanged = {},
                focusEpisode = focusEpisode,
                onOpenOther = {
                    focusEpisode = null
                    detail = it
                },
                onClose = {
                    detail = null
                    focusEpisode = null
                    if (openedFromLauncher) onOpenFinished()
                    if (openedBySurprise) {
                        openedBySurprise = false
                        onSurpriseClosed()
                    }
                },
            )
        }
    }

    choosing?.let { point ->
        ResumeChoice(
            point = point,
            onContinue = {
                choosing = null
                resumePlaying(point)
            },
            onStartOver = {
                choosing = null
                resumePlaying(point.copy(position = 0))
            },
            onEpisodes = {
                choosing = null
                // The series' page, on the season and episode you were watching.
                val item = catalogueItemFor(point) ?: resumeItemFor(point)
                if (item != null) {
                    focusEpisode = point.key
                    detail = item
                } else if (openedFromLauncher) onOpenFinished()
            },
            onClose = {
                choosing = null
                if (openedFromLauncher) onOpenFinished()
            },
        )
    }

    playing?.let { point ->
        key(point.key) {
            VodPlayerScreen(
                // A saved point has no address of its own: it's built from the current login.
                start = point.withUrl(login),
                store = store,
                // The Play Next card in the last minute of an episode.
                upNext = remember(point.key, playingNext) { playingNext?.peek() },
                onFinished = {
                    // Straight on to the next episode, if there is one.
                    val next = playingNext?.advance()
                    playing = next
                    // A film opened from the launcher: back there when it ends.
                    if (next == null && detail == null && openedFromLauncher) onOpenFinished()
                },
                onClose = {
                    playing = null
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

/** The "☰ Filter and sort" pill at the top right. */
@Composable
private fun FilterButton(onClick: () -> Unit) {
    val shape = WiptvShapes.pill
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiptvColors.surface,
            contentColor = WiptvColors.text,
            focusedContainerColor = WiptvColors.focus,
            focusedContentColor = WiptvColors.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = focusOutline(shape),
    ) {
        Text(
            text = "☰  Filter and sort",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/**
 * A poster with its name underneath. Focus is the bright outline round the poster; the focused
 * one's name turns bold and gains its year.
 */
@Composable
private fun GridPoster(item: VodItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val title = remember(item.name, item.year) { DisplayTitles.withoutYear(item.name, item.year) }
    Column(modifier = Modifier.width(PosterWidth + ArtFocusGap * 2)) {
        ArtFrame(
            width = PosterWidth,
            height = PosterHeight,
            onFocusChange = { focused = it },
            onClick = onClick,
            modifier = modifier,
        ) {
            // The name, for posters that are missing or still loading.
            Text(
                text = title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                style = WiptvType.metadata.copy(color = WiptvColors.secondary),
            )
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val year = item.year?.takeIf { it.isNotBlank() }
        Text(
            text = if (focused && year != null) "$title · $year" else title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = ArtFocusGap, end = ArtFocusGap, top = 6.dp),
            style = WiptvType.caption.copy(
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                color = if (focused) WiptvColors.text else WiptvColors.secondary,
            ),
        )
    }
}

/**
 * Filter and sort: a panel down the right with the orders, then every showing category (with how
 * many titles, once loaded), then Edit categories. It opens on the category you're in. OK
 * chooses and closes it; Back closes it without changing anything.
 */
@Composable
private fun FilterPanel(
    categories: List<Group>,
    current: String?,
    count: (String) -> Int?,
    sort: CatalogSort,
    onSort: (CatalogSort) -> Unit,
    onCategory: (Group) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    val currentIndex = categories.indexOfFirst { it.id == current }
    // List positions: the sort caption, the orders, the category caption, then the categories.
    val firstCategory = 2 + CatalogSort.entries.size
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = if (currentIndex >= 0) (firstCategory + currentIndex - 2).coerceAtLeast(0) else 0,
    )
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { start.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.3f)))
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight()
                    .background(WiptvColors.surface)
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp),
            ) {
                Text(
                    text = "Filter and sort",
                    modifier = Modifier.padding(start = 12.dp),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                )
                Text(
                    text = "OK chooses · Back closes",
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 6.dp),
                    style = WiptvType.hint,
                )
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "sortCaption") { PanelCaption("SORT BY") }
                    items(CatalogSort.entries, key = { "sort:" + it.name }) { option ->
                        val selected = option == sort
                        MenuItem(
                            onClick = { onSort(option) },
                            selected = selected,
                            trailing = if (selected) "✓" else null,
                            modifier = if (currentIndex < 0 && selected) Modifier.focusRequester(start) else Modifier,
                        ) {
                            Text(text = option.label, style = TextStyle(fontSize = 14.sp))
                        }
                    }
                    item(key = "categoryCaption") { PanelCaption("CATEGORY") }
                    items(categories, key = { "cat:" + it.id }) { group ->
                        val selected = group.id == current
                        MenuItem(
                            onClick = { onCategory(group) },
                            selected = selected,
                            trailing = count(group.id)?.toString(),
                            modifier = if (selected) Modifier.focusRequester(start) else Modifier,
                        ) {
                            Text(
                                text = group.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                    item(key = "edit") {
                        Spacer(Modifier.height(8.dp))
                        MenuItem(onClick = onEdit, selected = false) {
                            Text(text = "Edit categories", style = TextStyle(fontSize = 13.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelCaption(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        style = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp, color = WiptvColors.quiet),
    )
}

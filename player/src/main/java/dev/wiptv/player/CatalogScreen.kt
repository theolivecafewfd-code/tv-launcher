package dev.wiptv.player

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What the big area at the top describes: the poster you're on. */
private data class Hero(val title: String, val meta: String, val plot: String?, val backdrop: String?)

private fun heroOf(item: VodItem) = Hero(
    title = item.name,
    meta = listOfNotNull(item.year, item.rating?.let { "★ %.1f".format(it) }).joinToString(" · "),
    plot = item.plot,
    backdrop = item.backdrop,
)

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
) {
    val context = LocalContext.current
    val noun = if (kind == VodKind.MOVIE) "films" else "series"
    var categories by remember(kind) { mutableStateOf<List<Group>?>(null) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val itemsByCategory = remember(kind) { mutableStateMapOf<String, List<VodItem>>() }
    var hero by remember(kind) { mutableStateOf<Hero?>(null) }
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

    val firstPoster = remember { FocusRequester() }
    val listState = rememberLazyListState()

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

    Box(modifier = Modifier.fillMaxSize().background(Palette.background)) {
        // The highlighted title's backdrop, faded into the background, top right.
        hero?.backdrop?.let { backdrop ->
            Box(modifier = Modifier.align(Alignment.TopEnd).size(width = 560.dp, height = 315.dp)) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Palette.background, Color.Transparent))),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))),
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { tabs() }
                UpdatePill(update)
            }

            // What the highlighted poster is.
            Column(modifier = Modifier.height(110.dp).width(520.dp).padding(top = 10.dp)) {
                val h = hero
                if (h != null) {
                    Text(
                        text = h.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                    )
                    if (h.meta.isNotBlank()) {
                        Text(text = h.meta, style = TextStyle(fontSize = 12.sp, color = Palette.quiet))
                    }
                    if (!h.plot.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = h.plot,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                        )
                    }
                } else {
                    Text(
                        text = when {
                            error != null -> error!!
                            categories == null -> "Loading $noun…"
                            else -> ""
                        },
                        style = TextStyle(fontSize = 14.sp, color = if (error != null) Palette.warn else Palette.quiet),
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        PlayerButton(text = "Try again", onClick = { attempt++ })
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                var firstAssigned = false
                fun firstModifier(): Modifier =
                    if (!firstAssigned) Modifier.focusRequester(firstPoster).also { firstAssigned = true } else Modifier

                if (resume.isNotEmpty()) {
                    val first = firstModifier()
                    item(key = "resume") {
                        PosterRow(title = "Continue watching") {
                            items(resume, key = { it.key }) { point ->
                                PosterCard(
                                    title = point.title,
                                    caption = point.subtitle ?: point.title,
                                    poster = point.poster,
                                    progress = point.progress,
                                    onFocus = { hero = Hero(point.title, point.subtitle ?: "Resume at ${clockOf(point.position)}", null, null) },
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
                                    caption = item.name,
                                    poster = item.poster,
                                    onFocus = { hero = heroOf(item) },
                                    onClick = { detail = item },
                                    modifier = if (item == myList.first()) first else Modifier,
                                )
                            }
                        }
                    }
                }

                val firstCategoryModifier = firstModifier()
                shown.orEmpty().forEachIndexed { index, category ->
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
                        PosterRow(title = category.name, note = if (rowItems == null) "Loading…" else if (rowItems.isEmpty()) "Nothing here" else null) {
                            items(rowItems.orEmpty(), key = { it.key }) { item ->
                                PosterCard(
                                    title = item.name,
                                    caption = item.name,
                                    poster = item.poster,
                                    onFocus = { hero = heroOf(item) },
                                    onClick = { detail = item },
                                    modifier = if (index == 0 && item == rowItems?.firstOrNull()) firstCategoryModifier else Modifier,
                                )
                            }
                        }
                    }
                }

                if (categories != null) {
                    item(key = "edit") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                            PlayerButton(text = "Edit categories", onClick = { editing = true })
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
            onClose = { detail = null },
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
                },
                onClose = {
                    playing = null
                    refresh++
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

/** A titled row of posters. */
@Composable
private fun PosterRow(
    title: String,
    note: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
            )
            if (note != null) Text(text = note, style = TextStyle(fontSize = 11.sp, color = Palette.quiet))
        }
        LazyRow(
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** A poster with its title underneath, and a progress bar for things you've started. */
@Composable
private fun PosterCard(
    title: String,
    caption: String,
    poster: String?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(width = 92.dp, height = 138.dp)
                .onFocusChanged { if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.tile,
                focusedContainerColor = Palette.tileFocused,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(shape)) {
                // Title as a fallback, under the picture, for posters that don't load.
                Text(
                    text = title,
                    maxLines = 4,
                    modifier = Modifier.align(Alignment.Center).padding(6.dp),
                    style = TextStyle(fontSize = 10.sp, color = Palette.quiet),
                )
                if (poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (progress != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Palette.glow),
                        )
                    }
                }
            }
        }
        Text(
            text = caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 10.sp, color = Palette.quiet),
        )
    }
}

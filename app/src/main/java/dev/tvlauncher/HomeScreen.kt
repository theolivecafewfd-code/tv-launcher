package dev.tvlauncher

import dev.wiptv.update.UpdateState
import dev.wiptv.update.Updater
import dev.wiptv.update.label
import dev.wiptv.update.loud
import androidx.compose.foundation.BorderStroke
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.GlassPill
import dev.wiptv.design.RowScrolling
import dev.wiptv.design.WiptvShapes
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import dev.wiptv.design.glass
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Your name for the greeting. Moves to a settings screen in a later phase. */
private const val OWNER_NAME = "Wayne"

// Sizes are in dp on the TV's 960 x 540 layout grid (a 1080p TV at the standard TV density).
// Colours, type and shapes come from the shared WIPTV tokens (the design module), as in the player.
/** The left and right margin, the same as the player's. */
internal val Edge = 32.dp

/** A row's heading: the player's row title. */
internal val RowHeading = WiptvType.control.copy(fontSize = 17.sp, color = WiptvColors.text)


@Composable
fun HomeScreen(refreshKey: Int, onLaunch: (TvApp) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var weather by remember { mutableStateOf<WeatherNow?>(null) }

    // Weather for the header, refreshed every 30 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            Weather.fetch()?.let { weather = it }
            delay(30 * 60_000L)
        }
    }

    // Which apps you've hidden and the order you've chosen, saved on the device.
    val dockPrefs = remember { DockPrefs(context) }
    var hidden by remember { mutableStateOf(dockPrefs.hidden()) }
    var order by remember { mutableStateOf(dockPrefs.order()) }
    val dockApps = remember(apps, hidden, order) { DockPrefs.arrange(apps, hidden, order) }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var editing by remember { mutableStateOf(false) }
    // After a menu action, which tile the remote's focus should land on.
    var focusKey by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    fun focusOn(key: String?) {
        focusKey = key
        focusTick++
    }

    fun saveOrder(newOrder: List<TvApp>) {
        order = newOrder.map { it.packageName }
        dockPrefs.setOrder(order)
    }
    fun setHidden(newHidden: Set<String>) {
        hidden = newHidden
        dockPrefs.setHidden(newHidden)
    }

    LaunchedEffect(refreshKey) {
        apps = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    // Back home: ask again about titles the IPTV list didn't have (it may have filled in since).
    LaunchedEffect(refreshKey) { IptvBadges.forgetMisses() }

    // Continue watching: what you've started in WIPTV Player, refreshed each time you come home.
    var continueItems by remember { mutableStateOf<List<IptvResume>>(emptyList()) }
    LaunchedEffect(refreshKey) { continueItems = Iptv.continueWatching(context) }
    val continueRow = remember { FocusRequester() }

    // TV Shows or Movies, and the rows for it from TMDB, limited to your services.
    var mediaType by remember { mutableStateOf(MediaType.TV) }
    var rows by remember { mutableStateOf<List<ContentRow>>(emptyList()) }
    var rowsFailed by remember { mutableStateOf(false) }
    var featuredIndex by remember { mutableIntStateOf(0) }
    // One focus target per row, so up and down move exactly one row.
    val appsRow = remember { FocusRequester() }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun rowRequester(key: String) = rowFocus.getOrPut(key) { FocusRequester() }
    val featured = rows.firstOrNull { it.key == "top" }?.items.orEmpty()
    LaunchedEffect(mediaType, refreshKey) {
        if (!Catalog.hasKey) return@LaunchedEffect
        rowsFailed = false
        val loaded = try {
            Catalog.rows(mediaType)
        } catch (e: Exception) {
            rowsFailed = true
            emptyList()
        }
        // A refresh on returning home keeps the current rows if the new load failed.
        if (loaded.isNotEmpty() || rows.isEmpty() || rows.firstOrNull()?.items?.firstOrNull()?.type != mediaType) {
            if (rows.firstOrNull()?.items?.firstOrNull()?.type != mediaType) featuredIndex = 0
            rows = loaded
        }
    }
    // Recommended for you loads on its own, after the other rows. Until it arrives its row shows
    // grey placeholder posters that can take focus, so Down from the row above isn't stuck.
    var recommendedRow by remember { mutableStateOf<ContentRow?>(null) }
    var recommendedLoading by remember { mutableStateOf(false) }
    LaunchedEffect(mediaType, refreshKey) {
        if (!Catalog.hasKey) return@LaunchedEffect
        val haveThisType = recommendedRow?.items?.firstOrNull()?.type == mediaType
        if (!haveThisType) recommendedRow = null
        recommendedLoading = !haveThisType
        val loaded = try {
            Catalog.recommendedRow(mediaType, History(context).entries())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // A failed refresh keeps the row you already have.
        if (loaded != null && loaded.items.isNotEmpty()) recommendedRow = loaded
        recommendedLoading = false
    }
    val titleScope = rememberCoroutineScope()
    // OK on a title opens its details; holding OK plays it straight away.
    var detailFor by remember { mutableStateOf<Title?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    // Surprise Me: a random well-rated pick, shown on its details page with "Another one".
    var surpriseMode by remember { mutableStateOf(false) }
    fun surprise() {
        titleScope.launch {
            val pick = runCatching { Catalog.surprise(mediaType, Library(context).watched()) }.getOrNull()
            if (pick == null) {
                Toast.makeText(context, "Couldn't find a surprise right now", Toast.LENGTH_SHORT).show()
            } else {
                surpriseMode = true
                detailFor = pick
            }
        }
    }
    val openTitle: (Title) -> Unit = { detailFor = it }
    fun watch(title: Title, only: Source? = null) {
        titleScope.launch {
            // Holding OK plays in the first place it's on: your IPTV provider first, if it has it.
            if (only == null) {
                val iptv = Iptv.match(context, title)
                if (iptv != null && Iptv.open(context, iptv)) {
                    History(context).add(title) // so Recommended learns from it, as with Netflix and Prime
                    return@launch
                }
            }
            val message = TitleOpener.open(context, title, apps, only)
            if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val playTitle: (Title) -> Unit = { watch(it) }

    // My List, shown as a row once you've added something.
    val library = remember { Library(context) }
    var myList by remember { mutableStateOf(library.myList()) }
    val allRows = remember(rows, myList, mediaType, recommendedRow, recommendedLoading) {
        val mine = myList.filter { it.type == mediaType }
        val withList = if (mine.isEmpty()) rows else {
            val top = rows.filter { it.key == "top" }
            top + ContentRow("mylist", "My List", "", ranked = false, items = mine) + rows.filter { it.key != "top" }
        }
        val recommended = recommendedRow?.takeIf { it.items.firstOrNull()?.type == mediaType }
        when {
            recommended != null -> withList + recommended
            // An empty row while it loads: PosterRow shows placeholders for it.
            recommendedLoading && withList.isNotEmpty() -> withList + ContentRow("recommended", "Recommended for you", "", ranked = false, items = emptyList())
            else -> withList
        }
    }

    // Check GitHub for a newer build when the screen opens, then at most every 30 minutes.
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var lastUpdateCheck by remember { mutableLongStateOf(0L) }
    val checkNow: () -> Unit = {
        scope.launch {
            update = UpdateState.Checking
            lastUpdateCheck = SystemClock.elapsedRealtime()
            update = Updater.check(context, BuildConfig.UPDATE_REPO)
        }
    }
    LaunchedEffect(refreshKey) {
        // Back on screen while still "installing" means the install was cancelled: check again.
        if (update is UpdateState.Installing) lastUpdateCheck = 0L
        val busy = update is UpdateState.Downloading || update is UpdateState.Checking && lastUpdateCheck != 0L
        val due = lastUpdateCheck == 0L || SystemClock.elapsedRealtime() - lastUpdateCheck > 30 * 60_000L
        if (due && !busy) checkNow()
    }
    val onUpdateClick: () -> Unit = onUpdateClick@{
        val target = when (val current = update) {
            is UpdateState.Available -> current
            is UpdateState.Failed -> current.retry
            UpdateState.UpToDate, is UpdateState.CheckFailed, UpdateState.Installing -> {
                checkNow()
                return@onUpdateClick
            }
            else -> return@onUpdateClick
        }
        if (!Updater.canInstall(context)) {
            Updater.openInstallPermissionSettings(context)
            return@onUpdateClick
        }
        scope.launch {
            update = UpdateState.Downloading(0)
            try {
                Updater.downloadAndInstall(context, target) { app, percent ->
                    update = UpdateState.Downloading(percent, app)
                }
                update = UpdateState.Installing
            } catch (e: Exception) {
                update = UpdateState.Failed(e.message ?: "download failed", target)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(15_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .launcherBackground(),
        ) {
            // While choosing apps, the editor replaces the home screen so the remote can't
            // wander onto the tiles underneath. Everything scrolls as one list: header, the
            // featured title and your apps first, then the rows below.
            // The featured title's artwork, large and 16:9 like the player's, top right. It scrolls
            // up with the top of the list (the apps and rows pass over its faded lower edge).
            if (!editing) {
                val shownTitle = featured.getOrNull(featuredIndex.coerceIn(0, (featured.size - 1).coerceAtLeast(0)))
                // The text area on the left (the featured title runs to about 44% across the
                // picture) stays dark enough for bright text, then fades so the artwork shows.
                CinematicBackdrop(
                    backdrop = shownTitle?.backdropUrl,
                    poster = shownTitle?.posterUrl,
                    leftFade = 0.78f,
                    textScrim = 0.46f,
                    textScrimAlpha = 0.88f,
                    fadeToTransparent = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(width = 780.dp, height = 439.dp)
                        .graphicsLayer {
                            translationY = if (listState.firstVisibleItemIndex == 0) {
                                -listState.firstVisibleItemScrollOffset.toFloat()
                            } else {
                                -2000f // scrolled away
                            }
                        },
                )
            }
            if (!editing) PivotScrolling { LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // The top of the screen, like the player's Movies and Series: the featured title's
                // artwork large on the right, fading into the dark behind the header and its text.
                item(key = "top") {
                    Box(Modifier.fillMaxWidth()) {
                        // Going up to anything here scrolls right to the top, header included
                        // (see PivotScrolling), in one movement.
                        Column(Modifier.padding(top = 20.dp)) {
                            Header(now = now, weather = weather, update = update, onUpdateClick = onUpdateClick)
                            Box(Modifier.padding(top = 12.dp, bottom = 14.dp)) {
                                TypeSwitch(selected = mediaType, onSelect = { mediaType = it }, onSearch = { searchOpen = true }, onSurprise = { surprise() })
                            }
                            when {
                                !Catalog.hasKey -> RowMessage("Add your TMDB key to GitHub secrets as TMDB_API_KEY to see shows and films here.")
                                featured.isNotEmpty() -> Featured(
                                    titles = featured,
                                    index = featuredIndex.coerceIn(0, featured.size - 1),
                                    onIndexChange = { featuredIndex = it },
                                    onOpen = openTitle,
                                    onPlay = playTitle,
                                )
                                rowsFailed -> RowMessage("Couldn't reach TMDB. Check the internet connection.")
                                else -> RowMessage("Loading ${mediaType.label.lowercase()}…")
                            }
                        }
                    }
                }
                item(key = "apps") {
                    Column(Modifier.padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Edge, end = Edge, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(text = "Your apps", style = RowHeading)
                        }
                        if (apps.isEmpty()) {
                            RowMessage("Loading apps…")
                        } else {
                            AppDock(
                                apps = dockApps,
                                onLaunch = onLaunch,
                                onLongPress = { menuFor = it },
                                onEdit = { editing = true },
                                focusKey = focusKey,
                                focusTick = focusTick,
                                rowRequester = appsRow,
                                down = if (continueItems.isNotEmpty()) continueRow else allRows.firstOrNull()?.let { rowRequester(it.key) },
                            )
                        }
                    }
                }
                if (continueItems.isNotEmpty()) {
                    item(key = "continue") {
                        Box(Modifier.padding(top = 14.dp)) {
                            ContinueRow(
                                items = continueItems,
                                onResume = {
                                    if (!Iptv.resume(context, it)) {
                                        Toast.makeText(context, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                requester = continueRow,
                                up = appsRow,
                                down = allRows.firstOrNull()?.let { rowRequester(it.key) },
                            )
                        }
                    }
                }
                itemsIndexed(allRows, key = { _, row -> "row:${mediaType.path}:${row.key}" }) { i, row ->
                    Box(Modifier.padding(top = 14.dp)) {
                        PosterRow(
                            row = row,
                            onOpen = openTitle,
                            onPlay = playTitle,
                            requester = rowRequester(row.key),
                            up = if (i > 0) rowRequester(allRows[i - 1].key) else if (continueItems.isNotEmpty()) continueRow else appsRow,
                            down = allRows.getOrNull(i + 1)?.let { rowRequester(it.key) },
                        )
                    }
                }
                item(key = "credit") {
                    Text(
                        text = "Show and film data from TMDB. This app uses the TMDB API but is not endorsed or certified by TMDB.",
                        modifier = Modifier.padding(horizontal = Edge, vertical = 16.dp),
                        style = TextStyle(fontSize = 10.sp, color = WiptvColors.quiet.copy(alpha = 0.6f)),
                    )
                }
            } }

            // Search, over the home screen. A details page opened from it sits on top.
            if (searchOpen) {
                SearchScreen(
                    onOpen = {
                        surpriseMode = false
                        detailFor = it
                    },
                    onPlay = playTitle,
                    onClose = { searchOpen = false },
                )
            }

            // A title's details page, over the home screen.
            detailFor?.let { title ->
                DetailScreen(
                    title = title,
                    onWatch = { source -> watch(title, source) },
                    onTrailer = { key ->
                        TitleOpener.openTrailer(context, key, apps)?.let {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                        }
                    },
                    onOpenOther = {
                        surpriseMode = false
                        detailFor = it
                    },
                    onAnother = if (surpriseMode) ({ surprise() }) else null,
                    onPlayOther = playTitle,
                    onLibraryChange = { myList = library.myList() },
                    onClose = {
                        detailFor = null
                        surpriseMode = false
                    },
                )
            }

            // Hold OK on an app: hide it or move it.
            menuFor?.let { app ->
                val index = dockApps.indexOfFirst { it.packageName == app.packageName }
                AppMenu(
                    app = app,
                    canMoveLeft = index > 0,
                    canMoveRight = index in 0 until dockApps.size - 1,
                    onHide = {
                        setHidden(hidden + app.packageName)
                        val neighbour = dockApps.getOrNull(index + 1) ?: dockApps.getOrNull(index - 1)
                        focusOn(neighbour?.packageName ?: EDIT_TILE_KEY)
                        menuFor = null
                    },
                    onMoveLeft = {
                        val list = dockApps.toMutableList()
                        list.add(index - 1, list.removeAt(index))
                        saveOrder(list)
                        focusOn(app.packageName)
                        menuFor = null
                    },
                    onMoveRight = {
                        val list = dockApps.toMutableList()
                        list.add(index + 1, list.removeAt(index))
                        saveOrder(list)
                        focusOn(app.packageName)
                        menuFor = null
                    },
                    onClose = {
                        focusOn(app.packageName)
                        menuFor = null
                    },
                )
            }

            // "Edit apps": every app on the TV, OK to show or hide.
            if (editing) {
                AppEditor(
                    apps = apps,
                    hidden = hidden,
                    onToggle = { app ->
                        setHidden(if (app.packageName in hidden) hidden - app.packageName else hidden + app.packageName)
                    },
                    onDone = {
                        editing = false
                        focusOn(EDIT_TILE_KEY)
                    },
                )
            }
        }
    }
}

/** Focus key for the "Edit apps" tile at the end of the dock. */
private const val EDIT_TILE_KEY = "__edit_apps__"

/**
 * The top line: the W badge and greeting on the left; on the right the weather and clock in one
 * quiet glass capsule, then the update pill. Waterford and the outlook sit underneath, smaller.
 */
@Composable
private fun Header(now: LocalTime, weather: WeatherNow?, update: UpdateState, onUpdateClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Edge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // The W badge and the greeting.
        Row(
            modifier = Modifier.height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(26.dp).background(WiptvColors.focus, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "W", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WiptvColors.onLight))
            }
            Text(text = "${greeting(now)}, $OWNER_NAME", style = WiptvType.screen)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .glass(WiptvShapes.pill)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (weather != null) {
                        WeatherIcon(sky = weather.sky, color = WiptvColors.text, size = 16.dp)
                        Text(
                            text = "${weather.temperature}°",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text),
                        )
                        Text(text = weather.description, maxLines = 1, style = TextStyle(fontSize = 13.sp, color = WiptvColors.secondary))
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(Color.White.copy(alpha = 0.18f)),
                        )
                    }
                    Text(
                        text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text),
                    )
                }
                UpdateButton(update = update, onClick = onUpdateClick)
            }
            if (weather != null) {
                // The forecast, quieter, on its own line under the capsule.
                Text(
                    text = "${Weather.placeName} · ${weather.outlook}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, end = 14.dp),
                    style = WiptvType.hint.copy(fontSize = 11.sp, color = WiptvColors.quiet.copy(alpha = 0.75f)),
                )
            }
        }
    }
}

/**
 * A pill in the header that's always there: it shows the installed build, and lights up when a
 * newer one is on GitHub. Pressing it checks again, or installs the update when there is one.
 */
@Composable
private fun UpdateButton(update: UpdateState, onClick: () -> Unit) {
    val label = update.label(BuildConfig.VERSION_CODE)
    // Bright when there's something to do, quiet otherwise.
    GlassPill(
        text = label,
        onClick = onClick,
        loud = update.loud,
        modifier = Modifier.widthIn(max = 320.dp),
    )
}

private fun greeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppDock(
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onLongPress: (TvApp) -> Unit,
    onEdit: () -> Unit,
    focusKey: String?,
    focusTick: Int,
    rowRequester: FocusRequester,
    down: FocusRequester?,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String) = requesters.getOrPut(key) { FocusRequester() }

    // Put the remote's focus on the first app when the screen opens, and back on the right
    // tile after the menu or editor closes. Tiles may not be laid out on the first frame,
    // so retry briefly.
    LaunchedEffect(focusKey, focusTick) {
        val key = focusKey ?: apps.firstOrNull()?.packageName ?: EDIT_TILE_KEY
        repeat(20) {
            if (runCatching { requesterFor(key).requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    TrackAttached(rowRequester)
    val toNextRow = Modifier.focusProperties { down.ifAttached()?.let { this.down = it } }
    RowScrolling(start = Edge, end = 72.dp) {
    LazyRow(
        modifier = Modifier
            .focusRequester(rowRequester)
            .focusRestorer(),
        contentPadding = PaddingValues(start = Edge - ArtFocusGap, end = Edge, top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp - ArtFocusGap * 2),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppTile(
                app = app,
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) },
                modifier = Modifier.focusRequester(requesterFor(app.packageName)).then(toNextRow),
            )
        }
        items(QuickSetting.entries, key = { it.key }) { setting ->
            QuickSettingTile(
                setting = setting,
                modifier = Modifier.focusRequester(requesterFor(setting.key)).then(toNextRow),
            )
        }
        item(key = EDIT_TILE_KEY) {
            EditAppsTile(
                onClick = onEdit,
                modifier = Modifier.focusRequester(requesterFor(EDIT_TILE_KEY)).then(toNextRow),
            )
        }
    }
    }
}

/** The last tile in the dock: opens the screen for choosing which apps show. */
@Composable
private fun EditAppsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    ArtFrame(width = TileWidth, height = TileHeight, onFocusChange = { focused = it }, onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val colour = if (focused) WiptvColors.text else WiptvColors.secondary
            Text(text = "+", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = colour))
            Text(text = "Edit apps", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colour))
        }
    }
}

/** App tiles: the 16:9 size of an Android TV banner, so banners show whole. */
internal val TileWidth = 144.dp
internal val TileHeight = 81.dp

/**
 * One app tile, in the same frame as the posters: its TV banner, or else its icon and name. Focus
 * is the shared outline just outside the tile and a slight lift. [shown] is only set on the
 * "Choose your apps" screen: a tick when the app shows in the dock, dimmed when it's hidden.
 */
@Composable
internal fun AppTile(
    app: TvApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shown: Boolean? = null,
) {
    ArtFrame(
        width = TileWidth,
        height = TileHeight,
        onFocusChange = {},
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.alpha(if (shown == false) 0.4f else 1f),
    ) {
        val image = app.image
        if (image != null && app.isBanner) {
            Image(
                bitmap = image,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                )
            }
        }
        if (shown != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(if (shown) WiptvColors.focus else WiptvColors.background.copy(alpha = 0.8f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (shown) "✓" else "–",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (shown) WiptvColors.onLight else WiptvColors.secondary),
                )
            }
        }
    }
}

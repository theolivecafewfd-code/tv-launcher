package dev.tvlauncher

import dev.wiptv.update.UpdateState
import dev.wiptv.update.Updater
import dev.wiptv.update.label
import dev.wiptv.update.loud
import androidx.compose.foundation.BorderStroke
import dev.wiptv.design.wiptvBackground
import dev.wiptv.design.WiptvHeader
import dev.wiptv.design.WiptvButton
import dev.wiptv.design.Glass
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.Hero
import dev.wiptv.design.HeroInfo
import dev.wiptv.design.PivotScrolling
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Job
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
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
internal val RowHeading = WiptvType.sectionHeading


@Composable
fun HomeScreen(
    refreshKey: Int,
    onLaunch: (TvApp) -> Unit,
    /** Asked for by WIPTV Player's header: "apps" opens the Apps tray, "search" opens search. */
    request: String? = null,
    requestTick: Int = 0,
    /** Set when the player asked: closing the tray or search goes back there ([onReturn]). */
    returnTo: String? = null,
    onReturn: () -> Unit = {},
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }

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

    // Your channels: the live channels you've starred in the player, refreshed each time you come home.
    var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    LaunchedEffect(refreshKey) { channels = Iptv.favouriteChannels(context) }
    val channelRow = remember { FocusRequester() }

    // TV Shows or Movies: Recommended for you follows it (My List shows both, at Wayne's request).
    var mediaType by remember { mutableStateOf(MediaType.TV) }
    // One focus target per row, so up and down move exactly one row. Up from the first row goes
    // to the navigation in the header; Apps there opens the tray.
    val appsButton = remember { FocusRequester() }
    val searchButton = remember { FocusRequester() }
    var trayOpen by remember { mutableStateOf(false) }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun rowRequester(key: String) = rowFocus.getOrPut(key) { FocusRequester() }
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
    val openTitle: (Title) -> Unit = { title ->
        // From the player's My List: it opens there (a film plays, a series opens its page).
        val iptv = title.iptv
        if (iptv == null) {
            detailFor = title
        } else if (!Iptv.open(context, iptv)) {
            Toast.makeText(context, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
        }
    }
    fun watch(title: Title, only: Source? = null) {
        if (title.iptv != null) {
            openTitle(title)
            return
        }
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

    // My List, shown as a row once you've added something. It's one list with the player's:
    // titles your provider has live in the player's My List, the rest in the launcher's.
    val library = remember { Library(context) }
    var myList by remember { mutableStateOf(library.myList()) }
    var playerList by remember { mutableStateOf<List<IptvTitle>>(emptyList()) }
    var listChanges by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey, listChanges) { playerList = Iptv.myList(context) }
    val allRows = remember(myList, playerList, mediaType, recommendedRow, recommendedLoading) {
        // My List has shows and films together, the same on TV Shows and Movies. A title in both
        // lists (added before they were joined) shows once, as the launcher's.
        val launcherNames = myList.map { it.type to sameName(it.name) }.toSet()
        val mine = playerList.filter { (it.type to sameName(it.name)) !in launcherNames }.map { it.asTitle() } + myList
        val withList = if (mine.isEmpty()) emptyList() else listOf(ContentRow("mylist", "My List", "", ranked = false, items = mine))
        val recommended = recommendedRow?.takeIf { it.items.firstOrNull()?.type == mediaType }
        when {
            recommended != null -> withList + recommended
            // An empty row while it loads: PosterRow shows placeholders for it.
            recommendedLoading -> withList + ContentRow("recommended", "Recommended for you", "", ranked = false, items = emptyList())
            else -> withList
        }
    }

    // The top of the screen, as in the player: the focused card's artwork, title and synopsis.
    // Until a card has focus it shows the first title in the rows.
    var hero by remember(mediaType) { mutableStateOf<Hero?>(null) }
    val shownHero = hero ?: allRows.firstNotNullOfOrNull { it.items.firstOrNull() }?.let(::heroOf)
    // Continue watching has no synopsis of its own: it comes from TMDB, looked up by name once
    // you rest on the card, and remembered.
    val continueDetails = remember { mutableStateMapOf<String, Title?>() }
    var heroJob by remember { mutableStateOf<Job?>(null) }
    fun focusTitle(title: Title) {
        heroJob?.cancel()
        hero = heroOf(title)
    }
    fun focusResume(item: IptvResume) {
        heroJob?.cancel()
        hero = heroOf(item, continueDetails[item.key])
        if (item.key in continueDetails) return
        heroJob = titleScope.launch {
            delay(400)
            val found = try {
                // The provider's names often carry the year in brackets, "Project Hail Mary (2026)"
                // (a bare number, as in "Blade Runner 2049", is part of the name).
                val year = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""").findAll(item.title).lastOrNull()?.groupValues?.get(1)
                Catalog.lookup(sameName(item.title), year, item.type)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch // not remembered, so it's tried again next time
            }
            continueDetails[item.key] = found
            hero = heroOf(item, found)
        }
    }
    // Your channels: what's on now and next, from the player's guide, once you rest on a channel.
    // Kept for a few minutes, or until the programme ends.
    val onNow = remember { mutableMapOf<Long, Pair<Long, List<IptvProgramme>>>() }
    fun focusChannel(channel: IptvChannel) {
        heroJob?.cancel()
        val now = System.currentTimeMillis()
        val cached = onNow[channel.id]?.takeIf { (at, list) ->
            now - at < 5 * 60_000L && (list.firstOrNull()?.end ?: Long.MAX_VALUE) > now
        }?.second
        hero = heroOf(channel, cached.orEmpty(), now)
        if (cached != null) return
        heroJob = titleScope.launch {
            delay(400)
            val list = Iptv.onNow(context, channel)
            onNow[channel.id] = System.currentTimeMillis() to list
            hero = heroOf(channel, list, System.currentTimeMillis())
        }
    }

    /** The first row: Continue watching, Your channels, or the first poster row. */
    fun firstRowBelowTop(): FocusRequester? = continueRow.takeIf { continueItems.isNotEmpty() }
        ?: channelRow.takeIf { channels.isNotEmpty() }
        ?: allRows.firstOrNull()?.let { rowRequester(it.key) }

    // When the home screen opens, focus starts on the first card (or on Apps if there's none).
    var placedFocus by remember { mutableStateOf(false) }
    val anyRow = continueItems.isNotEmpty() || channels.isNotEmpty() || allRows.any { it.items.isNotEmpty() }
    LaunchedEffect(anyRow) {
        if (placedFocus) return@LaunchedEffect
        val target = if (anyRow) {
            delay(100)
            firstRowBelowTop() ?: return@LaunchedEffect
        } else {
            delay(3_000)
            appsButton
        }
        repeat(20) {
            if (runCatching { target.requestFocus() }.isSuccess) {
                placedFocus = true
                return@LaunchedEffect
            }
            delay(50)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update = UpdateState.Failed(e.message ?: "download failed", target)
            }
        }
    }
    // From the player's header: open the Apps tray or search straight away.
    LaunchedEffect(requestTick) {
        if (request != "apps" && request != "search") return@LaunchedEffect
        // At the top, so closing them puts focus back on Apps or search in the header.
        runCatching { listState.scrollToItem(0) }
        if (request == "apps") trayOpen = true else searchOpen = true
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wiptvBackground(),
        ) {
            // While choosing apps, the editor replaces the home screen so the remote can't
            // wander onto the tiles underneath. Your apps are in the Apps tray.
            if (!editing) {
                // The focused card's artwork, large and 16:9, top right, fading into the dark
                // behind the header and the text. The header and text stay put; the rows move.
                CinematicBackdrop(
                    backdrop = shownHero?.backdrop,
                    poster = shownHero?.poster,
                    modifier = Modifier.align(Alignment.TopEnd).size(width = 780.dp, height = 439.dp),
                )
                Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                    // The header the player shares: greeting, status pill and navigation.
                    WiptvHeader(
                        nav = launcherNav(
                            selected = mediaType,
                            onSelect = { mediaType = it },
                            onLiveTv = {
                                if (!Iptv.openLiveTv(context)) {
                                    Toast.makeText(context, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onApps = { trayOpen = true },
                            onSearch = { searchOpen = true },
                            onSurprise = { surprise() },
                            appsRequester = appsButton,
                            searchRequester = searchButton,
                        ),
                        updateLabel = update.label(BuildConfig.VERSION_CODE),
                        updateLoud = update.loud,
                        onUpdateClick = onUpdateClick,
                        modifier = Modifier.padding(horizontal = Edge),
                    )
                    // What the focused card is: title, details and a short synopsis.
                    HeroInfo(hero = shownHero, start = Edge) {
                        Text(
                            text = when {
                                !Catalog.hasKey -> "Add your TMDB key to GitHub secrets as TMDB_API_KEY to see shows and films here."
                                recommendedLoading -> "Loading ${mediaType.label.lowercase()}…"
                                else -> ""
                            },
                            style = WiptvType.metadata.copy(color = WiptvColors.quiet),
                        )
                    }
                    // The focused row always moves to the top of the list, one row per press, as
                    // in the player.
                    PivotScrolling(pivot = 34.dp, alwaysAlign = true) { LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (continueItems.isNotEmpty()) {
                            item(key = "continue") {
                                ContinueRow(
                                    items = continueItems,
                                    onResume = {
                                        if (!Iptv.resume(context, it)) {
                                            Toast.makeText(context, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFocusItem = ::focusResume,
                                    requester = continueRow,
                                    up = null,
                                    down = channelRow.takeIf { channels.isNotEmpty() } ?: allRows.firstOrNull()?.let { rowRequester(it.key) },
                                )
                            }
                        }
                        if (channels.isNotEmpty()) {
                            item(key = "channels") {
                                ChannelRow(
                                    channels = channels,
                                    onWatch = {
                                        if (!Iptv.watchChannel(context, it)) {
                                            Toast.makeText(context, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFocusChannel = ::focusChannel,
                                    requester = channelRow,
                                    up = continueRow.takeIf { continueItems.isNotEmpty() },
                                    down = allRows.firstOrNull()?.let { rowRequester(it.key) },
                                )
                            }
                        }
                        itemsIndexed(allRows, key = { _, row -> "row:${mediaType.path}:${row.key}" }) { i, row ->
                            PosterRow(
                                row = row,
                                onOpen = openTitle,
                                onPlay = playTitle,
                                onFocusTitle = ::focusTitle,
                                requester = rowRequester(row.key),
                                up = if (i > 0) rowRequester(allRows[i - 1].key) else channelRow.takeIf { channels.isNotEmpty() }
                                    ?: continueRow.takeIf { continueItems.isNotEmpty() },
                                down = allRows.getOrNull(i + 1)?.let { rowRequester(it.key) },
                            )
                        }
                        item(key = "credit") {
                            Text(
                                text = "Show and film data from TMDB. This app uses the TMDB API but is not endorsed or certified by TMDB.",
                                modifier = Modifier.padding(horizontal = Edge, vertical = 16.dp),
                                style = TextStyle(fontSize = 10.sp, color = WiptvColors.quiet.copy(alpha = 0.6f)),
                            )
                        }
                    } }
                }
            }

            // Back on the home screen itself asks before leaving WIPTV, so a stray press doesn't
            // drop you on the Fire TV home. Anything open on top (the tray, search, a details page,
            // the app menu or editor) handles Back itself first.
            var confirmExit by remember { mutableStateOf(false) }
            BackHandler(enabled = !trayOpen && !searchOpen && detailFor == null && !editing && menuFor == null && !confirmExit) {
                confirmExit = true
            }
            if (confirmExit) {
                ExitConfirm(
                    onStay = { confirmExit = false },
                    onLeave = {
                        confirmExit = false
                        (context as? android.app.Activity)?.finish()
                    },
                )
            }

            // Apps: the tray over the bottom of the home screen. Back puts focus back on Apps, and
            // the home screen stays scrolled where it was.
            AppsTray(
                open = trayOpen && !editing,
                apps = dockApps,
                onLaunch = onLaunch,
                onLongPress = { menuFor = it },
                onEdit = { editing = true },
                focusKey = focusKey,
                focusTick = focusTick,
                backEnabled = menuFor == null,
                onClose = {
                    trayOpen = false
                    // Opened from the player: back there, as if the tray were part of it.
                    if (returnTo != null) onReturn()
                    titleScope.launch {
                        delay(50)
                        runCatching { appsButton.requestFocus() }
                    }
                },
            )

            // Search, over the home screen. A details page opened from it sits on top.
            if (searchOpen) {
                SearchScreen(
                    onOpen = {
                        surpriseMode = false
                        detailFor = it
                    },
                    onPlay = playTitle,
                    onClose = {
                        searchOpen = false
                        if (returnTo != null) onReturn()
                        // Back on the search icon in the top navigation.
                        titleScope.launch {
                            delay(50)
                            runCatching { searchButton.requestFocus() }
                        }
                    },
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
                    onLibraryChange = {
                        myList = library.myList()
                        listChanges++
                    },
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
    width: Dp = TileWidth,
    height: Dp = TileHeight,
    /** Off in the Apps tray, where the name sits under the tile: an icon tile shows just its icon. */
    labelInside: Boolean = true,
) {
    ArtFrame(
        width = width,
        height = height,
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
                        contentDescription = if (labelInside) null else app.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(if (labelInside) 32.dp else 44.dp),
                    )
                    if (labelInside) Spacer(Modifier.height(6.dp))
                }
                if (labelInside || image == null) {
                    Text(
                        text = app.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                    )
                }
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

/**
 * A name for spotting the same title in both My Lists: no language tag ("EN - "), year or
 * punctuation, lower case.
 */
private fun sameName(name: String): String =
    name.replace(Regex("""^\s*(\|[^|]*\||\[[^\]]*])\s*"""), "").replace(Regex("""^\s*[A-Z]{2,3}\s*[-:|]\s+"""), "")
        .lowercase().replace(Regex("""[(\[]\s*(19|20)\d{2}\s*[)\]]"""), " ").replace(Regex("[^a-z0-9]+"), " ").trim().removePrefix("the ")

/**
 * "Leave WIPTV?" on a small dark glass panel, for Back on the home screen. Stay has focus, so a
 * second stray press of OK or Back keeps you here; Leave goes back to the Fire TV home.
 */
@Composable
private fun ExitConfirm(onStay: () -> Unit, onLeave: () -> Unit) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { stay.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Dialog(onDismissRequest = onStay) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .glass(WiptvShapes.panel, Glass.Sheet)
                .padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Leave WIPTV?", style = WiptvType.section)
            Text(text = "You'll go back to the Fire TV home screen.", style = WiptvType.metadata)
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WiptvButton(text = "Stay", onClick = onStay, primary = true, modifier = Modifier.focusRequester(stay))
                WiptvButton(text = "Leave", onClick = onLeave)
            }
        }
    }
}

/** A TMDB title at the top of the screen: its name, details and synopsis over its artwork. */
private fun heroOf(title: Title) = Hero(
    title = DisplayTitles.withoutYear(title.name, title.year),
    meta = metaItems(title).joinToString(" · "),
    plot = title.overview.ifBlank { null },
    backdrop = title.backdropUrl,
    poster = title.posterUrl,
)

/**
 * Something you've started in the player: where you're up to and how long is left, with the
 * synopsis (and artwork, if the player has none) from TMDB once it's found.
 */
private fun heroOf(item: IptvResume, tmdb: Title?) = Hero(
    title = item.title,
    meta = listOfNotNull(
        item.subtitle?.takeIf { it.isNotBlank() },
        item.resumeFrom?.let { "Resume from $it" },
        item.left,
    ).joinToString(" · "),
    plot = tmdb?.overview?.ifBlank { null },
    backdrop = item.imageUrl ?: tmdb?.backdropUrl,
    poster = item.posterUrl ?: tmdb?.posterUrl,
)

/**
 * A starred channel: its name, what's on now (times, title, how far through, description) and
 * what's next. With no guide, just the name.
 */
private fun heroOf(channel: IptvChannel, programmes: List<IptvProgramme>, now: Long): Hero {
    val current = programmes.firstOrNull()?.takeIf { it.start <= now }
    val next = if (current != null) programmes.getOrNull(1) else programmes.firstOrNull()
    return Hero(
        title = channel.name,
        meta = if (current == null) "Live TV · OK to watch" else "Now · ${hhmm(current.start)}–${hhmm(current.end)} · ${current.title}",
        plot = current?.description?.ifBlank { null },
        backdrop = null,
        poster = null,
        progress = current?.let { ((now - it.start).toFloat() / (it.end - it.start)).coerceIn(0f, 1f) },
        note = next?.let { "Next · ${hhmm(it.start)} · ${it.title}" },
    )
}

private fun hhmm(time: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(time))

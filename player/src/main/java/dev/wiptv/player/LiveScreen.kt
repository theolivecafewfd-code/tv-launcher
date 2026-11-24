package dev.wiptv.player

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import dev.wiptv.design.Glass
import dev.wiptv.design.KeyHints
import dev.wiptv.design.PivotScrolling
import androidx.activity.compose.BackHandler
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import dev.wiptv.design.glass
import dev.wiptv.design.selectedMarker
import dev.wiptv.design.wiptvBackground
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.draw.clip

/** Remembers the last group and channel watched, so the app opens where you left off. */
private object LastWatched {
    private const val PREFS = "live"
    fun group(context: Context): String? = prefs(context).getString("group", null)
    fun channel(context: Context): Long = prefs(context).getLong("channel", -1)
    /** Channels in name order (A–Z) rather than the provider's own order. */
    fun sortByName(context: Context): Boolean = prefs(context).getBoolean("sortByName", false)
    fun setSortByName(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("sortByName", value).apply()
    }
    fun save(context: Context, channel: Channel) {
        prefs(context).edit().putString("group", channel.groupId).putLong("channel", channel.id).apply()
    }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** What full screen plays through: a group's channels and where to start. */
private class WatchList(val channels: List<Channel>, val groupId: String, val groupName: String, val start: Int)

/**
 * Channels (Live TV): groups on the left, and the group's channels as a grid of logo tiles, each
 * with what's on now, how far through it is and how long is left. OK on a tile watches it full
 * screen; Menu (≡) adds it to (or removes it from) Favourites. Nothing plays on this page.
 *
 * The full guide (the menu's Guide) is still part of this screen: there, a small preview plays
 * what you pick, and OK again on it goes full screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
    login: Login,
    update: UpdateHandle,
    onSignOut: () -> Unit,
    /** A favourite channel to open full screen straight away (from the launcher). */
    openChannel: Long? = null,
    /** Open on ★ Favourites (the menu's Favourites) rather than the last group you were in. */
    startOnFavourites: Boolean = false,
    /** Open straight into the full guide (the menu's Guide), on the group you were last in. */
    startInGuide: Boolean = false,
    header: @Composable () -> Unit = {
        Text(
            text = "Live TV",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.text),
        )
    },
) {
    val context = LocalContext.current
    var account by remember { mutableStateOf<Account?>(null) }
    // Kept between tabs while the player runs (SessionCache), so coming back is instant.
    val cache = remember(login) { SessionCache.of(login, "LIVE") }
    var groups by remember { mutableStateOf(cache.liveGroups) }
    var groupsError by remember { mutableStateOf<String?>(null) }
    var groupsAttempt by remember { mutableIntStateOf(0) }
    val channelsByGroup = cache.channels
    var channelsError by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf(if (startOnFavourites) FAVOURITES_ID else LastWatched.group(context)) }
    var showAccount by remember { mutableStateOf(false) }

    // Groups you've hidden or reordered (Edit groups).
    val groupPrefs = remember { GroupPrefs(context) }
    var hiddenGroups by remember { mutableStateOf(groupPrefs.hidden()) }
    var groupOrder by remember { mutableStateOf(groupPrefs.order()) }
    var editingGroups by remember { mutableStateOf(false) }
    val arranged = groups?.let { GroupPrefs.arrange(it, groupOrder) }
    val shownGroups = arranged?.filter { it.id !in hiddenGroups }
    var watching by remember { mutableStateOf<WatchList?>(null) }

    // Favourites: a group of your own above the provider's groups.
    val favouritePrefs = remember { FavouritePrefs(context) }
    var favourites by remember { mutableStateOf(favouritePrefs.load()) }
    val favouriteIds = favourites.map { it.id }.toSet()

    /** Menu (≡) on a channel: adds it to Favourites or takes it off, with a short message. */
    fun toggleFavourite(channel: Channel) {
        favourites = FavouritePrefs.toggle(favourites, channel)
        favouritePrefs.save(favourites)
        val added = favourites.any { it.id == channel.id }
        android.widget.Toast.makeText(
            context,
            if (added) "Added ${channel.name} to Favourites" else "Removed ${channel.name} from Favourites",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    // The group the preview's channel was picked from, so full screen can change channel within it.
    var previewSource by remember { mutableStateOf<Pair<String, List<Channel>>?>(null) }

    val live = rememberLivePlayer()
    val guide = remember(login) { GuideStore(login) }
    val now = rememberNow()
    val format = account?.liveFormat ?: "ts"

    // A channel to move the focus to once it's on screen: the last one watched when the app
    // opens, and whichever channel you ended on after watching. Null once it's done.
    var pendingFocus by remember { mutableStateOf<Long?>(LastWatched.channel(context)) }

    val groupList = rememberLazyListState()
    val channelGrid = rememberLazyGridState()
    var sortByName by remember { mutableStateOf(LastWatched.sortByName(context)) }
    /** A group's channels in the order chosen with Sort. */
    fun arrange(list: List<Channel>): List<Channel> =
        if (sortByName) list.sortedBy { ChannelLabels.of(it.name).name.lowercase() } else list
    val firstFocus = remember { FocusRequester() }
    val channelRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    fun requesterFor(id: Long) = channelRequesters.getOrPut(id) { FocusRequester() }

    // The channel you were last on in each group, so moving right returns to it.
    val lastChannelInGroup = remember { mutableMapOf<String, Long>() }

    // The account (for the stream format) and the groups load together when the screen opens.
    LaunchedEffect(Unit) {
        account = runCatching { Xtream.signIn(login, withCatalogue = false) }.getOrNull()
    }
    LaunchedEffect(groupsAttempt) {
        if (groupsAttempt == 0 && groups != null) return@LaunchedEffect
        groupsError = null
        try {
            val loaded = Xtream.liveGroups(login)
            groups = loaded
            cache.liveGroups = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            groupsError = e.message ?: "Couldn't load Live TV."
        }
    }

    // Load the selected group's channels shortly after it's highlighted (not on every step).
    LaunchedEffect(selectedGroup) {
        val id = selectedGroup ?: return@LaunchedEffect
        channelsError = null
        if (id == FAVOURITES_ID) return@LaunchedEffect
        if (channelsByGroup.containsKey(id)) return@LaunchedEffect
        delay(250)
        try {
            channelsByGroup[id] = Xtream.liveChannels(login, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            channelsError = e.message ?: "Couldn't load this group."
        }
    }

    // Keep the selection on a group that's showing, and scroll to it when the list first appears.
    var scrolledToSelection by remember { mutableStateOf(false) }
    LaunchedEffect(shownGroups) {
        val shown = shownGroups ?: return@LaunchedEffect
        if (selectedGroup != FAVOURITES_ID && shown.none { it.id == selectedGroup }) selectedGroup = shown.firstOrNull()?.id
        if (!scrolledToSelection) {
            scrolledToSelection = true
            groupList.scrollToItem(shown.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0))
        }
    }

    val unsorted = if (selectedGroup == FAVOURITES_ID) favourites else selectedGroup?.let { channelsByGroup[it] }
    val channels = remember(unsorted, sortByName) { unsorted?.let(::arrange) }
    fun groupName(id: String?): String =
        if (id == FAVOURITES_ID) "Favourites" else groups?.firstOrNull { it.id == id }?.name.orEmpty()

    // The full guide (Right on a channel): the channel it was opened from, and the programme
    // highlighted in it. A past programme playing from it is catch-up.
    var gridFrom by remember { mutableStateOf<Channel?>(null) }
    var gridPick by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }
    val fullGuide = remember(login) { FullGuideStore(login) }
    var catchUp by remember { mutableStateOf<Pair<Channel, ResumePoint>?>(null) }
    val vodStore = remember { VodStore(context) }
    // The menu's Guide: straight into the full guide once the group's channels are here, on the
    // channel last watched if it's in the group.
    var guideOpened by remember { mutableStateOf(!startInGuide) }
    LaunchedEffect(channels) {
        val list = channels ?: return@LaunchedEffect
        if (guideOpened || list.isEmpty()) return@LaunchedEffect
        guideOpened = true
        val last = LastWatched.channel(context)
        val from = list.firstOrNull { it.id == last } ?: list.first()
        gridPick = from to null
        gridFrom = from
    }

    // Opening a channel from the launcher: nothing else starts playing meanwhile.
    var opening by remember { mutableStateOf(openChannel != null) }

    // In the full guide, the preview picks up the last channel you watched.
    LaunchedEffect(channels, gridFrom != null) {
        if (gridFrom == null || live.channel != null || opening) return@LaunchedEffect
        val last = LastWatched.channel(context)
        val list = channels ?: return@LaunchedEffect
        list.firstOrNull { it.id == last }?.let {
            previewSource = (selectedGroup ?: "") to list
            live.play(login, format, it)
        }
    }

    /** Full screen for whatever's in the preview, changing channel within the group it came from. */
    fun watchPreview() {
        val current = live.channel ?: return
        val (groupId, list) = previewSource?.takeIf { (_, l) -> l.any { it.id == current.id } }
            ?: (current.groupId to listOf(current))
        watching = WatchList(list, groupId, groupName(groupId), list.indexOfFirst { it.id == current.id }.coerceAtLeast(0))
    }

    /** Replays a past programme from the start (catch-up), from the full guide. */
    fun startCatchUp(channel: Channel, programme: Programme) {
        // One stream at a time: the live preview stops while catch-up plays.
        live.hold()
        catchUp = channel to ResumePoint(
            key = "catchup:${channel.id}:${programme.start}",
            title = programme.title,
            subtitle = ChannelLabels.of(channel.name).name + " · " +
                java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK).format(java.util.Date(programme.start)),
            poster = channel.logo,
            url = Xtream.catchUpUrl(login, channel, programme, account?.timezone),
            position = 0,
            duration = programme.end - programme.start,
            updated = System.currentTimeMillis(),
        )
    }

    // From the launcher's channel row: that channel full screen, changing channel within its own
    // group. Back from full screen lands here in Live TV, on that group and channel.
    LaunchedEffect(openChannel) {
        val id = openChannel ?: return@LaunchedEffect
        val channel = favourites.firstOrNull { it.id == id }
        if (channel == null) {
            opening = false
            return@LaunchedEffect
        }
        selectedGroup = channel.groupId
        pendingFocus = channel.id
        // The stream format comes with the account; give it a moment.
        withTimeoutOrNull(4_000) { snapshotFlow { account }.filterNotNull().first() }
        val list = try {
            channelsByGroup[channel.groupId] ?: Xtream.liveChannels(login, channel.groupId).also { channelsByGroup[channel.groupId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }?.takeIf { l -> l.any { it.id == id } }?.let(::arrange) ?: listOf(channel)
        previewSource = channel.groupId to list
        watching = WatchList(list, channel.groupId, groupName(channel.groupId), list.indexOfFirst { it.id == id }.coerceAtLeast(0))
        opening = false
    }

    // One stream at a time, and none hidden: the channel grid has no preview, so whatever was
    // playing (the guide's preview, or full screen just closed) stops there.
    val onGrid = gridFrom == null && watching == null && catchUp == null && !opening
    LaunchedEffect(onGrid) {
        if (onGrid) live.stop()
    }

    // Whatever's in the preview: load its guide for the panel, and remember it for next time.
    LaunchedEffect(live.channel?.id) {
        val channel = live.channel ?: return@LaunchedEffect
        LastWatched.save(context, channel)
        guide.ensure(channel)
    }

    // Put the focus somewhere sensible: that channel if it's in the list, else the selected group.
    LaunchedEffect(groups, channels, pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        if (groups == null && groupsError == null) return@LaunchedEffect
        // Wait for the group's channels, unless they failed to load.
        if (groups != null && selectedGroup != null && channels == null && channelsError == null) return@LaunchedEffect
        delay(50)
        val index = channels?.indexOfFirst { it.id == target } ?: -1
        if (index >= 0) {
            channelGrid.scrollToItem(index)
            delay(50)
            if (runCatching { requesterFor(target).requestFocus() }.isSuccess) {
                pendingFocus = null
                return@LaunchedEffect
            }
        }
        runCatching { firstFocus.requestFocus() }
        pendingFocus = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wiptvBackground()
            .onPreviewKeyEvent { event ->
                // Play/Pause goes full screen on the preview from anywhere on this screen.
                val key = event.nativeKeyEvent.keyCode
                val isPlay = key == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                if (isPlay && live.channel != null) {
                    if (event.type == KeyEventType.KeyDown) watchPreview()
                    true
                } else {
                    false
                }
            }
            .padding(start = 32.dp, end = 32.dp, top = 20.dp),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // The shared header: wordmark, Live TV · Movies · Series, update.
        header()
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Groups down the left (not while the full guide is open: it has the whole width).
            if (gridFrom == null) Column(
                modifier = Modifier
                    .width(196.dp)
                    .fillMaxHeight()
                    .padding(top = 4.dp, bottom = 20.dp),
            ) {
                when {
                    groupsError != null -> {
                        Text(text = groupsError!!, style = TextStyle(fontSize = 13.sp, color = Palette.warn))
                        Spacer(Modifier.height(10.dp))
                        PlayerButton(
                            text = "Try again",
                            onClick = { groupsAttempt++ },
                            modifier = Modifier.focusRequester(firstFocus),
                        )
                    }
                    groups == null -> Text(text = "Loading groups…", style = TextStyle(fontSize = 13.sp, color = Palette.quiet))
                    shownGroups.isNullOrEmpty() -> Text(
                        text = "All groups are hidden. Use Edit groups below to show some.",
                        style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
                    )
                }
                val list = shownGroups.orEmpty()
                LazyColumn(
                    state = groupList,
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties {
                            enter = {
                                val visible = groupList.layoutInfo.visibleItemsInfo.any { it.key == selectedGroup }
                                if (visible) firstFocus else FocusRequester.Default
                            }
                        }
                        .focusGroup(),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "sort") {
                        // Channels in the provider's order, or by name.
                        ListItem(
                            onClick = {
                                sortByName = !sortByName
                                LastWatched.setSortByName(context, sortByName)
                            },
                            selected = false,
                            trailing = if (sortByName) "Name A–Z ›" else "Provider ›",
                        ) {
                            Text(text = "Sort", style = TextStyle(fontSize = 14.sp))
                        }
                    }
                    item(key = FAVOURITES_ID) {
                        val selected = selectedGroup == FAVOURITES_ID
                        ListItem(
                            onClick = { requesterForFirstChannel(channels, ::requesterFor) },
                            selected = selected,
                            trailing = favourites.size.toString(),
                            modifier = Modifier
                                .then(if (selected) Modifier.focusRequester(firstFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) selectedGroup = FAVOURITES_ID },
                        ) {
                            Text(
                                text = "★ Favourites",
                                maxLines = 1,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    item(key = "categoriesCaption") {
                        Text(
                            text = "CATEGORIES",
                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                            style = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp, color = WiptvColors.quiet),
                        )
                    }
                    itemsIndexed(list, key = { _, g -> g.id }) { _, group ->
                        val selected = group.id == selectedGroup
                        ListItem(
                            onClick = { requesterForFirstChannel(channels, ::requesterFor) },
                            selected = selected,
                            trailing = channelsByGroup[group.id]?.size?.toString(),
                            modifier = Modifier
                                .then(if (selected) Modifier.focusRequester(firstFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) selectedGroup = group.id },
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
                        ListItem(
                            onClick = { editingGroups = true },
                            selected = false,
                            modifier = if (list.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                        ) {
                            Text(text = "Edit categories", style = TextStyle(fontSize = 13.sp))
                        }
                    }
                    item(key = "account") {
                        ListItem(onClick = { showAccount = true }, selected = false) {
                            Text(text = "Account & sign out", style = TextStyle(fontSize = 13.sp))
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val from = gridFrom
                if (from != null) {
                // The full guide: the preview and what's highlighted, then the group's channels,
                // yesterday to tomorrow.
                Row(modifier = Modifier.height(136.dp)) {
                    Preview(live = live, attached = watching == null, onClick = ::watchPreview)
                    Spacer(Modifier.width(16.dp))
                    val pick = gridPick
                    if (pick != null) {
                        GuideDetailPanel(
                            channel = pick.first,
                            programme = pick.second,
                            loaded = fullGuide.programmes(pick.first.id) != null,
                            now = now,
                            modifier = Modifier.weight(1f).padding(top = 2.dp),
                        )
                    } else {
                        NowPanel(
                            channel = live.channel,
                            programmes = live.channel?.let { guide.programmes(it.id) },
                            now = now,
                            modifier = Modifier.weight(1f).padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                    val gridChannels = channels.orEmpty().ifEmpty { listOf(from) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = groupName(selectedGroup).ifBlank { "Full guide" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp).width(ChannelCellWidth - 8.dp),
                            style = WiptvType.control.copy(color = WiptvColors.text),
                        )
                        KeyHints(listOf("◀ ▶ through the day", "▲ ▼ channels", "OK watch", "≡ favourite", "Back to Channels"))
                    }
                    Spacer(Modifier.height(4.dp))
                    GuideGrid(
                        channels = gridChannels,
                        startIndex = gridChannels.indexOfFirst { it.id == from.id }.coerceAtLeast(0),
                        store = fullGuide,
                        now = now,
                        playingId = live.channel?.id,
                        favouriteIds = favouriteIds,
                        onSelect = { channel, programme -> gridPick = channel to programme },
                        // What's on now: into the preview; OK again on the one in the preview goes full screen.
                        onWatch = { channel ->
                            previewSource = (selectedGroup ?: "") to gridChannels
                            if (live.channel?.id == channel.id) watchPreview() else live.play(login, format, channel)
                        },
                        onCatchUp = ::startCatchUp,
                        onToggleFavourite = { toggleFavourite(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                } else {
                // The selected group's channels, as a grid of logo tiles.
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = groupName(selectedGroup),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                    )
                    Text(
                        text = when {
                            channelsError != null -> channelsError!!
                            selectedGroup == null -> ""
                            channels == null -> "Loading channels…"
                            channels.isEmpty() && selectedGroup == FAVOURITES_ID ->
                                "No favourites yet. Press Menu (≡) on a channel to add it."
                            channels.isEmpty() -> "No channels in this group"
                            channels.size == 1 -> "1 channel"
                            else -> "${channels.size} channels"
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp),
                        style = TextStyle(fontSize = 13.sp, color = if (channelsError != null) Palette.warn else WiptvColors.quiet),
                    )
                    if (!channels.isNullOrEmpty()) {
                        Spacer(Modifier.weight(1f))
                        KeyHints(listOf("OK watch", "≡ favourite"))
                    }
                }
                // A row that's partly off screen settles at the top, so whole rows show.
                PivotScrolling(pivot = 6.dp) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = channelGrid,
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties {
                                enter = {
                                    val last = selectedGroup?.let { lastChannelInGroup[it] }
                                    val visible = last != null &&
                                        channelGrid.layoutInfo.visibleItemsInfo.any { it.key == last }
                                    if (visible) requesterFor(last!!) else FocusRequester.Default
                                }
                            }
                            .focusGroup(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gridItemsIndexed(channels.orEmpty(), key = { _, c -> c.id }) { _, channel ->
                            // Each tile loads its own guide as it comes on screen, and again every half hour.
                            LaunchedEffect(channel.id, now / (30 * 60_000L)) { guide.ensure(channel) }
                            ChannelTile(
                                channel = channel,
                                programmes = guide.programmes(channel.id),
                                now = now,
                                favourite = channel.id in favouriteIds,
                                onClick = {
                                    val list = channels.orEmpty()
                                    previewSource = (selectedGroup ?: "") to list
                                    watching = WatchList(
                                        list,
                                        selectedGroup ?: channel.groupId,
                                        groupName(selectedGroup),
                                        list.indexOfFirst { it.id == channel.id }.coerceAtLeast(0),
                                    )
                                },
                                onMenu = { toggleFavourite(channel) },
                                modifier = Modifier
                                    .focusRequester(requesterFor(channel.id))
                                    .onFocusChanged { focus ->
                                        if (focus.isFocused) selectedGroup?.let { lastChannelInGroup[it] = channel.id }
                                    },
                            )
                        }
                    }
                }
                }
            }
        }
      }
    }

    // Back in the full guide: Live TV again, on the channel it was opened from.
    BackHandler(enabled = gridFrom != null && watching == null && catchUp == null) {
        pendingFocus = gridFrom?.id
        gridFrom = null
        gridPick = null
    }

    catchUp?.let { (channel, point) ->
        val finish = {
            catchUp = null
            // Back to the full guide, with the preview playing live again.
            previewSource = (selectedGroup ?: "") to (channels?.takeIf { l -> l.any { it.id == channel.id } } ?: listOf(channel))
            live.play(login, format, channel)
        }
        VodPlayerScreen(
            start = point,
            store = vodStore,
            onFinished = finish,
            onClose = finish,
            rememberPosition = false,
        )
    }

    val playing = watching
    if (playing != null) {
        WatchScreen(
            login = login,
            format = format,
            live = live,
            start = ChannelGroup(playing.groupId, playing.groupName, playing.channels),
            startIndex = playing.start,
            groups = listOf(FAVOURITES_ID to "★ Favourites") + shownGroups.orEmpty().map { it.id to it.name },
            loadGroup = { id ->
                arrange(
                    if (id == FAVOURITES_ID) favourites
                    else channelsByGroup[id] ?: Xtream.liveChannels(login, id).also { channelsByGroup[id] = it },
                )
            },
            groupOf = { channel ->
                val inGroup = channelsByGroup[channel.groupId]
                when {
                    inGroup != null && inGroup.any { it.id == channel.id } ->
                        ChannelGroup(channel.groupId, groupName(channel.groupId), arrange(inGroup))
                    favourites.any { it.id == channel.id } -> ChannelGroup(FAVOURITES_ID, "Favourites", arrange(favourites))
                    else -> ChannelGroup(channel.groupId, groupName(channel.groupId), listOf(channel))
                }
            },
            nowShowing = { guide.nowShowing(it) },
            upNext = { guide.upNext(it) },
            ensureGuide = { guide.ensure(it) },
            isFavourite = { channel -> favourites.any { it.id == channel.id } },
            onToggleFavourite = ::toggleFavourite,
            onChannel = { LastWatched.save(context, it) },
            onClose = { last, groupId ->
                watching = null
                // Back in the guide, on the group and channel you finished on.
                if (groupId.isNotEmpty()) {
                    selectedGroup = groupId
                    val list = if (groupId == FAVOURITES_ID) favourites else channelsByGroup[groupId]
                    previewSource = groupId to (list?.let(::arrange) ?: listOf(last))
                }
                pendingFocus = last.id
            },
        )
    }

    if (editingGroups && arranged != null) {
        fun saveOrder(ids: List<String>) {
            groupOrder = ids
            groupPrefs.setOrder(ids)
        }
        GroupEditor(
            groups = arranged,
            hidden = hiddenGroups,
            onToggle = { group ->
                hiddenGroups = if (group.id in hiddenGroups) hiddenGroups - group.id else hiddenGroups + group.id
                groupPrefs.setHidden(hiddenGroups)
            },
            onSetAllHidden = { hide ->
                hiddenGroups = if (hide) arranged.map { it.id }.toSet() else emptySet()
                groupPrefs.setHidden(hiddenGroups)
            },
            onMove = { group, step ->
                val ids = arranged.map { it.id }.toMutableList()
                val from = ids.indexOf(group.id)
                val to = from + step
                if (from >= 0 && to in ids.indices) {
                    ids.add(to, ids.removeAt(from))
                    saveOrder(ids)
                }
            },
            onMoveToTop = { group ->
                val ids = arranged.map { it.id }.toMutableList()
                if (ids.remove(group.id)) {
                    ids.add(0, group.id)
                    saveOrder(ids)
                }
            },
            onDone = { editingGroups = false },
        )
    }

    if (showAccount) {
        Dialog(onDismissRequest = { showAccount = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            AccountScreen(login = login, update = update, onSignOut = onSignOut)
        }
    }
}

private fun requesterForFirstChannel(channels: List<Channel>?, requesterFor: (Long) -> FocusRequester) {
    val first = channels?.firstOrNull() ?: return
    runCatching { requesterFor(first.id).requestFocus() }
}

/**
 * A row in the groups list: quiet until focused, when it flips to a light fill. The selected group
 * keeps a fainter wash and a dimmed bar, so it stays findable while focus is in the guide without
 * competing with the guide's bright focus bar.
 */
@Composable
private fun ListItem(
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** A quiet note on the right: a count, or the Sort setting. */
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = WiptvShapes.card
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) WiptvColors.focus.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) WiptvColors.text else WiptvColors.secondary,
            focusedContainerColor = WiptvColors.focus,
            focusedContentColor = WiptvColors.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = focusOutline(shape),
    ) {
        Row(
            modifier = Modifier.selectedMarker(selected, WiptvColors.focus.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { content() }
            if (trailing != null) {
                Text(
                    text = trailing,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                    style = TextStyle(fontSize = 13.sp, color = LocalContentColor.current.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

/**
 * One channel in the grid: its logo on a dark tile (the name stands in until the logo loads, or
 * if there isn't one), then how far through the current programme it is, the channel's name, and
 * what's on with the time left. Focus is a bright outline round the tile; a favourite has a ★.
 */
@Composable
private fun ChannelTile(
    channel: Channel,
    programmes: List<Programme>?,
    now: Long,
    favourite: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = remember(channel.name) { ChannelLabels.of(channel.name) }
    val shape = RoundedCornerShape(10.dp)
    var logoShown by remember(channel.logo) { mutableStateOf(false) }
    val current = programmes?.firstOrNull { it.isOn(now) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(92.dp)
                // The remote's Menu (≡) button: adds the channel to Favourites, or takes it off.
                // Play/Pause watches it, like OK.
                .onPreviewKeyEvent { event ->
                    val action = when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MENU -> onMenu
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> onClick
                        else -> null
                    } ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) action()
                    true
                },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = WiptvColors.surface,
                contentColor = WiptvColors.secondary,
                focusedContainerColor = WiptvColors.elevated,
                focusedContentColor = WiptvColors.text,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = focusOutline(shape, width = 3.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!logoShown) {
                    Text(
                        text = label.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    )
                }
                if (channel.logo != null) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onSuccess = { logoShown = true },
                        onError = { logoShown = false },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                if (favourite) {
                    Text(
                        text = "★",
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp),
                        style = TextStyle(fontSize = 13.sp, color = WiptvColors.text),
                    )
                }
            }
        }
        // How far through what's on now.
        if (current != null) {
            val progress = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(WiptvColors.elevated, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(WiptvColors.progress, RoundedCornerShape(2.dp)),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
            )
            label.quality?.let { QualityBadge(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = when {
                    current != null -> current.title
                    programmes == null -> "Loading…"
                    else -> "No information"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 12.sp, color = if (current != null) WiptvColors.secondary else WiptvColors.quiet),
            )
            if (current != null) {
                Text(
                    text = timeLeft(current.end - now),
                    maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, color = WiptvColors.secondary),
                )
            }
        }
    }
}

/** "24m left", "1h 10m left". */
private fun timeLeft(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    val hours = minutes / 60
    return when {
        hours == 0L -> "${minutes}m left"
        minutes % 60 == 0L -> "${hours}h left"
        else -> "${hours}h ${minutes % 60}m left"
    }
}

/**
 * The small picture at the top, with loading and error notes over it. It can be highlighted
 * (Up from the top of the guide), and OK on it goes full screen.
 *
 * The video can't sit inside a TV "Surface" (the picture goes black, only sound plays), so the
 * highlight is a plain border drawn over the picture instead.
 */
@Composable
private fun Preview(live: LivePlayer, attached: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(width = 242.dp, height = 136.dp)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f), WiptvShapes.panel)
            .padding(3.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val key = event.nativeKeyEvent.keyCode
                val isOk = key == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    key == android.view.KeyEvent.KEYCODE_ENTER ||
                    key == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                if (isOk && event.type == KeyEventType.KeyUp) onClick()
                isOk
            }
            .focusable()
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        PlayerSurface(live, attached = attached, modifier = Modifier.fillMaxSize())
        val note = when {
            live.channel == null -> "Press OK on a channel to preview it"
            live.error != null -> live.error
            live.buffering -> "Loading…"
            else -> null
        }
        if (note != null) {
            Text(
                text = note,
                maxLines = 3,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = if (live.error != null && live.channel != null) Palette.warn else Palette.quiet,
                ),
            )
        }
        if (focused) {
            Box(modifier = Modifier.matchParentSize().border(3.dp, Palette.glow, shape))
        }
    }
}

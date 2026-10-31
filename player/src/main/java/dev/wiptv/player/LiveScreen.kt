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
    fun save(context: Context, channel: Channel) {
        prefs(context).edit().putString("group", channel.groupId).putLong("channel", channel.id).apply()
    }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** What full screen plays through: a group's channels and where to start. */
private class WatchList(val channels: List<Channel>, val groupId: String, val groupName: String, val start: Int)

/**
 * Live TV: groups on the left; on the right, a small preview with what's on, and below it the
 * group's channels as a simple two-hour guide. Moving around leaves the preview alone: OK on a
 * channel previews it, and OK again on the channel in the preview goes full screen. Play/Pause,
 * or OK on the preview itself, also goes full screen from anywhere. Holding OK on a channel adds
 * it to (or removes it from) Favourites.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
    login: Login,
    update: UpdateHandle,
    onSignOut: () -> Unit,
    /** A favourite channel to open full screen straight away (from the launcher). */
    openChannel: Long? = null,
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
    var selectedGroup by remember { mutableStateOf(LastWatched.group(context)) }
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
    val channelList = rememberLazyListState()
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

    val channels = if (selectedGroup == FAVOURITES_ID) favourites else selectedGroup?.let { channelsByGroup[it] }
    fun groupName(id: String?): String =
        if (id == FAVOURITES_ID) "Favourites" else groups?.firstOrNull { it.id == id }?.name.orEmpty()

    // The full guide (Right on a channel): the channel it was opened from, and the programme
    // highlighted in it. A past programme playing from it is catch-up.
    var gridFrom by remember { mutableStateOf<Channel?>(null) }
    var gridPick by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }
    val fullGuide = remember(login) { FullGuideStore(login) }
    var catchUp by remember { mutableStateOf<Pair<Channel, ResumePoint>?>(null) }
    val vodStore = remember { VodStore(context) }

    // Opening a channel from the launcher: nothing else starts playing meanwhile.
    var opening by remember { mutableStateOf(openChannel != null) }

    // When the app opens, the preview picks up the last channel you watched.
    LaunchedEffect(channels) {
        if (live.channel != null || opening) return@LaunchedEffect
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
        }?.takeIf { l -> l.any { it.id == id } } ?: listOf(channel)
        previewSource = channel.groupId to list
        watching = WatchList(list, channel.groupId, groupName(channel.groupId), list.indexOfFirst { it.id == id }.coerceAtLeast(0))
        opening = false
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
            channelList.scrollToItem((index - 3).coerceAtLeast(0))
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
            // Groups, on a glass panel (not while the full guide is open: it has the whole width).
            if (gridFrom == null) Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .padding(bottom = 20.dp)
                    .glass(WiptvShapes.panel, Glass.Quiet)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Categories",
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                    style = WiptvType.hint,
                )
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
                    item(key = FAVOURITES_ID) {
                        val selected = selectedGroup == FAVOURITES_ID
                        ListItem(
                            onClick = { requesterForFirstChannel(channels, ::requesterFor) },
                            selected = selected,
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
                    itemsIndexed(list, key = { _, g -> g.id }) { _, group ->
                        val selected = group.id == selectedGroup
                        ListItem(
                            onClick = { requesterForFirstChannel(channels, ::requesterFor) },
                            selected = selected,
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
                // Preview and what's on.
                Row(modifier = Modifier.height(136.dp)) {
                    Preview(live = live, attached = watching == null, onClick = ::watchPreview)
                    Spacer(Modifier.width(16.dp))
                    val pick = gridPick
                    if (gridFrom != null && pick != null) {
                        // The full guide: what's highlighted in it.
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

                val from = gridFrom
                if (from != null) {
                    // The full guide: this group's channels, yesterday to tomorrow.
                    val gridChannels = channels.orEmpty().ifEmpty { listOf(from) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = groupName(selectedGroup).ifBlank { "Full guide" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp).width(ChannelCellWidth - 8.dp),
                            style = WiptvType.control.copy(color = WiptvColors.text),
                        )
                        KeyHints(listOf("◀ ▶ through the day", "▲ ▼ channels", "OK watch", "≡ favourite", "Back to Live TV"))
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
                // The selected group's channels, as a guide.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = groupName(selectedGroup),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp).width(ChannelCellWidth - 8.dp),
                        style = WiptvType.control.copy(color = WiptvColors.text),
                    )
                    Text(
                        text = when {
                            channelsError != null -> channelsError!!
                            selectedGroup == null -> ""
                            channels == null -> "Loading channels…"
                            channels.isEmpty() && selectedGroup == FAVOURITES_ID ->
                                "No favourites yet. Press Menu (≡) on a channel to add it."
                            channels.isEmpty() -> "No channels in this group"
                            else -> ""
                        },
                        style = TextStyle(fontSize = 12.sp, color = if (channelsError != null) Palette.warn else Palette.quiet),
                    )
                }
                TimeRuler(windowStart = guideStart(now))
                LazyColumn(
                    state = channelList,
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties {
                            enter = {
                                val last = selectedGroup?.let { lastChannelInGroup[it] }
                                val visible = last != null &&
                                    channelList.layoutInfo.visibleItemsInfo.any { it.key == last }
                                if (visible) requesterFor(last!!) else FocusRequester.Default
                            }
                        }
                        .focusGroup(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(channels.orEmpty(), key = { _, c -> c.id }) { index, channel ->
                        // Each row loads its own guide as it comes on screen, and again every half hour.
                        LaunchedEffect(channel.id, now / (30 * 60_000L)) { guide.ensure(channel) }
                        GuideRow(
                            channel = channel,
                            programmes = guide.programmes(channel.id),
                            windowStart = guideStart(now),
                            now = now,
                            playing = live.channel?.id == channel.id,
                            favourite = channel.id in favouriteIds,
                            // First OK previews the channel; OK on the channel already in the preview goes full screen.
                            onClick = {
                                previewSource = (selectedGroup ?: "") to channels.orEmpty()
                                if (live.channel?.id == channel.id) watchPreview() else live.play(login, format, channel)
                            },
                            onMenu = { toggleFavourite(channel) },
                            onGuide = {
                                gridPick = channel to null
                                gridFrom = channel
                            },
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
                if (id == FAVOURITES_ID) favourites
                else channelsByGroup[id] ?: Xtream.liveChannels(login, id).also { channelsByGroup[id] = it }
            },
            groupOf = { channel ->
                val inGroup = channelsByGroup[channel.groupId]
                when {
                    inGroup != null && inGroup.any { it.id == channel.id } ->
                        ChannelGroup(channel.groupId, groupName(channel.groupId), inGroup)
                    favourites.any { it.id == channel.id } -> ChannelGroup(FAVOURITES_ID, "Favourites", favourites)
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
                    previewSource = groupId to (list ?: listOf(last))
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
        Box(
            modifier = Modifier.selectedMarker(selected, WiptvColors.focus.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
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

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
import kotlinx.coroutines.launch
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
fun LiveScreen(login: Login, update: UpdateHandle, onSignOut: () -> Unit) {
    val context = LocalContext.current
    var account by remember { mutableStateOf<Account?>(null) }
    var groups by remember { mutableStateOf<List<Group>?>(null) }
    var groupsError by remember { mutableStateOf<String?>(null) }
    var groupsAttempt by remember { mutableIntStateOf(0) }
    val channelsByGroup = remember { mutableStateMapOf<String, List<Channel>>() }
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
        groupsError = null
        try {
            val loaded = Xtream.liveGroups(login)
            groups = loaded
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

    // When the app opens, the preview picks up the last channel you watched.
    LaunchedEffect(channels) {
        if (live.channel != null) return@LaunchedEffect
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
            .background(Palette.background)
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
            .padding(start = 24.dp, end = 24.dp, top = 18.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Groups.
            Column(modifier = Modifier.width(190.dp).fillMaxHeight()) {
                Text(
                    text = "Live TV",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                )
                Spacer(Modifier.height(6.dp))
                UpdatePill(update)
                Spacer(Modifier.height(8.dp))
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
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.glow),
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
                            Text(
                                text = "Edit groups",
                                style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
                            )
                        }
                    }
                    item(key = "account") {
                        ListItem(onClick = { showAccount = true }, selected = false) {
                            Text(
                                text = "Account & sign out",
                                style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Preview and what's on.
                Row(modifier = Modifier.height(176.dp)) {
                    Preview(live = live, attached = watching == null, onClick = ::watchPreview)
                    Spacer(Modifier.width(16.dp))
                    NowPanel(
                        channel = live.channel,
                        programmes = live.channel?.let { guide.programmes(it.id) },
                        now = now,
                        modifier = Modifier.weight(1f).padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))

                // The selected group's channels, as a guide.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = groupName(selectedGroup),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp).width(ChannelCellWidth - 8.dp),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                    )
                    Text(
                        text = when {
                            channelsError != null -> channelsError!!
                            selectedGroup == null -> ""
                            channels == null -> "Loading channels…"
                            channels.isEmpty() && selectedGroup == FAVOURITES_ID ->
                                "No favourites yet. Hold OK on a channel to add it."
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
                    contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
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
                            // Holding OK adds the channel to Favourites, or takes it off.
                            onLongClick = {
                                favourites = FavouritePrefs.toggle(favourites, channel)
                                favouritePrefs.save(favourites)
                                val added = favourites.any { it.id == channel.id }
                                android.widget.Toast.makeText(
                                    context,
                                    if (added) "Added ${channel.name} to Favourites" else "Removed ${channel.name} from Favourites",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
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
            ensureGuide = { guide.ensure(it) },
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

/** A row in the groups list: quiet until focused; the selected group keeps a soft highlight. */
@Composable
private fun ListItem(
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.tile else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = Palette.text,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
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
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(width = 313.dp, height = 176.dp)
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

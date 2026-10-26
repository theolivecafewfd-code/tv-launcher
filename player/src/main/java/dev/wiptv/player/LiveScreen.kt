package dev.wiptv.player

import android.content.Context
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

/**
 * Live TV: groups on the left; on the right, a small preview with what's on, and below it the
 * group's channels as a simple two-hour guide. Moving around leaves the preview alone: OK on a
 * channel previews it, and OK again on the channel in the preview goes full screen.
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
    var watching by remember { mutableStateOf<Int?>(null) }

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
            if (loaded.none { it.id == selectedGroup }) selectedGroup = loaded.firstOrNull()?.id
            val index = loaded.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
            groupList.scrollToItem(index)
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

    val channels = selectedGroup?.let { channelsByGroup[it] }

    // When the app opens, the preview picks up the last channel you watched.
    LaunchedEffect(channels) {
        if (live.channel != null) return@LaunchedEffect
        val last = LastWatched.channel(context)
        channels?.firstOrNull { it.id == last }?.let { live.play(login, format, it) }
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
                }
                val list = groups.orEmpty()
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
                    item(key = "account") {
                        Spacer(Modifier.height(8.dp))
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
                    Preview(live = live, attached = watching == null)
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
                    val groupName = groups?.firstOrNull { it.id == selectedGroup }?.name.orEmpty()
                    Text(
                        text = groupName,
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
                            // First OK previews the channel; OK on the channel already in the preview goes full screen.
                            onClick = {
                                if (live.channel?.id == channel.id) watching = index else live.play(login, format, channel)
                            },
                            modifier = Modifier
                                .focusRequester(requesterFor(channel.id))
                                .onFocusChanged { if (it.isFocused) lastChannelInGroup[channel.groupId] = channel.id },
                        )
                    }
                }
            }
        }
    }

    val playing = watching
    if (playing != null && channels != null) {
        WatchScreen(
            login = login,
            format = format,
            live = live,
            channels = channels,
            groupName = groups?.firstOrNull { it.id == selectedGroup }?.name.orEmpty(),
            startIndex = playing,
            nowShowing = { guide.nowShowing(it) },
            onChannel = { LastWatched.save(context, it) },
            onClose = { last ->
                watching = null
                pendingFocus = last.id
            },
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

/** The small picture at the top, with loading and error notes over it. */
@Composable
private fun Preview(live: LivePlayer, attached: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 313.dp, height = 176.dp)
            .clip(RoundedCornerShape(10.dp))
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
    }
}

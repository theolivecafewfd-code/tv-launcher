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
import androidx.compose.ui.focus.focusRestorer
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
import kotlinx.coroutines.delay

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
 * Live TV: groups on the left, the selected group's channels on the right. Moving through the
 * groups shows each one's channels; OK on a channel plays it full screen.
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

    // A channel to move the focus to once it's on screen: the last one watched when the app
    // opens, and whichever channel you ended on after watching. Null once it's done.
    var pendingFocus by remember { mutableStateOf<Long?>(LastWatched.channel(context)) }

    val groupList = rememberLazyListState()
    val channelList = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val channelRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    fun requesterFor(id: Long) = channelRequesters.getOrPut(id) { FocusRequester() }

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
        } catch (e: Exception) {
            channelsError = e.message ?: "Couldn't load this group."
        }
    }

    val channels = selectedGroup?.let { channelsByGroup[it] }

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
            .padding(start = 32.dp, end = 32.dp, top = 24.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Groups.
            Column(modifier = Modifier.width(230.dp).fillMaxHeight()) {
                Text(
                    text = "Live TV",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                )
                Spacer(Modifier.height(10.dp))
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
                    modifier = Modifier.weight(1f).focusRestorer(),
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

            // Channels in the selected group.
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val groupName = groups?.firstOrNull { it.id == selectedGroup }?.name.orEmpty()
                    Text(
                        text = groupName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                    )
                    UpdatePill(update)
                }
                Text(
                    text = when {
                        channelsError != null -> channelsError!!
                        selectedGroup == null -> ""
                        channels == null -> "Loading channels…"
                        channels.isEmpty() -> "No channels in this group"
                        else -> "${channels.size} channels"
                    },
                    style = TextStyle(fontSize = 12.sp, color = if (channelsError != null) Palette.warn else Palette.quiet),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    state = channelList,
                    modifier = Modifier.weight(1f).focusRestorer(),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(channels.orEmpty(), key = { _, c -> c.id }) { index, channel ->
                        ListItem(
                            onClick = { watching = index },
                            selected = false,
                            modifier = Modifier.focusRequester(requesterFor(channel.id)),
                        ) {
                            ChannelRow(channel)
                        }
                    }
                }
            }
        }
    }

    val playing = watching
    if (playing != null && channels != null && account != null) {
        WatchScreen(
            login = login,
            format = account!!.liveFormat,
            channels = channels,
            groupName = groups?.firstOrNull { it.id == selectedGroup }?.name.orEmpty(),
            startIndex = playing,
            onChannel = { LastWatched.save(context, it) },
            onClose = { last ->
                watching = null
                pendingFocus = last.id
            },
        )
    } else if (playing != null && account == null) {
        // The account details (stream format) haven't arrived; try once more, then play as .ts.
        LaunchedEffect(playing) {
            account = runCatching { Xtream.signIn(login, withCatalogue = false) }.getOrNull()
                ?: Account("Unknown", null, 0, 1, false, null, null, null, null)
        }
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

/** Number, logo and name. */
@Composable
private fun ChannelRow(channel: Channel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = channel.number.toString(),
            modifier = Modifier.width(40.dp),
            style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
        )
        Box(
            modifier = Modifier.size(width = 44.dp, height = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** A row in either list: quiet until focused; the selected group keeps a soft highlight. */
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

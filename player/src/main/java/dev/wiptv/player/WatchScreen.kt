package dev.wiptv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import dev.wiptv.design.ListRow
import dev.wiptv.design.glass
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.Glass
import dev.wiptv.design.WiptvType
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.PlaybackProgress
import dev.wiptv.design.PlaybackPanel
import dev.wiptv.design.KeyHints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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

/** What's over the picture: nothing, a group's channels, or the list of groups. */
private enum class Overlay { NONE, CHANNELS, GROUPS }

/** A group of channels to play through: its id, name and channels. */
class ChannelGroup(val id: String, val name: String, val channels: List<Channel>)

/**
 * Full-screen playback, using the same player as the preview.
 * - Up and down change channel within the group (up goes to the channel above in the list).
 * - Left opens the group's channel list over the picture; Left again shows the groups, so you can
 *   pick a channel from any group. OK on a channel switches to it.
 * - Right jumps back to the channel you were on before, whichever group it's in.
 * - OK shows the channel info. Menu (≡) adds the channel (or the one highlighted in the list) to
 *   Favourites, or takes it off. Back returns to the guide with the channel still in the preview.
 */
@Composable
fun WatchScreen(
    login: Login,
    format: String,
    live: LivePlayer,
    start: ChannelGroup,
    startIndex: Int,
    groups: List<Pair<String, String>>,
    loadGroup: suspend (String) -> List<Channel>,
    groupOf: (Channel) -> ChannelGroup,
    nowShowing: (Channel) -> Programme?,
    /** What's on next, for the info bar. */
    upNext: (Channel) -> Programme? = { null },
    ensureGuide: suspend (Channel) -> Unit,
    isFavourite: (Channel) -> Boolean,
    onToggleFavourite: (Channel) -> Unit,
    onChannel: (Channel) -> Unit,
    onClose: (Channel, String) -> Unit,
) {
    // The channel highlighted in the channel list overlay, for Menu (≡).
    var highlightedInList by remember { mutableStateOf<Channel?>(null) }
    var group by remember { mutableStateOf(start) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, start.channels.lastIndex)) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val itemRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    fun requesterFor(id: Long) = itemRequesters.getOrPut(id) { FocusRequester() }
    val groupRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun groupRequesterFor(id: String) = groupRequesters.getOrPut(id) { FocusRequester() }
    val channels = group.channels
    val channel = channels[index.coerceIn(0, channels.lastIndex)]

    // The group shown in the channel list overlay, which can differ from the one playing
    // while you browse other groups.
    var browseId by remember { mutableStateOf(start.id) }
    var browseName by remember { mutableStateOf(start.name) }
    var browseList by remember { mutableStateOf<List<Channel>?>(start.channels) }

    /** Plays a channel, moving to its group if it isn't in the one playing. */
    fun goToChannel(target: Channel, from: ChannelGroup? = null) {
        val inCurrent = channels.indexOfFirst { it.id == target.id }
        if (from == null && inCurrent >= 0) {
            index = inCurrent
            return
        }
        val newGroup = from ?: groupOf(target)
        group = newGroup
        index = newGroup.channels.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
    }

    fun move(step: Int) {
        if (channels.size > 1) index = (index + step + channels.size) % channels.size
    }

    fun openChannelList() {
        browseId = group.id
        browseName = group.name
        browseList = group.channels
        overlay = Overlay.CHANNELS
    }

    // Play the current channel whenever it changes (nothing restarts if it's already playing).
    LaunchedEffect(channel.id) {
        live.play(login, format, channel)
        onChannel(channel)
        infoTick++
        ensureGuide(channel)
    }

    // Load a group's channels when you pick it from the groups overlay.
    LaunchedEffect(browseId) {
        if (browseList == null) {
            browseList = try {
                loadGroup(browseId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // The info bar shows for a few seconds after a change or an OK press.
    LaunchedEffect(infoTick) {
        showInfo = true
        delay(4000)
        showInfo = false
    }

    // Focus: the channel playing in the channel list, the group in the groups list, or the picture.
    LaunchedEffect(overlay, browseList) {
        when (overlay) {
            Overlay.CHANNELS -> {
                val list = browseList ?: return@LaunchedEffect
                val target = list.indexOfFirst { it.id == channel.id }
                listState.scrollToItem((target - 4).coerceAtLeast(0))
                val focusId = list.getOrNull(target.coerceAtLeast(0))?.id ?: return@LaunchedEffect
                repeat(20) {
                    if (runCatching { requesterFor(focusId).requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(50)
                }
            }
            Overlay.GROUPS -> {
                val target = groups.indexOfFirst { it.first == browseId }.coerceAtLeast(0)
                groupListState.scrollToItem((target - 4).coerceAtLeast(0))
                val focusId = groups.getOrNull(target)?.first ?: return@LaunchedEffect
                repeat(20) {
                    if (runCatching { groupRequesterFor(focusId).requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(50)
                }
            }
            Overlay.NONE -> repeat(20) {
                if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        }
    }

    Dialog(
        onDismissRequest = {
            when (overlay) {
                Overlay.GROUPS -> overlay = Overlay.CHANNELS
                Overlay.CHANNELS -> overlay = Overlay.NONE
                Overlay.NONE -> onClose(channel, group.id)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent.keyCode
                    if (key == AndroidKeyEvent.KEYCODE_MENU) {
                        if (event.type == KeyEventType.KeyUp) {
                            when (overlay) {
                                Overlay.NONE -> { onToggleFavourite(channel); infoTick++ }
                                Overlay.CHANNELS -> highlightedInList?.let(onToggleFavourite)
                                Overlay.GROUPS -> Unit
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (overlay) {
                        // The lists handle their own up and down; left and right move between them.
                        Overlay.CHANNELS -> when (key) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { overlay = Overlay.GROUPS; true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { overlay = Overlay.NONE; true }
                            else -> false
                        }
                        Overlay.GROUPS -> when (key) {
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { overlay = Overlay.CHANNELS; true }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> true
                            else -> false
                        }
                        Overlay.NONE -> when (key) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { move(-1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { move(1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { openChannelList(); true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> {
                                live.previous?.let { goToChannel(it) }
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_INFO -> { infoTick++; true }
                            else -> false
                        }
                    }
                },
        ) {
            PlayerSurface(live, attached = true, modifier = Modifier.fillMaxSize())

            // Loading or a problem, in the middle of the screen.
            val message = live.error ?: if (live.buffering) "Loading ${ChannelLabels.of(channel.name).name}…" else null
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .glass(WiptvShapes.pill, Glass.OverVideo)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (live.error != null) Palette.warn else Palette.text,
                    ),
                )
            }

            AnimatedVisibility(
                visible = overlay == Overlay.NONE && (showInfo || live.error != null),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                InfoBar(
                    channel = channel,
                    programme = nowShowing(channel),
                    next = upNext(channel),
                    groupName = group.name,
                    index = index,
                    total = channels.size,
                    previous = live.previous,
                    favourite = isFavourite(channel),
                )
            }

            if (overlay == Overlay.CHANNELS) {
                ChannelListOverlay(
                    channels = browseList.orEmpty(),
                    loading = browseList == null,
                    groupName = browseName,
                    playingId = channel.id,
                    listState = listState,
                    requesterFor = ::requesterFor,
                    nowShowing = nowShowing,
                    ensureGuide = ensureGuide,
                    isFavourite = isFavourite,
                    onHighlight = { highlightedInList = it },
                    onPick = { picked ->
                        val list = browseList.orEmpty()
                        list.getOrNull(picked)?.let { goToChannel(it, ChannelGroup(browseId, browseName, list)) }
                        overlay = Overlay.NONE
                    },
                )
            }

            if (overlay == Overlay.GROUPS) {
                GroupListOverlay(
                    groups = groups,
                    currentId = browseId,
                    listState = groupListState,
                    requesterFor = ::groupRequesterFor,
                    onPick = { (id, name) ->
                        if (id != browseId) {
                            browseId = id
                            browseName = name
                            browseList = null
                        }
                        overlay = Overlay.CHANNELS
                    },
                )
            }
        }
    }
}

/** The group's channels down the left of the picture, with what's on each. */
@Composable
private fun ChannelListOverlay(
    channels: List<Channel>,
    loading: Boolean,
    groupName: String,
    playingId: Long,
    listState: androidx.compose.foundation.lazy.LazyListState,
    requesterFor: (Long) -> FocusRequester,
    nowShowing: (Channel) -> Programme?,
    ensureGuide: suspend (Channel) -> Unit,
    isFavourite: (Channel) -> Boolean,
    onHighlight: (Channel) -> Unit,
    onPick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.75f))))
            .padding(top = 20.dp, start = 16.dp, end = 12.dp),
    ) {
        Text(
            text = groupName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
        )
        Text(
            text = if (loading) "Loading channels…" else "OK to switch · ≡ favourite · ◀ groups · Right or Back to close",
            style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 20.dp, start = 2.dp, end = 2.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, item ->
                LaunchedEffect(item.id) { ensureGuide(item) }
                val playing = item.id == playingId
                // The shared list row: the guide's soft fill and bar, no outline.
                ListRow(
                    onClick = { onPick(i) },
                    modifier = Modifier
                        .focusRequester(requesterFor(item.id))
                        .onFocusChanged { if (it.isFocused) onHighlight(item) },
                    contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                ) {
                    Text(
                        text = if (playing) "▶" else item.number.toString(),
                        modifier = Modifier.width(30.dp),
                        style = TextStyle(fontSize = 12.sp, color = if (playing) Palette.glow else Palette.quiet),
                    )
                    Column {
                        // The name with a trailing "| FHD |" shown as a small badge, as in the guide.
                        val label = remember(item.name) { ChannelLabels.of(item.name) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isFavourite(item)) "★ ${label.name}" else label.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (playing) Palette.glow else Palette.text,
                                ),
                            )
                            label.quality?.let { QualityBadge(it) }
                            if (item.catchUp) CatchUpBadge(short = true)
                        }
                        nowShowing(item)?.let { p ->
                            Text(
                                text = p.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The groups (Favourites first) down the left of the picture; OK shows a group's channels. */
@Composable
private fun GroupListOverlay(
    groups: List<Pair<String, String>>,
    currentId: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    requesterFor: (String) -> FocusRequester,
    onPick: (Pair<String, String>) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(top = 20.dp, start = 16.dp, end = 12.dp),
    ) {
        Text(
            text = "Groups",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
        )
        Text(
            text = "OK to see a group's channels · Right or Back to go back",
            style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 20.dp, start = 2.dp, end = 2.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(groups, key = { _, g -> g.first }) { _, item ->
                val current = item.first == currentId
                // The current group keeps a faint wash; focus is the shared list row's.
                ListRow(
                    onClick = { onPick(item) },
                    modifier = Modifier.focusRequester(requesterFor(item.first)),
                    selected = current,
                    contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    Text(
                        text = item.second,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

/**
 * Along the bottom of the picture, in the shared playback panel: the channel's number and logo,
 * its name (★ when a favourite, with its quality badge), what's on now with its times and how far
 * through it is, what's next, the clock, and a quiet line of what the remote does.
 */
@Composable
private fun InfoBar(
    channel: Channel,
    programme: Programme?,
    next: Programme?,
    groupName: String,
    index: Int,
    total: Int,
    previous: Channel?,
    favourite: Boolean,
) {
    val now = rememberNow()
    PlaybackPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = channel.number.toString(), style = WiptvType.screen.copy(fontWeight = FontWeight.Bold))
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 72.dp, height = 44.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                val label = remember(channel.name) { ChannelLabels.of(channel.name) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (favourite) "★ ${label.name}" else label.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = WiptvType.section.copy(fontWeight = FontWeight.SemiBold),
                    )
                    label.quality?.let { QualityBadge(it) }
                    if (channel.catchUp) CatchUpBadge()
                }
                if (programme != null) {
                    Text(
                        text = "${timeRange(programme)}  ${programme.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = WiptvType.body,
                    )
                }
                if (next != null) {
                    Text(
                        text = "Next  ${timeOf(next.start)}  ${next.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = WiptvType.metadata,
                    )
                }
            }
            Text(text = timeOf(now), style = WiptvType.control.copy(color = WiptvColors.secondary))
        }
        if (programme != null) {
            val length = (programme.end - programme.start).coerceAtLeast(1)
            PlaybackProgress(
                fraction = (now - programme.start).toFloat() / length,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        KeyHints(
            hints = listOfNotNull(
                "$groupName · ${index + 1} of $total",
                "▲▼ change",
                "◀ channels and groups",
                previous?.let { "▶ back to ${ChannelLabels.of(it.name).name}" },
                "≡ favourite",
            ),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

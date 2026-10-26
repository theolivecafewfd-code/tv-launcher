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

/**
 * Full-screen playback, using the same player as the preview.
 * - Up and down change channel within the group (up goes to the channel above in the list).
 * - Left opens the group's channel list over the picture; OK on a channel switches to it.
 * - Right jumps back to the channel you were on before.
 * - OK shows the channel info. Back returns to the guide with the channel still in the preview.
 */
@Composable
fun WatchScreen(
    login: Login,
    format: String,
    live: LivePlayer,
    channels: List<Channel>,
    groupName: String,
    startIndex: Int,
    nowShowing: (Channel) -> Programme?,
    ensureGuide: suspend (Channel) -> Unit,
    onChannel: (Channel) -> Unit,
    onClose: (Channel) -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, channels.lastIndex)) }
    var previous by remember { mutableStateOf<Int?>(null) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    var showList by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val itemRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    fun requesterFor(id: Long) = itemRequesters.getOrPut(id) { FocusRequester() }
    val channel = channels[index]

    fun goTo(target: Int) {
        if (target != index && target in channels.indices) {
            previous = index
            index = target
        }
    }

    fun move(step: Int) {
        if (channels.size > 1) goTo((index + step + channels.size) % channels.size)
    }

    // Play the current channel whenever it changes (nothing restarts if it's already playing).
    LaunchedEffect(index) {
        live.play(login, format, channel)
        onChannel(channel)
        infoTick++
        ensureGuide(channel)
    }

    // The info bar shows for a few seconds after a change or an OK press.
    LaunchedEffect(infoTick) {
        showInfo = true
        delay(4000)
        showInfo = false
    }

    // Focus: the channel list when it's open (on the channel playing), otherwise the picture.
    LaunchedEffect(showList) {
        if (showList) {
            listState.scrollToItem((index - 4).coerceAtLeast(0))
            repeat(20) {
                if (runCatching { requesterFor(channel.id).requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        } else {
            repeat(20) {
                if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (showList) showList = false else onClose(channel) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val key = event.nativeKeyEvent.keyCode
                    if (showList) {
                        // The list handles its own moving; Right closes it.
                        if (key == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) {
                            showList = false
                            true
                        } else {
                            false
                        }
                    } else {
                        when (key) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { move(-1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { move(1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { showList = true; true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> {
                                previous?.let { goTo(it) }
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
            val message = live.error ?: if (live.buffering) "Loading ${channel.name}…" else null
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (live.error != null) Palette.warn else Palette.text,
                    ),
                )
            }

            AnimatedVisibility(
                visible = !showList && (showInfo || live.error != null),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                InfoBar(
                    channel = channel,
                    programme = nowShowing(channel),
                    groupName = groupName,
                    index = index,
                    total = channels.size,
                    previous = previous?.let { channels.getOrNull(it) },
                )
            }

            if (showList) {
                ChannelListOverlay(
                    channels = channels,
                    groupName = groupName,
                    playingIndex = index,
                    listState = listState,
                    requesterFor = ::requesterFor,
                    nowShowing = nowShowing,
                    ensureGuide = ensureGuide,
                    onPick = { picked ->
                        goTo(picked)
                        showList = false
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
    groupName: String,
    playingIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    requesterFor: (Long) -> FocusRequester,
    nowShowing: (Channel) -> Programme?,
    ensureGuide: suspend (Channel) -> Unit,
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
            text = "OK to switch · Right or Back to close",
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
                val shape = RoundedCornerShape(8.dp)
                val playing = i == playingIndex
                Surface(
                    onClick = { onPick(i) },
                    modifier = Modifier.fillMaxWidth().focusRequester(requesterFor(item.id)),
                    shape = ClickableSurfaceDefaults.shape(shape = shape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Palette.text,
                        focusedContainerColor = Palette.tileFocused,
                        focusedContentColor = Palette.text,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (playing) "▶" else item.number.toString(),
                            modifier = Modifier.width(30.dp),
                            style = TextStyle(fontSize = 12.sp, color = if (playing) Palette.glow else Palette.quiet),
                        )
                        Column {
                            Text(
                                text = item.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (playing) Palette.glow else Palette.text,
                                ),
                            )
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
}

/** Channel number, logo, name, what's on, and the group along the bottom of the picture. */
@Composable
private fun InfoBar(
    channel: Channel,
    programme: Programme?,
    groupName: String,
    index: Int,
    total: Int,
    previous: Channel?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = channel.number.toString(),
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.text),
            )
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 72.dp, height = 44.dp),
                )
            }
            Column(modifier = Modifier.width(640.dp)) {
                Text(
                    text = channel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                )
                if (programme != null) {
                    Text(
                        text = "${timeRange(programme)}  ${programme.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, color = Palette.text),
                    )
                }
                val back = if (previous != null) " · ▶ back to ${previous.name}" else ""
                Text(
                    text = "$groupName · ${index + 1} of $total · ▲▼ change · ◀ channel list$back",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                )
            }
        }
    }
}

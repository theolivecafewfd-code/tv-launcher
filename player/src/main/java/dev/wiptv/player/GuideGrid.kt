package dev.wiptv.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.view.KeyEvent as AndroidKey

/** How much time the full guide shows across at once, and its steps. */
private const val GRID_WINDOW_MS = 150 * 60_000L
private const val SLOT_MS = 30 * 60_000L
private const val DAY_MS = 24 * 60 * 60_000L

/** A row in the full guide: as tall as the Live TV guide's. */
private val GridRowHeight = 48.dp

/**
 * Each channel's guide from yesterday to tomorrow, for the full guide, loaded a row at a time as
 * rows come on screen (a few at once) and kept while Live TV is open.
 */
@Stable
class FullGuideStore(private val login: Login) {
    private val entries = mutableStateMapOf<Long, List<Programme>>()
    private val loading = Semaphore(4)

    /** The channel's programmes, or null while they're loading. */
    fun programmes(channelId: Long): List<Programme>? = entries[channelId]

    suspend fun ensure(channel: Channel, now: Long) {
        if (channel.id in entries) return
        val loaded = try {
            loading.withPermit {
                Xtream.fullGuide(login, channel.id).ifEmpty { Xtream.shortGuide(login, channel.id) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Shown as "no programme information" rather than loading forever.
            emptyList()
        }
        entries[channel.id] = withoutOverlaps(loaded).filter { it.end > now - DAY_MS && it.start < now + DAY_MS }
    }
}

private fun halfHourOf(time: Long): Long = time - Math.floorMod(time, SLOT_MS)

/**
 * The full guide: the group's channels down the left and their programmes across, from 24 hours
 * back to 24 hours ahead, opening on [startIndex]'s programme on now. One programme has focus:
 * Left and Right move through the day, Up and Down change channel at the same time of day (Up
 * from the top row goes to the preview). OK on what's on now watches it in the preview (OK again
 * goes full screen); on an earlier programme marked ⟲ it replays it from the start (catch-up).
 * Menu (≡) adds the channel to Favourites or takes it off. Back is the caller's (Live TV).
 *
 * The whole grid is one focus target that moves its own highlight, so focus never gets lost
 * among programmes that scroll in and out of view.
 */
@Composable
fun GuideGrid(
    channels: List<Channel>,
    startIndex: Int,
    store: FullGuideStore,
    now: Long,
    playingId: Long?,
    favouriteIds: Set<Long>,
    onSelect: (Channel, Programme?) -> Unit,
    onWatch: (Channel) -> Unit,
    onCatchUp: (Channel, Programme) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The day's limits are fixed when the guide opens, so the edges don't creep while it's open.
    val opened = remember { System.currentTimeMillis() }
    val earliest = halfHourOf(opened) - DAY_MS
    val latest = halfHourOf(opened) + DAY_MS + SLOT_MS
    var row by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    // The time the highlight is at; the highlighted programme is the one on at that time.
    var anchor by remember { mutableLongStateOf(opened) }
    var viewStart by remember { mutableLongStateOf(halfHourOf(opened)) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    fun programmeAt(list: List<Programme>?, time: Long) = list?.firstOrNull { it.start <= time && time < it.end }
    val channel = channels.getOrNull(row)
    val selected = channel?.let { programmeAt(store.programmes(it.id), anchor) }

    /** Scrolls the times so [time] is on screen, with a little of what's before it. */
    fun show(time: Long) {
        if (time < viewStart || time >= viewStart + GRID_WINDOW_MS - SLOT_MS) {
            viewStart = (halfHourOf(time) - SLOT_MS).coerceIn(earliest, latest - GRID_WINDOW_MS)
        }
    }

    fun across(forward: Boolean) {
        val list = channel?.let { store.programmes(it.id) }.orEmpty()
        val current = programmeAt(list, anchor)
        val target = if (forward) {
            list.firstOrNull { it.start >= (current?.end ?: (anchor + 1)) }
        } else {
            list.lastOrNull { it.end <= (current?.start ?: anchor) }
        }
        // No programme that way (or no guide): half an hour at a time.
        val next = target?.start ?: (halfHourOf(anchor) + if (forward) SLOT_MS else -SLOT_MS)
        anchor = next.coerceIn(earliest, latest - 1)
        show(anchor)
    }

    fun ok() {
        val ch = channel ?: return
        val p = selected
        when {
            p == null || p.isOn(now) -> onWatch(ch)
            p.canCatchUp(ch, now) -> onCatchUp(ch, p)
            p.start > now -> Toast.makeText(context, "Starts at ${timeOf(p.start)}", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(context, "No catch-up for this one", Toast.LENGTH_SHORT).show()
        }
    }

    // Opens on the channel you came from, at what's on now, with the focus on the grid.
    LaunchedEffect(Unit) {
        listState.scrollToItem((row - 2).coerceAtLeast(0))
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    // Keeps the highlighted channel on screen, with a row or two around it.
    LaunchedEffect(row) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (row <= first || row >= last) listState.animateScrollToItem((row - 2).coerceAtLeast(0))
    }
    // The panel above describes the highlighted programme.
    LaunchedEffect(channel?.id, selected) { channel?.let { onSelect(it, selected) } }

    Column(modifier = modifier) {
        GridRuler(viewStart = viewStart, now = now)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focus)
                .onKeyEvent { event ->
                    val down = event.type == KeyEventType.KeyDown
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKey.KEYCODE_DPAD_LEFT -> {
                            if (down) across(forward = false)
                            true
                        }
                        AndroidKey.KEYCODE_DPAD_RIGHT -> {
                            if (down) across(forward = true)
                            true
                        }
                        AndroidKey.KEYCODE_DPAD_UP -> {
                            // From the top row, up to the preview.
                            if (row == 0) return@onKeyEvent false
                            if (down) {
                                anchor = maxOf(anchor, viewStart)
                                row--
                            }
                            true
                        }
                        AndroidKey.KEYCODE_DPAD_DOWN -> {
                            if (down && row < channels.size - 1) {
                                anchor = maxOf(anchor, viewStart)
                                row++
                            }
                            true
                        }
                        // On release, so one press acts once (holding OK doesn't reach the app reliably).
                        AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER -> {
                            if (event.type == KeyEventType.KeyUp) ok()
                            true
                        }
                        AndroidKey.KEYCODE_MENU -> {
                            if (event.type == KeyEventType.KeyUp) channel?.let(onToggleFavourite)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
            contentPadding = PaddingValues(bottom = 16.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, ch ->
                LaunchedEffect(ch.id) { store.ensure(ch, opened) }
                GridRow(
                    channel = ch,
                    programmes = store.programmes(ch.id),
                    viewStart = viewStart,
                    now = now,
                    highlighted = if (index == row) selected else null,
                    rowActive = index == row,
                    playing = ch.id == playingId,
                    favourite = ch.id in favouriteIds,
                )
            }
        }
    }
}

/** The day on the left, then the times every half hour across, with a mark at now. */
@Composable
private fun GridRuler(viewStart: Long, now: Long) {
    Row(modifier = Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = dayLabel(viewStart, now),
            modifier = Modifier.width(ChannelCellWidth).padding(start = 12.dp),
            style = WiptvType.control.copy(color = WiptvColors.text),
        )
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val slots = (GRID_WINDOW_MS / SLOT_MS).toInt()
            for (i in 0 until slots) {
                val time = viewStart + i * SLOT_MS
                Text(
                    // Midnight says which day it starts.
                    text = if (timeOf(time) == "00:00") "${dayLabel(time, now)} 00:00" else timeOf(time),
                    modifier = Modifier.offset(x = maxWidth * (i.toFloat() / slots) + 6.dp).align(Alignment.CenterStart),
                    style = WiptvType.hint.copy(color = WiptvColors.secondary),
                )
            }
        }
    }
}

/**
 * One channel's row: the channel, then its programmes in the time on screen. The highlighted
 * programme has the raised fill and a pale outline (focus); the row it's in also shows the
 * short bar by the channel, like the Live TV guide's focus. ▶ (in the preview) and ★
 * (favourite) stay on the channel whatever has focus.
 */
@Composable
private fun GridRow(
    channel: Channel,
    programmes: List<Programme>?,
    viewStart: Long,
    now: Long,
    highlighted: Programme?,
    rowActive: Boolean,
    playing: Boolean,
    favourite: Boolean,
) {
    val label = remember(channel.name) { ChannelLabels.of(channel.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GridRowHeight)
            .drawBehind {
                // A hairline between rows.
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(10.dp.toPx(), size.height - 0.5f),
                    end = Offset(size.width - 10.dp.toPx(), size.height - 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
                if (rowActive) {
                    val h = 24.dp.toPx()
                    drawRoundRect(
                        color = WiptvColors.rowFocusMarker,
                        topLeft = Offset(3.dp.toPx(), (size.height - h) / 2),
                        size = Size(3.dp.toPx(), h),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelCell(channel = channel, label = label, playing = playing, favourite = favourite)
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val viewEnd = viewStart + GRID_WINDOW_MS
            val nowAt = (now - viewStart).toFloat() / GRID_WINDOW_MS
            if (nowAt in 0f..1f) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowAt)
                        .width(2.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .background(WiptvColors.focus.copy(alpha = 0.55f), WiptvShapes.pill),
                )
            }
            val shown = programmes.orEmpty().filter { it.end > viewStart && it.start < viewEnd }
            when {
                programmes == null -> GridNote("Loading guide…")
                shown.isEmpty() -> GridNote("No programme information")
            }
            for (p in shown) {
                val from = (maxOf(p.start, viewStart) - viewStart).toFloat() / GRID_WINDOW_MS
                val to = (minOf(p.end, viewEnd) - viewStart).toFloat() / GRID_WINDOW_MS
                val cellWidth = (maxWidth * (to - from)).coerceAtLeast(2.dp)
                ProgrammeCell(
                    programme = p,
                    channel = channel,
                    now = now,
                    highlighted = p == highlighted,
                    modifier = Modifier.offset(x = maxWidth * from).width(cellWidth).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ProgrammeCell(programme: Programme, channel: Channel, now: Long, highlighted: Boolean, modifier: Modifier) {
    val on = programme.isOn(now)
    val past = programme.end <= now
    val catchUp = programme.canCatchUp(channel, now) && !on
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.padding(horizontal = 1.dp, vertical = 3.dp)) {
        if (highlighted) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(WiptvColors.rowFocus, shape)
                    .border(2.dp, WiptvColors.focus, shape),
            )
        } else {
            // A very faint line where a programme starts.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .fillMaxHeight(0.6f)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 9.dp)) {
            Text(
                text = (if (catchUp) "⟲ " else "") + programme.title,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (on || highlighted) FontWeight.Medium else FontWeight.Normal,
                    color = when {
                        highlighted || on -> WiptvColors.text
                        past && !catchUp -> WiptvColors.quiet
                        else -> WiptvColors.secondary
                    },
                ),
            )
            Text(
                text = timeOf(programme.start),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, color = WiptvColors.quiet),
            )
        }
    }
}

@Composable
private fun GridNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = text, style = WiptvType.hint)
    }
}

/**
 * Next to the preview while the full guide is open: the highlighted programme — its channel,
 * title, day and times, description, and what OK does.
 */
@Composable
fun GuideDetailPanel(channel: Channel, programme: Programme?, loaded: Boolean, now: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 36.dp, height = 22.dp),
                )
            }
            val label = remember(channel.name) { ChannelLabels.of(channel.name) }
            Text(
                text = "${channel.number} · ${label.name.uppercase()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(fontSize = 12.sp, letterSpacing = 0.6.sp, color = WiptvColors.secondary),
            )
            label.quality?.let { QualityBadge(it) }
            if (channel.catchUp) CatchUpBadge()
        }
        if (programme == null) {
            Text(
                text = if (loaded) "No programme information for this time." else "Loading guide…",
                style = WiptvType.metadata.copy(color = WiptvColors.quiet),
            )
            return@Column
        }
        val on = programme.isOn(now)
        Text(
            text = programme.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text),
        )
        val left = if (on) " · ${((programme.end - now) / 60_000).coerceAtLeast(0)} min left" else ""
        Text(
            text = "${dayLabel(programme.start, now)} · ${timeRange(programme)}$left",
            style = TextStyle(fontSize = 13.sp, color = WiptvColors.secondary),
        )
        if (programme.description.isNotBlank()) {
            Text(
                text = programme.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = WiptvColors.secondary),
            )
        }
        Text(
            text = when {
                on -> "OK to watch"
                programme.canCatchUp(channel, now) -> "⟲ OK to watch from the start"
                programme.start > now -> "Starts at ${timeOf(programme.start)}"
                else -> "No catch-up for this one"
            },
            style = TextStyle(fontSize = 12.sp, color = WiptvColors.quiet),
        )
    }
}

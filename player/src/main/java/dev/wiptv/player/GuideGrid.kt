package dev.wiptv.player

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.view.KeyEvent as AndroidKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How much time the guide shows across at once, and its steps. */
private const val GRID_WINDOW_MS = 120 * 60_000L
private const val SLOT_MS = 30 * 60_000L
private const val DAY_MS = 24 * 60 * 60_000L

/** How many days back the day pills (catch-up) go, besides today. */
private const val DAYS_BACK = 7

/** A channel's row in the guide, the logo tile at its start, and the gap between rows. */
private val GridRowHeight = 54.dp
private val LogoTileWidth = 112.dp
private val LogoGap = 12.dp
private val RowGap = 6.dp

/** Midnight at the start of [time]'s day, on the TV's clock. */
private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** The first moment the guide reaches back to: midnight a week ago. */
private fun guideEarliest(now: Long): Long = startOfDay(now - DAYS_BACK * DAY_MS)

/**
 * Each channel's guide from a week back to tomorrow, for the guide, loaded a row at a time as
 * rows come on screen (a few at once) and kept while Channels is open.
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
        val earliest = guideEarliest(now)
        entries[channel.id] = withoutOverlaps(loaded).filter { it.end > earliest && it.start < now + DAY_MS + SLOT_MS }
    }
}

private fun halfHourOf(time: Long): Long = time - Math.floorMod(time, SLOT_MS)

/**
 * The guide: day pills to jump back through the week (catch-up), what's highlighted, then the
 * group's channels down the left as logo tiles and their programmes across. It opens on
 * [startIndex]'s programme on now. One programme has focus (a white block): Left and Right move
 * through the day, Up and Down change channel at the same time of day (Up from the top row goes
 * to the day pills). OK on what's on now watches it full screen; on an earlier programme marked
 * ⟲ it replays it from the start (catch-up). Menu (≡) adds the channel to Favourites or takes it
 * off. Back is the caller's.
 *
 * The whole grid is one focus target that moves its own highlight, so focus never gets lost
 * among programmes that scroll in and out of view. A change of [refocus] puts focus back on it
 * (after full screen or catch-up closes).
 */
@Composable
fun GuideGrid(
    channels: List<Channel>,
    startIndex: Int,
    store: FullGuideStore,
    now: Long,
    favouriteIds: Set<Long>,
    onWatch: (Channel) -> Unit,
    onCatchUp: (Channel, Programme) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    refocus: Int = 0,
) {
    val context = LocalContext.current
    // The guide's limits are fixed when it opens, so the edges don't creep while it's open.
    val opened = remember { System.currentTimeMillis() }
    val earliest = remember { guideEarliest(opened) }
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

    /** A day pill: the same time of day on that day (now, for today), with focus back on the grid. */
    fun goToDay(dayStart: Long) {
        val today = startOfDay(opened)
        anchor = if (dayStart == today) System.currentTimeMillis() else dayStart + (opened - today)
        anchor = anchor.coerceIn(earliest, latest - 1)
        viewStart = (halfHourOf(anchor) - SLOT_MS).coerceIn(earliest, latest - GRID_WINDOW_MS)
        runCatching { focus.requestFocus() }
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

    // Opens on the channel you came from, at what's on now, with the focus on the grid; and
    // takes the focus back whenever [refocus] changes.
    LaunchedEffect(refocus) {
        if (refocus == 0) listState.scrollToItem((row - 2).coerceAtLeast(0))
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

    Column(modifier = modifier) {
        DayPills(
            today = startOfDay(opened),
            selectedDay = startOfDay(anchor),
            catchUp = channels.any { it.catchUp },
            onPick = ::goToDay,
        )
        Spacer(Modifier.height(10.dp))
        if (channel != null) {
            DetailLine(channel = channel, programme = selected, loaded = store.programmes(channel.id) != null, now = now)
            Spacer(Modifier.height(8.dp))
        }
        GridRuler(viewStart = viewStart, now = now)
        Spacer(Modifier.height(8.dp))
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
                            // From the top row, up to the day pills.
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
                        AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
                        AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKey.KEYCODE_MEDIA_PLAY -> {
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
            verticalArrangement = Arrangement.spacedBy(RowGap),
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
                    favourite = ch.id in favouriteIds,
                )
            }
        }
    }
}

/**
 * Today and the week before it, to jump the guide back a day at a time (catch-up). The day the
 * highlight is on is filled; focus is the bright fill.
 */
@Composable
private fun DayPills(today: Long, selectedDay: Long, catchUp: Boolean, onPick: (Long) -> Unit) {
    val format = remember { SimpleDateFormat("EEE d", Locale.UK) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (catchUp) "CATCH-UP" else "DAY",
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            style = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp, color = WiptvColors.quiet),
        )
        for (back in 0..DAYS_BACK) {
            // Midday that many days back, then that day's own midnight (safe across clock changes).
            val day = startOfDay(today - back * DAY_MS + DAY_MS / 2)
            val isSelected = day == selectedDay
            Surface(
                onClick = { onPick(day) },
                shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.pill),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) WiptvColors.elevated else WiptvColors.surface,
                    contentColor = if (isSelected) WiptvColors.text else WiptvColors.secondary,
                    focusedContainerColor = WiptvColors.focus,
                    focusedContentColor = WiptvColors.onLight,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            ) {
                Text(
                    text = if (back == 0) "Today" else format.format(Date(day)),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/** One line on the highlighted programme: title, day and times, what OK does, then its description. */
@Composable
private fun DetailLine(channel: Channel, programme: Programme?, loaded: Boolean, now: Long) {
    Column(modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 4.dp)) {
        if (programme == null) {
            Text(
                text = if (loaded) "No programme information for this time." else "Loading guide…",
                style = TextStyle(fontSize = 13.sp, color = WiptvColors.quiet),
            )
            return@Column
        }
        val on = programme.isOn(now)
        val left = if (on) " · ${((programme.end - now) / 60_000).coerceAtLeast(0)} min left" else ""
        val action = when {
            on -> "OK to watch"
            programme.canCatchUp(channel, now) -> "⟲ OK to watch from the start"
            programme.start > now -> "Starts at ${timeOf(programme.start)}"
            else -> "No catch-up for this one"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = programme.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
            )
            Text(
                text = "${dayLabel(programme.start, now)} · ${timeRange(programme)}$left · $action",
                maxLines = 1,
                style = TextStyle(fontSize = 13.sp, color = WiptvColors.secondary),
            )
        }
        if (programme.description.isNotBlank()) {
            Text(
                text = programme.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 13.sp, color = WiptvColors.quiet),
            )
        }
    }
}

/** The times every half hour across, above the programmes, with an accent mark at now. */
@Composable
private fun GridRuler(viewStart: Long, now: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayLabel(viewStart, now),
            maxLines = 1,
            modifier = Modifier.width(LogoTileWidth + LogoGap).padding(start = 4.dp),
            style = TextStyle(fontSize = 12.sp, color = WiptvColors.secondary),
        )
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val slots = (GRID_WINDOW_MS / SLOT_MS).toInt()
            for (i in 0 until slots) {
                val time = viewStart + i * SLOT_MS
                Text(
                    // Midnight says which day it starts.
                    text = if (timeOf(time) == "00:00") "${dayLabel(time, now)} 00:00" else timeOf(time),
                    modifier = Modifier.offset(x = maxWidth * (i.toFloat() / slots) + 6.dp).align(Alignment.CenterStart),
                    style = TextStyle(fontSize = 12.sp, color = WiptvColors.secondary),
                )
            }
            val nowAt = (now - viewStart).toFloat() / GRID_WINDOW_MS
            if (nowAt in 0f..1f) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowAt)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(WiptvColors.accent),
                )
            }
        }
    }
}

/**
 * One channel's row: its logo tile, then its programmes in the time on screen as rounded blocks.
 * The highlighted programme is white with dark text (focus); what's on now has an accent progress
 * bar. ★ (favourite) and ⟲ (catch-up) sit on the logo tile.
 */
@Composable
private fun GridRow(
    channel: Channel,
    programmes: List<Programme>?,
    viewStart: Long,
    now: Long,
    highlighted: Programme?,
    rowActive: Boolean,
    favourite: Boolean,
) {
    val label = remember(channel.name) { ChannelLabels.of(channel.name) }
    Row(
        modifier = Modifier.fillMaxWidth().height(GridRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogoGap),
    ) {
        LogoTile(channel = channel, label = label, active = rowActive, favourite = favourite)
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val viewEnd = viewStart + GRID_WINDOW_MS
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

/** The channel's logo on a dark tile (its name if there's no logo), lighter on the row with focus. */
@Composable
private fun LogoTile(channel: Channel, label: ChannelLabel, active: Boolean, favourite: Boolean) {
    var logoShown by remember(channel.logo) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(LogoTileWidth)
            .fillMaxHeight()
            .background(if (active) WiptvColors.elevated else WiptvColors.surface, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoShown) {
            Text(
                text = label.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.secondary, textAlign = TextAlign.Center),
            )
        }
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { logoShown = true },
                onError = { logoShown = false },
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (favourite) {
            Text(
                text = "★",
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 6.dp),
                style = TextStyle(fontSize = 11.sp, color = WiptvColors.text),
            )
        }
        if (channel.catchUp) {
            Text(
                text = "⟲",
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 3.dp, end = 6.dp),
                style = TextStyle(fontSize = 11.sp, color = WiptvColors.quiet),
            )
        }
    }
}

@Composable
private fun ProgrammeCell(programme: Programme, channel: Channel, now: Long, highlighted: Boolean, modifier: Modifier) {
    val on = programme.isOn(now)
    val past = programme.end <= now
    val catchUp = programme.canCatchUp(channel, now) && !on
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .padding(end = 4.dp)
            .background(if (highlighted) WiptvColors.focus else WiptvColors.surface, shape),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp)) {
            Text(
                text = (if (catchUp) "⟲ " else "") + programme.title,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (on || highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        highlighted -> WiptvColors.onLight
                        on -> WiptvColors.text
                        past && !catchUp -> WiptvColors.quiet
                        else -> WiptvColors.secondary
                    },
                ),
            )
            Text(
                text = timeRange(programme),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = if (highlighted) WiptvColors.onLight.copy(alpha = 0.7f) else WiptvColors.quiet,
                ),
            )
        }
        // What's on now: how far through it is.
        if (on) {
            val progress = ((now - programme.start).toFloat() / (programme.end - programme.start)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)
                    .height(3.dp)
                    .background(
                        if (highlighted) WiptvColors.onLight.copy(alpha = 0.2f) else WiptvColors.elevated,
                        RoundedCornerShape(2.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(if (highlighted) WiptvColors.onLight else WiptvColors.progress, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun GridNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 4.dp)
            .background(WiptvColors.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = TextStyle(fontSize = 13.sp, color = WiptvColors.quiet))
    }
}

package dev.wiptv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.wiptv.design.Glass
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MS = 24 * 60 * 60_000L

/** Whether a programme can be replayed now: it has started, and the provider kept a recording. */
fun Programme.canCatchUp(channel: Channel, now: Long): Boolean = channel.catchUp && archived && start < now

/**
 * One channel's schedule, from yesterday to tomorrow, in a panel over Live TV. It opens on what's
 * on now; Up goes back through earlier programmes and Down on to later ones, with a heading for
 * each day. OK on what's on now watches it live; OK on an earlier programme marked ⟲ plays it
 * from the start (catch-up). Back closes the panel.
 */
@Composable
fun ScheduleSheet(
    channel: Channel,
    login: Login,
    now: Long,
    onWatchLive: () -> Unit,
    onCatchUp: (Programme) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var programmes by remember(channel.id) { mutableStateOf<List<Programme>?>(null) }
    var failed by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        val loaded = try {
            Xtream.fullGuide(login, channel.id).ifEmpty { Xtream.shortGuide(login, channel.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            emptyList()
        }
        programmes = withoutOverlaps(loaded).filter { it.end > now - DAY_MS && it.start < now + DAY_MS }
    }

    val list = programmes
    val nowIndex = list?.indexOfFirst { it.isOn(now) }?.takeIf { it >= 0 }
        ?: list?.indexOfFirst { it.start > now }?.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState()
    val first = remember { FocusRequester() }
    LaunchedEffect(list) {
        if (list.isNullOrEmpty()) return@LaunchedEffect
        listState.scrollToItem((rowIndexOf(list, nowIndex, now) - 2).coerceAtLeast(0))
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.45f)) }) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(560.dp)
                    .glass(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp), Glass.Sheet)
                    .padding(start = 24.dp, end = 20.dp, top = 22.dp),
            ) {
                Text(text = ChannelLabels.of(channel.name).name, style = WiptvType.section, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (channel.catchUp) "⟲ marks programmes you can watch again from the start." else "This channel doesn't keep catch-up.",
                    style = WiptvType.hint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                when {
                    list == null -> Text("Loading the schedule…", style = WiptvType.metadata)
                    list.isEmpty() -> Text(
                        if (failed) "Couldn't load the schedule." else "No schedule for this channel.",
                        style = WiptvType.metadata,
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(scheduleRows(list, now), key = { it.key }) { row ->
                            when (row) {
                                is ScheduleRow.Day -> Text(
                                    text = row.label,
                                    style = WiptvType.control.copy(color = WiptvColors.quiet),
                                    modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp),
                                )
                                is ScheduleRow.Show -> ProgrammeRow(
                                    programme = row.programme,
                                    now = now,
                                    catchUp = row.programme.canCatchUp(channel, now),
                                    onClick = {
                                        val p = row.programme
                                        when {
                                            p.isOn(now) -> onWatchLive()
                                            p.canCatchUp(channel, now) -> onCatchUp(p)
                                            p.start > now -> android.widget.Toast.makeText(context, "Not on yet", android.widget.Toast.LENGTH_SHORT).show()
                                            else -> android.widget.Toast.makeText(context, "No catch-up for this one", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = if (row.index == nowIndex) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class ScheduleRow(val key: String) {
    class Day(val label: String) : ScheduleRow("day:$label")
    class Show(val programme: Programme, val index: Int) : ScheduleRow("p:${programme.start}")
}

/** The programmes with a heading before each day ("Yesterday", "Today", "Tomorrow"). */
private fun scheduleRows(list: List<Programme>, now: Long): List<ScheduleRow> {
    val out = mutableListOf<ScheduleRow>()
    var lastDay: String? = null
    list.forEachIndexed { i, p ->
        val day = dayLabel(p.start, now)
        if (day != lastDay) {
            out += ScheduleRow.Day(day)
            lastDay = day
        }
        out += ScheduleRow.Show(p, i)
    }
    return out
}

/** Where programme [index] sits among the rows, headings included. */
private fun rowIndexOf(list: List<Programme>, index: Int, now: Long): Int =
    scheduleRows(list, now).indexOfFirst { it is ScheduleRow.Show && it.index == index }.coerceAtLeast(0)

private fun dayLabel(time: Long, now: Long): String {
    fun dayOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    return when (dayOf(time) - dayOf(now)) {
        0 -> "Today"
        -1 -> "Yesterday"
        1 -> "Tomorrow"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.UK).format(Date(time))
    }
}

/** One programme: time, title, and on focus its description. Focus is the guide's soft fill and bar. */
@Composable
private fun ProgrammeRow(programme: Programme, now: Long, catchUp: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val live = programme.isOn(now)
    val past = programme.end <= now
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiptvColors.text,
            focusedContainerColor = WiptvColors.rowFocus,
            focusedContentColor = WiptvColors.text,
            pressedContainerColor = WiptvColors.rowFocus,
            pressedContentColor = WiptvColors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (focused) {
                        val h = 24.dp.toPx()
                        drawRoundRect(
                            color = WiptvColors.rowFocusMarker,
                            topLeft = Offset(3.dp.toPx(), 10.dp.toPx()),
                            size = Size(3.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                }
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = clock.format(Date(programme.start)),
                style = WiptvType.metadata.copy(color = if (past && !catchUp) WiptvColors.quiet else WiptvColors.secondary),
                modifier = Modifier.width(46.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = programme.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = WiptvType.body.copy(
                            fontWeight = if (focused || live) FontWeight.Medium else FontWeight.Normal,
                            color = if (past && !catchUp && !focused) WiptvColors.quiet else WiptvColors.text,
                        ),
                    )
                    when {
                        live -> Text("Now", style = WiptvType.hint.copy(color = WiptvColors.progress, fontWeight = FontWeight.Medium))
                        catchUp -> Text("⟲", style = WiptvType.metadata.copy(color = WiptvColors.secondary))
                    }
                }
                if (focused) {
                    val hint = when {
                        live -> "OK to watch live"
                        catchUp -> "OK to watch from the start"
                        else -> null
                    }
                    if (programme.description.isNotBlank()) {
                        Text(
                            text = programme.description,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = WiptvType.hint.copy(color = WiptvColors.secondary),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (hint != null) Text(hint, style = WiptvType.hint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

private val clock = SimpleDateFormat("HH:mm", Locale.UK)

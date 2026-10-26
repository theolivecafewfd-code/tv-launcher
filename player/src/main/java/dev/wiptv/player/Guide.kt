package dev.wiptv.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How much of the day the guide shows at once, and the width of the channel column. */
private const val WINDOW_MINUTES = 120
private const val WINDOW_MS = WINDOW_MINUTES * 60_000L
private const val HALF_HOUR_MS = 30 * 60_000L
internal val ChannelCellWidth = 176.dp

/**
 * What's on each channel, loaded a channel at a time as rows come on screen, and refreshed
 * every half hour. Kept for as long as the Live TV screen is open.
 */
@Stable
class GuideStore(private val login: Login) {
    private class Entry(val programmes: List<Programme>, val fetched: Long)

    private val entries = mutableStateMapOf<Long, Entry>()

    /** The channel's programmes, or null while they're loading. */
    fun programmes(channelId: Long): List<Programme>? = entries[channelId]?.programmes

    fun nowShowing(channel: Channel, now: Long = System.currentTimeMillis()): Programme? =
        programmes(channel.id)?.firstOrNull { it.isOn(now) }

    /** Loads the channel's guide unless a fresh copy is already here. */
    suspend fun ensure(channel: Channel) {
        val now = System.currentTimeMillis()
        val existing = entries[channel.id]
        if (existing != null && now - existing.fetched < HALF_HOUR_MS) return
        try {
            entries[channel.id] = Entry(Xtream.shortGuide(login, channel.id), now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No guide for this channel right now; show it as empty rather than loading forever.
            if (existing == null) entries[channel.id] = Entry(emptyList(), now)
        }
    }
}

/** The time now, updated every 30 seconds, for the guide's "now" line and progress bars. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Where the guide starts: the half hour before now. */
fun guideStart(now: Long): Long = now - now % HALF_HOUR_MS

private val clock = DateTimeFormatter.ofPattern("HH:mm")
internal fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clock)
internal fun timeRange(p: Programme): String = "${timeOf(p.start)}–${timeOf(p.end)}"

/** The times along the top of the guide, every half hour. */
@Composable
fun TimeRuler(windowStart: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Spacer(Modifier.width(ChannelCellWidth))
        BoxWithConstraints(modifier = Modifier.weight(1f).height(18.dp)) {
            val slots = WINDOW_MINUTES / 30
            for (i in 0 until slots) {
                Text(
                    text = timeOf(windowStart + i * HALF_HOUR_MS),
                    modifier = Modifier.offset(x = maxWidth * (i.toFloat() / slots) + 6.dp),
                    style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
                )
            }
        }
    }
}

/**
 * One channel in the guide: number, logo and name, then its programmes across the next two hours.
 * The whole row is one focusable item; OK on it plays the channel full screen.
 */
@Composable
fun GuideRow(
    channel: Channel,
    programmes: List<Programme>?,
    windowStart: Long,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(46.dp),
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
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Channel.
            Row(
                modifier = Modifier.width(ChannelCellWidth).padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The channel in the preview is marked with ▶.
                Text(
                    text = if (playing) "▶" else channel.number.toString(),
                    modifier = Modifier.width(28.dp),
                    style = TextStyle(fontSize = 12.sp, color = if (playing) Palette.glow else Palette.quiet),
                )
                Box(modifier = Modifier.size(width = 36.dp, height = 24.dp), contentAlignment = Alignment.Center) {
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
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                        color = if (playing) Palette.glow else Palette.text,
                    ),
                )
            }

            // Programmes.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))) {
                val windowEnd = windowStart + WINDOW_MS
                val shown = programmes.orEmpty().filter { it.end > windowStart && it.start < windowEnd }
                when {
                    programmes == null -> GuideNote("Loading guide…")
                    shown.isEmpty() -> GuideNote("No guide information")
                }
                for (p in shown) {
                    val from = (maxOf(p.start, windowStart) - windowStart).toFloat() / WINDOW_MS
                    val to = (minOf(p.end, windowEnd) - windowStart).toFloat() / WINDOW_MS
                    val on = p.isOn(now)
                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * from)
                            .width((maxWidth * (to - from) - 3.dp).coerceAtLeast(2.dp))
                            .fillMaxHeight()
                            .padding(vertical = 5.dp)
                            .background(
                                if (on) Palette.tileFocused else Palette.tile,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = p.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) Palette.text else Palette.quiet,
                            ),
                        )
                    }
                }
                // The "now" line.
                val nowAt = (now - windowStart).toFloat() / WINDOW_MS
                if (nowAt in 0f..1f) {
                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * nowAt)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Palette.glow.copy(alpha = 0.8f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = text, style = TextStyle(fontSize = 11.sp, color = Palette.quiet.copy(alpha = 0.7f)))
    }
}

/** Next to the preview: the channel, what's on now with a progress bar, and what's next. */
@Composable
fun NowPanel(channel: Channel?, programmes: List<Programme>?, now: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (channel == null) {
            Text(
                text = "Press OK on a channel to preview it here, and OK again to watch it full screen.",
                style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 44.dp, height = 28.dp),
                )
            }
            Text(
                text = "${channel.number}  ${channel.name}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.quiet),
            )
        }

        val current = programmes?.firstOrNull { it.isOn(now) }
        val next = programmes?.firstOrNull { it.start >= (current?.end ?: now) }
        when {
            current != null -> {
                Text(
                    text = current.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                )
                val minutesLeft = ((current.end - now) / 60_000).coerceAtLeast(0)
                Text(
                    text = "${timeRange(current)} · $minutesLeft min left",
                    style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                )
                val progress = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(3.dp)
                        .background(Palette.tile, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(220.dp * progress)
                            .background(Palette.glow, RoundedCornerShape(2.dp)),
                    )
                }
                if (current.description.isNotBlank()) {
                    Text(
                        text = current.description,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                    )
                }
            }
            programmes == null -> Text(text = "Loading guide…", style = TextStyle(fontSize = 13.sp, color = Palette.quiet))
            else -> Text(
                text = "No guide information for this channel",
                style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
            )
        }
        if (next != null) {
            Text(
                text = "Next  ${timeOf(next.start)}  ${next.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
            )
        }
    }
}

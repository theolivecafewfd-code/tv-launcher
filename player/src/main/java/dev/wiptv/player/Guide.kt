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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import dev.wiptv.design.selectedMarker
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
internal val ChannelCellWidth = 248.dp

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
                    style = WiptvType.hint.copy(color = WiptvColors.secondary),
                )
            }
        }
    }
}

/**
 * One channel in the guide: number, logo and name, then its programmes across the next two hours.
 * The whole row is one focusable item; OK on it previews the channel (OK again goes full screen).
 *
 * Styled like the categories list: no boxes at rest, a hairline between rows, programmes told
 * apart by spacing, a faint divider at each start and a soft wash on the one on now. Focus is the
 * one strong signal: a light wash and outline across the whole row.
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
    favourite: Boolean = false,
    onMenu: (() -> Unit)? = null,
) {
    val shape = WiptvShapes.card
    Surface(
        onClick = onClick,
        modifier = modifier
            // The remote's Menu (≡) button: adds the channel to Favourites, or takes it off.
            // (Holding OK didn't reach the app reliably on the Fire Stick.)
            .onPreviewKeyEvent { event ->
                if (onMenu == null || event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_MENU) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp) onMenu()
                true
            }
            // A hairline under each row instead of a filled box.
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(12.dp.toPx(), size.height - 0.5f),
                    end = Offset(size.width - 12.dp.toPx(), size.height - 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .fillMaxWidth()
            .height(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiptvColors.text,
            focusedContainerColor = WiptvColors.focus.copy(alpha = 0.10f),
            focusedContentColor = WiptvColors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = focusOutline(shape),
    ) {
        Row(
            // The channel in the preview gets a slim green bar on the left, separate from focus.
            modifier = Modifier.fillMaxSize().selectedMarker(playing, WiptvColors.positive),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel: number (▶ when it's in the preview), logo, name (★ when it's a favourite).
            Row(
                modifier = Modifier.width(ChannelCellWidth).padding(start = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (playing) "▶" else channel.number.toString(),
                    modifier = Modifier.width(24.dp),
                    style = TextStyle(fontSize = 12.sp, color = if (playing) WiptvColors.positive else WiptvColors.quiet),
                )
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 22.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(5.dp)),
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
                if (favourite) {
                    Text(text = "★", style = TextStyle(fontSize = 13.sp, color = WiptvColors.warning))
                }
                Text(
                    text = channel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                        color = WiptvColors.text,
                    ),
                )
            }

            // Programmes, across the same width as the time ruler above.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val windowEnd = windowStart + WINDOW_MS
                val shown = programmes.orEmpty().filter { it.end > windowStart && it.start < windowEnd }

                // Faint half-hour divisions, lined up with the ruler's times.
                val slots = WINDOW_MINUTES / 30
                for (slot in 1 until slots) {
                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * (slot.toFloat() / slots))
                            .width(1.dp)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp)
                            .background(Color.White.copy(alpha = 0.04f)),
                    )
                }

                when {
                    programmes == null -> GuideNote("Loading guide…")
                    shown.isEmpty() -> GuideNote("Programme information unavailable")
                }
                for (p in shown) {
                    val from = (maxOf(p.start, windowStart) - windowStart).toFloat() / WINDOW_MS
                    val to = (minOf(p.end, windowEnd) - windowStart).toFloat() / WINDOW_MS
                    val on = p.isOn(now)
                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * from)
                            .width((maxWidth * (to - from)).coerceAtLeast(2.dp))
                            .fillMaxHeight()
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .background(if (on) Color.White.copy(alpha = 0.06f) else Color.Transparent, WiptvShapes.small),
                    ) {
                        // A short divider where a programme starts (not for one already running at the left edge).
                        if (p.start > windowStart) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(1.dp)
                                    .fillMaxHeight(0.6f)
                                    .background(Color.White.copy(alpha = 0.14f)),
                            )
                        }
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp),
                        ) {
                            Text(
                                text = p.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                                    color = if (on) WiptvColors.text else WiptvColors.secondary,
                                ),
                            )
                            Text(
                                text = if (on) timeRange(p) else timeOf(p.start),
                                maxLines = 1,
                                style = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, color = WiptvColors.quiet),
                            )
                        }
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
                            .padding(vertical = 4.dp)
                            .background(WiptvColors.focus.copy(alpha = 0.55f), WiptvShapes.pill),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = text, style = WiptvType.hint)
    }
}

/** Next to the preview: the channel, what's on now with a progress bar, and what's next. */
@Composable
fun NowPanel(channel: Channel?, programmes: List<Programme>?, now: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (channel == null) {
            Text(
                text = "OK on a channel previews it here. OK again, or Play/Pause, watches it full screen. Menu (≡) adds a favourite.",
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
                    modifier = Modifier.size(width = 36.dp, height = 22.dp),
                )
            }
            Text(
                text = "▶ ${channel.number} · ${channel.name.uppercase()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 12.sp, letterSpacing = 0.6.sp, color = WiptvColors.secondary),
            )
        }

        val current = programmes?.firstOrNull { it.isOn(now) }
        val next = programmes?.firstOrNull { it.start >= (current?.end ?: now) }
        when {
            current != null -> {
                Text(
                    text = current.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text),
                )
                val minutesLeft = ((current.end - now) / 60_000).coerceAtLeast(0)
                val progress = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${timeRange(current)} · $minutesLeft min left",
                        style = TextStyle(fontSize = 13.sp, color = WiptvColors.secondary),
                    )
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(4.dp)
                            .background(WiptvColors.elevated, RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(120.dp * progress)
                                .background(WiptvColors.progress, RoundedCornerShape(2.dp)),
                        )
                    }
                }
                if (current.description.isNotBlank()) {
                    Text(
                        text = current.description,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = WiptvColors.secondary),
                    )
                }
            }
            programmes == null -> Text(text = "Loading guide…", style = TextStyle(fontSize = 13.sp, color = Palette.quiet))
            else -> Text(
                text = "Programme information unavailable",
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

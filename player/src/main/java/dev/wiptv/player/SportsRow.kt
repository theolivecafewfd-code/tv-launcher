package dev.wiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.wiptv.design.ListRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.wiptv.design.Glass
import dev.wiptv.design.SectionRow
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import dev.wiptv.design.glass
import dev.wiptv.design.rememberRowStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Premier League for Home's row and the Sports page, and the channels for each match. */
class SportsRowState(
    /** Today's matches still to finish, live ones first (Home's row); null until ESPN first answers. */
    val matches: List<Match>?,
    /** Every match today and the next two days, finished ones too, in kick-off order (the Sports page). */
    val all: List<Match>?,
    /** Whether the sports channels' guides have been read. */
    val guideReady: Boolean,
    /** The channel you chose for each match, else the one the guide says is showing it. */
    val channelFor: Map<String, Channel?>,
    /** The channels you chose, by match. */
    val picked: Map<String, Channel>,
    /** The channels to choose from for a match (empty until the guides are read). */
    val options: (Match) -> List<ChannelOption>,
    val setPick: (Match, Channel) -> Unit,
) {
    /** Home shows the row while there's a match today. */
    val visible: Boolean get() = !matches.isNullOrEmpty()
}

/**
 * The Premier League today and the next two days from ESPN, refreshed every minute while a match
 * is live (every five otherwise) while Home or Sports is open, and your provider's sports
 * channels' guides, read once there's a match to find a channel for and every half hour after.
 */
@Composable
fun rememberSports(
    login: Login,
    /** The Sports page: channels are looked for on every day shown, not just today. */
    allDays: Boolean = false,
): SportsRowState {
    val context = LocalContext.current
    val prefs = remember { SportsPrefs(context) }
    var matches by remember { mutableStateOf(SportsCache.matches) }
    var guide by remember { mutableStateOf(SportsCache.guide) }
    var pickedVersion by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val fresh = System.currentTimeMillis() - SportsCache.matchesAt < 55_000
            if (!fresh) {
                try {
                    val found = Espn.premierLeague()
                    SportsCache.matches = found
                    SportsCache.matchesAt = System.currentTimeMillis()
                    matches = found
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // ESPN didn't answer: keep what we had and try again later.
                }
            }
            delay(if (matches.orEmpty().any { it.live }) 60_000L else 5 * 60_000L)
        }
    }

    val needGuide = matches.orEmpty().any { !it.finished && (allDays || it.day == java.time.LocalDate.now()) }
    LaunchedEffect(needGuide) {
        if (!needGuide) return@LaunchedEffect
        while (true) {
            val kept = SportsCache.guide
            if (kept != null && System.currentTimeMillis() - kept.loadedAt < SportsCache.GUIDE_KEEP_MS) {
                guide = kept
            } else {
                try {
                    guide = SportsCache.loadGuide(login)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The provider didn't answer: nothing to choose from until the next try.
                    guide = SportsGuide(System.currentTimeMillis(), emptyList(), emptyMap())
                }
            }
            delay(SportsCache.GUIDE_KEEP_MS)
        }
    }

    // ESPN's list from an earlier day (the player left running overnight) drops yesterday's games.
    val today = java.time.LocalDate.now()
    val all = matches?.filter { !it.day.isBefore(today) }
    val shown = all?.filter { !it.finished && it.day == today }?.sortedWith(compareByDescending<Match> { it.live }.thenBy { it.kickOff })
    val current = guide
    // A saved pick, with the guide's own copy of the channel once it's read (its logo and catch-up up to date).
    val picked = remember(all, current, pickedVersion) {
        all.orEmpty().mapNotNull { m ->
            prefs.pick(m.id)?.let { p -> m.id to (current?.channels?.firstOrNull { it.id == p.id } ?: p) }
        }.toMap()
    }
    val channels = remember(all, current, picked) {
        all.orEmpty().filter { !it.finished }.associate { m ->
            m.id to (picked[m.id] ?: current?.let { SportsMatcher.channelFor(m, it) })
        }
    }
    return SportsRowState(
        matches = shown,
        all = all,
        guideReady = current != null,
        channelFor = channels,
        picked = picked,
        options = { m -> current?.let { SportsMatcher.options(m, it) }.orEmpty() },
        setPick = { m, channel ->
            prefs.setPick(m.id, channel, all.orEmpty().map { it.id }.toSet())
            pickedVersion++
        },
    )
}

private val CardWidth = 272.dp
private val CardHeight = 134.dp
private val CardShape = RoundedCornerShape(14.dp)

/**
 * The Sports row: "Premier League", then a card per match today, live ones first. A card shows
 * LIVE and the minute (or TODAY and the kick-off time), both teams with their badges, records and
 * the score, and the channel. OK watches the channel you chose for that match, or opens the list
 * of channels to choose from; Menu (≡) opens the list to change it. All fixtures comes last.
 */
@Composable
fun SportsShelf(
    state: SportsRowState,
    edge: androidx.compose.ui.unit.Dp,
    onWatchChannel: (Channel) -> Unit,
    /** Opens the Sports page (the "All fixtures" card). */
    onSeeAll: () -> Unit,
) {
    var choosing by remember { mutableStateOf<Match?>(null) }
    val rowStart = rememberRowStart()
    val matches = state.matches.orEmpty()
    SectionRow(
        title = "Premier League",
        edge = edge,
        contentStart = edge,
        spacing = 14.dp,
        rowModifier = rowStart.rowModifier(matches.firstOrNull()?.id ?: "all"),
    ) {
        items(matches, key = { it.id }) { match ->
            MatchCard(
                match = match,
                state = state,
                onWatch = onWatchChannel,
                onChoose = { choosing = match },
                modifier = rowStart.card(match.id),
            )
        }
        item(key = "all") {
            SeeAllCard(onClick = onSeeAll, modifier = rowStart.card("all"))
        }
    }
    choosing?.let { match ->
        ChannelPanel(match = match, state = state, onWatch = onWatchChannel, onDismiss = { choosing = null })
    }
}

/** The card after today's matches on Home: OK opens the Sports page with the next few days. */
@Composable
private fun SeeAllCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SportsCardFrame(onClick = onClick, modifier = modifier.width(180.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "All fixtures  ›", style = WiptvType.body.copy(fontWeight = FontWeight.SemiBold, color = WiptvColors.text))
            Spacer(Modifier.height(6.dp))
            Text(text = "Today and the next two days", style = WiptvType.hint)
        }
    }
}

/**
 * A match: LIVE and the minute, FULL TIME, or [label] ("TODAY" on Home) and the kick-off time;
 * both teams with their badges, records and score; and the channel. OK watches the channel you
 * chose for it, or [onChoose]s one; Menu (≡) always opens the choice, to change it.
 */
@Composable
internal fun MatchCard(
    match: Match,
    state: SportsRowState,
    onWatch: (Channel) -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "TODAY",
) {
    val picked = state.picked[match.id]
    val found = state.channelFor[match.id]
    SportsCardFrame(
        onClick = { if (picked != null) onWatch(picked) else onChoose() },
        modifier = modifier
            .width(CardWidth)
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Menu) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onChoose()
                true
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    match.live -> Badge(text = "● LIVE", fill = WiptvColors.live, colour = Color.White)
                    match.finished -> Badge(text = "FULL TIME", fill = WiptvColors.elevated, colour = WiptvColors.quiet)
                    else -> Badge(text = label, fill = WiptvColors.elevated, colour = WiptvColors.secondary)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (match.live) match.clock else kickOffTime(match.kickOff),
                    style = WiptvType.hint.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (match.live) WiptvColors.live else WiptvColors.text,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            TeamLine(match.home)
            Spacer(Modifier.height(4.dp))
            TeamLine(match.away)
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(WiptvColors.border))
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val channel = picked ?: found
                if (channel?.logo != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(channel.logo).size(48, 48).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = when {
                        picked != null -> "✓ " + ChannelLabels.of(picked.name).name
                        match.finished -> "Full time"
                        found != null -> ChannelLabels.of(found.name).name + "  ·  OK to choose"
                        !state.guideReady -> "Looking for channels…"
                        else -> "OK to choose a channel"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = WiptvType.hint.copy(color = if (channel != null) WiptvColors.secondary else WiptvColors.quiet),
                )
                if (picked != null) {
                    Text(text = "≡ change", style = WiptvType.hint.copy(fontSize = 11.sp, color = WiptvColors.quiet))
                }
            }
        }
    }
}

/** A team: its badge, its name and (once it has kicked off) its goals. */
@Composable
private fun TeamLine(side: Side) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(WiptvColors.elevated), contentAlignment = Alignment.Center) {
            if (side.logo != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(side.logo).size(60, 60).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = side.shortName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            style = WiptvType.body.copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
        )
        if (side.record != null) {
            Text(text = side.record, style = WiptvType.hint.copy(fontSize = 12.sp, color = WiptvColors.quiet))
        }
        Spacer(Modifier.weight(1f))
        if (side.score != null) {
            Text(
                text = side.score,
                style = WiptvType.body.copy(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = WiptvColors.text),
            )
        }
    }
}

/** A Sports card: a dark rounded panel with a faint edge; focus is the white outline. */
@Composable
private fun SportsCardFrame(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(CardHeight),
        shape = ClickableSurfaceDefaults.shape(shape = CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiptvColors.surface,
            focusedContainerColor = WiptvColors.elevated,
            pressedContainerColor = WiptvColors.elevated,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = focusOutline(CardShape),
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun Badge(text: String, fill: Color, colour: Color) {
    Text(
        text = text,
        modifier = Modifier.background(fill, WiptvShapes.pill).padding(horizontal = 8.dp, vertical = 2.dp),
        style = WiptvType.hint.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = colour, letterSpacing = 0.6.sp),
    )
}

private fun kickOffTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.UK).format(Date(time))

/**
 * The channels for a match, in a glass panel down the right: those whose guide names both teams
 * around kick-off first ("Showing this game"), then ones with football on, then your other sports
 * channels. Each row has the channel's logo and name, what's on at kick-off and its times. OK
 * remembers it for this match and watches it.
 */
@Composable
internal fun ChannelPanel(match: Match, state: SportsRowState, onWatch: (Channel) -> Unit, onDismiss: () -> Unit) {
    val options = remember(match.id, state.guideReady) { state.options(match) }
    val picked = state.picked[match.id]
    // The one you chose before, or the best guess, at the top of the list.
    val ordered = remember(options, picked) {
        val chosen = picked?.let { p -> options.firstOrNull { it.channel.id == p.id } ?: ChannelOption(p, null, "Your pick", showing = false) }
        listOfNotNull(chosen) + options.filter { it.channel.id != picked?.id }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(ordered.isNotEmpty()) {
        if (ordered.isEmpty()) return@LaunchedEffect
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxSize()
                    .padding(top = 16.dp, bottom = 16.dp, end = 16.dp)
                    .glass(WiptvShapes.panel, Glass.Drawer)
                    .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "${match.home.shortName} v ${match.away.shortName}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                    style = WiptvType.body.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = (if (match.live) "Live now · ${match.clock}" else "${matchDay(match)} · ${kickOffTime(match.kickOff)}") + "  ·  OK watches · Back closes",
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 10.dp),
                    style = WiptvType.hint,
                )
                when {
                    !state.guideReady -> Text(
                        text = "Looking through your sports channels' guides…",
                        modifier = Modifier.padding(start = 10.dp),
                        style = WiptvType.metadata.copy(color = WiptvColors.quiet),
                    )
                    ordered.isEmpty() -> Text(
                        text = "No sports channels found in your Live TV.",
                        modifier = Modifier.padding(start = 10.dp),
                        style = WiptvType.metadata.copy(color = WiptvColors.quiet),
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(ordered, key = { _, o -> o.channel.id }) { index, option ->
                            ChannelOptionRow(
                                option = option,
                                chosen = option.channel.id == picked?.id,
                                onClick = {
                                    state.setPick(match, option.channel)
                                    onDismiss()
                                    onWatch(option.channel)
                                },
                                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelOptionRow(option: ChannelOption, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRow(
        onClick = onClick,
        modifier = modifier,
        selected = chosen,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 52.dp, height = 36.dp).clip(WiptvShapes.small).background(WiptvColors.elevated),
            contentAlignment = Alignment.Center,
        ) {
            if (option.channel.logo != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(option.channel.logo).size(104, 72).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = (if (chosen) "✓ " else "") + ChannelLabels.of(option.channel.name).name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.control.copy(fontSize = 15.sp, lineHeight = 19.sp, color = WiptvColors.text),
            )
            val p = option.programme
            Text(
                text = if (p == null) "Nothing in the guide at kick-off" else "${p.title}  ·  ${kickOffTime(p.start)}–${kickOffTime(p.end)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.hint,
            )
        }
        Text(
            text = option.tag,
            maxLines = 1,
            modifier = Modifier
                .background(if (option.showing) WiptvColors.accent.copy(alpha = 0.18f) else WiptvColors.elevated, WiptvShapes.pill)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            style = WiptvType.hint.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (option.showing) WiptvColors.accent else WiptvColors.quiet),
        )
    }
}

/** "Today", "Tomorrow" or the weekday a match is on. */
private fun matchDay(match: Match): String {
    val today = java.time.LocalDate.now()
    return when (match.day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> match.day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", Locale.UK))
    }
}

package dev.wiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

/** The Premier League for Home's row and the Sports page, and saving a new sports group. */
class SportsRowState(
    val group: Group?,
    /** Today's matches still to finish, live ones first (Home's row); null until ESPN first answers. */
    val matches: List<Match>?,
    /** Every match today and the next two days, finished ones too, in kick-off order (the Sports page). */
    val all: List<Match>?,
    /** Whether the sports channels' guides have been read (so a missing channel means not found). */
    val guideReady: Boolean,
    val channelFor: Map<String, Channel?>,
    val setGroup: (Group) -> Unit,
) {
    /** Shown while there's a match today, or to choose the sports group the first time. */
    val visible: Boolean get() = group == null || !matches.isNullOrEmpty()
}

/**
 * The Premier League today and the next two days from ESPN, refreshed every minute while a match
 * is live (every five otherwise) while Home or Sports is open, with each match's channel found in
 * your sports group's guide.
 */
@Composable
fun rememberSports(
    login: Login,
    /** The Sports page: the channels are looked for on every day shown, not just today. */
    allDays: Boolean = false,
): SportsRowState {
    val context = LocalContext.current
    val prefs = remember { SportsPrefs(context) }
    var group by remember { mutableStateOf(prefs.group()) }
    var matches by remember { mutableStateOf(SportsCache.matches) }
    var guide by remember { mutableStateOf(SportsCache.guide) }

    LaunchedEffect(Unit) {
        while (true) {
            val fresh = System.currentTimeMillis() - SportsCache.matchesAt < 55_000
            if (!fresh) {
                try {
                    val today = Espn.premierLeague()
                    SportsCache.matches = today
                    SportsCache.matchesAt = System.currentTimeMillis()
                    matches = today
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // ESPN didn't answer: keep what we had and try again later.
                }
            }
            delay(if (matches.orEmpty().any { it.live }) 60_000L else 5 * 60_000L)
        }
    }

    // The sports channels' guides, read once there's a match to find and again every half hour.
    val needGuide = matches.orEmpty().any { !it.finished && (allDays || it.day == java.time.LocalDate.now()) }
    LaunchedEffect(group?.id, needGuide) {
        val chosen = group ?: return@LaunchedEffect
        if (!needGuide) return@LaunchedEffect
        while (true) {
            val kept = SportsCache.guide
            if (kept != null && kept.groupId == chosen.id && System.currentTimeMillis() - kept.loadedAt < SportsCache.GUIDE_KEEP_MS) {
                guide = kept
            } else {
                try {
                    guide = SportsCache.loadGuide(login, chosen)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The provider didn't answer: the cards say the channel wasn't found.
                    guide = SportsGuide(chosen.id, System.currentTimeMillis(), emptyList(), emptyMap())
                }
            }
            delay(SportsCache.GUIDE_KEEP_MS)
        }
    }

    val current = guide?.takeIf { it.groupId == group?.id }
    // ESPN's list from an earlier day (the player left running overnight) drops yesterday's games.
    val today = java.time.LocalDate.now()
    val all = matches?.filter { !it.day.isBefore(today) }
    val shown = all?.filter { !it.finished && it.day == today }?.sortedWith(compareByDescending<Match> { it.live }.thenBy { it.kickOff })
    val channels = remember(all, current) {
        if (current == null) emptyMap() else all.orEmpty().filter { !it.finished }.associate { it.id to SportsMatcher.channelFor(it, current) }
    }
    return SportsRowState(
        group = group,
        matches = shown,
        all = all,
        guideReady = current != null,
        channelFor = channels,
        setGroup = {
            prefs.setGroup(it)
            group = it
        },
    )
}

private val CardWidth = 272.dp
private val CardHeight = 134.dp
private val CardShape = RoundedCornerShape(14.dp)

/**
 * The Sports row: "Premier League", then a card per match today, live ones first. A card shows
 * LIVE and the minute (or TODAY and the kick-off time), both teams with their badges and the
 * score, and the channel it's on. OK watches that channel full screen; with no channel found it
 * opens your sports group in Live TV. The last card shows and changes the sports group.
 */
@Composable
fun SportsShelf(
    login: Login,
    state: SportsRowState,
    edge: androidx.compose.ui.unit.Dp,
    onWatchChannel: (Channel) -> Unit,
    onOpenGroup: (String) -> Unit,
    /** Opens the Sports page (the "All fixtures" card). */
    onSeeAll: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val rowStart = rememberRowStart()
    val matches = state.matches.orEmpty()
    val group = state.group
    SectionRow(
        title = "Premier League",
        edge = edge,
        contentStart = edge,
        spacing = 14.dp,
        rowModifier = rowStart.rowModifier(matches.firstOrNull()?.id ?: "group"),
    ) {
        items(matches, key = { it.id }) { match ->
            val channel = state.channelFor[match.id]
            MatchCard(
                match = match,
                channel = channel,
                searching = group != null && !state.guideReady,
                onClick = {
                    when {
                        channel != null -> onWatchChannel(channel)
                        group != null -> onOpenGroup(group.id)
                        else -> choosing = true
                    }
                },
                modifier = rowStart.card(match.id),
            )
        }
        item(key = "all") {
            SeeAllCard(onClick = onSeeAll, modifier = rowStart.card("all"))
        }
        item(key = "group") {
            GroupCard(group = group, onClick = { choosing = true }, modifier = rowStart.card("group"))
        }
    }
    if (choosing) {
        SportsGroupPicker(
            login = login,
            current = group?.id,
            onPick = {
                state.setGroup(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
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
 * both teams with their badges, records and score; and the channel it's on.
 */
@Composable
internal fun MatchCard(
    match: Match,
    channel: Channel?,
    searching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "TODAY",
) {
    SportsCardFrame(onClick = onClick, modifier = modifier.width(CardWidth)) {
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
                        match.finished && channel == null -> "Full time"
                        channel != null -> ChannelLabels.of(channel.name).name
                        searching -> "Finding the channel…"
                        // The guide only reaches a day or two ahead.
                        match.day != java.time.LocalDate.now() -> "Channel nearer the day"
                        else -> "Channel not found"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = WiptvType.hint.copy(color = if (channel != null) WiptvColors.secondary else WiptvColors.quiet),
                )
                if (channel != null && match.live) {
                    Text(text = "▶", style = WiptvType.hint.copy(color = WiptvColors.text))
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

/** The last card: which group your sports channels are in, and OK to choose (or change) it. */
@Composable
internal fun GroupCard(group: Group?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SportsCardFrame(onClick = onClick, modifier = modifier.width(if (group == null) CardWidth else 200.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (group == null) {
                Text(text = "Choose your sports channels", style = WiptvType.body.copy(fontWeight = FontWeight.SemiBold, color = WiptvColors.text))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Pick the Live TV group with Sky Sports, TNT Sports and so on, so each match shows its channel.",
                    style = WiptvType.hint,
                )
            } else {
                Text(text = "Sports channels", style = WiptvType.hint)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = group.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.body.copy(fontWeight = FontWeight.SemiBold, color = WiptvColors.text),
                )
                Spacer(Modifier.height(6.dp))
                Text(text = "OK to change", style = WiptvType.hint)
            }
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

/** Choosing the Live TV group with your sports channels: a list of your groups over Home. */
@Composable
internal fun SportsGroupPicker(login: Login, current: String?, onPick: (Group) -> Unit, onDismiss: () -> Unit) {
    val cache = remember(login) { SessionCache.of(login, "LIVE") }
    var groups by remember { mutableStateOf(cache.liveGroups) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(login) {
        if (groups != null) return@LaunchedEffect
        try {
            groups = Xtream.liveGroups(login).also { cache.liveGroups = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    // Focus on the group in use (or the first), once the list is there.
    val target = remember { FocusRequester() }
    LaunchedEffect(groups) {
        if (groups.isNullOrEmpty()) return@LaunchedEffect
        repeat(20) {
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(440.dp)
                    .heightIn(max = 440.dp)
                    .glass(WiptvShapes.panel, Glass.Drawer)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = "Your sports channels", style = WiptvType.pageTitle.copy(fontSize = 22.sp, lineHeight = 28.sp))
                Text(text = "Pick the group with your sports channels. Back to close.", style = WiptvType.hint)
                Spacer(Modifier.height(6.dp))
                val list = groups
                when {
                    list != null -> {
                        val focusOn = list.firstOrNull { it.id == current }?.id ?: list.firstOrNull()?.id
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(list, key = { it.id }) { g ->
                                MenuItem(
                                    onClick = { onPick(g) },
                                    selected = g.id == current,
                                    modifier = if (g.id == focusOn) Modifier.focusRequester(target) else Modifier,
                                ) {
                                    Text(text = g.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = WiptvType.body.copy(color = androidx.tv.material3.LocalContentColor.current))
                                }
                            }
                        }
                    }
                    failed -> Text(text = "Couldn't load your Live TV groups. Back, then try again.", style = WiptvType.metadata.copy(color = WiptvColors.warning))
                    else -> Text(text = "Loading your Live TV groups…", style = WiptvType.metadata)
                }
            }
        }
    }
}

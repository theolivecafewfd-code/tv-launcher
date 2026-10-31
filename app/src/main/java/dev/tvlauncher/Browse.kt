package dev.tvlauncher

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.LocalContentColor
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.PlaybackProgress
import dev.wiptv.design.PosterHeight
import dev.wiptv.design.PosterWidth
import dev.wiptv.design.PosterWide
import dev.wiptv.design.CARD_GROW_MS
import dev.wiptv.design.ContinueCard
import dev.wiptv.design.ContinueGap
import dev.wiptv.design.LocalSectionActive
import dev.wiptv.design.PosterCard
import dev.wiptv.design.PosterPlaceholder
import dev.wiptv.design.SectionDim
import dev.wiptv.design.SectionHeading
import dev.wiptv.design.SectionRow
import dev.wiptv.design.rememberWideCard
import dev.wiptv.design.sectionCaption
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.MetaLine
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import dev.wiptv.design.RowScrolling
import dev.wiptv.design.DiceIcon
import dev.wiptv.design.SearchIcon
import dev.wiptv.design.NavItem
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.focusOutline
import dev.wiptv.design.glass
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import java.util.Locale

/** Colours for the little "where to watch" tags. */
private fun Source.colours(): Pair<Color, Color> = when (this) {
    Source.NETFLIX -> Color(0xFFE5484D) to Color.White
    Source.PRIME -> Color(0xFF3EC1D3) to Color(0xFF0B1A1F)
}

/**
 * The launcher's items for the shared header's navigation capsule: TV Shows · Movies · Live TV ·
 * Apps, then search and Surprise me as icons. TV Shows and Movies are the destinations (the
 * current one keeps its underline); Live TV opens the player's Live TV, Apps opens the Apps tray,
 * and the icons open search and a surprise pick. Focus alone never does anything; OK does.
 */
fun launcherNav(
    selected: MediaType,
    onSelect: (MediaType) -> Unit,
    onLiveTv: () -> Unit,
    onApps: () -> Unit,
    onSearch: () -> Unit,
    onSurprise: () -> Unit,
    appsRequester: FocusRequester,
    searchRequester: FocusRequester,
): List<NavItem> = MediaType.entries.map { type ->
    NavItem(label = type.label, selected = type == selected, onClick = { if (type != selected) onSelect(type) })
} + listOf(
    NavItem(label = "Live TV", onClick = onLiveTv),
    NavItem(label = "Apps", modifier = Modifier.focusRequester(appsRequester), onClick = onApps),
    NavItem(
        icon = { colour -> SearchIcon(color = colour, size = 20.dp) },
        description = "Search",
        modifier = Modifier.focusRequester(searchRequester),
        onClick = onSearch,
    ),
    NavItem(icon = { colour -> DiceIcon(color = colour, size = 20.dp) }, description = "Surprise me", onClick = onSurprise),
)


internal fun metaItems(title: Title): List<String> = buildList {
    add(if (title.type == MediaType.TV) "Series" else "Film")
    if (title.year.isNotBlank()) add(title.year)
    title.genreIds.mapNotNull { Catalog.genreName(it) }.take(2).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    if (title.rating > 0) add("TMDB " + String.format(Locale.UK, "%.1f", title.rating))
}

/** The IPTV tag: dark with white text and a faint white edge, so it reads on any poster. */
@Composable
internal fun IptvTag() {
    Text(
        text = "IPTV",
        modifier = Modifier
            .background(WiptvColors.background.copy(alpha = 0.9f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White),
    )
}

@Composable
private fun SourceTag(source: Source) {
    val (bg, fg) = source.colours()
    Text(
        text = source.label,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg),
    )
}

/**
 * One row of posters (the shared [SectionRow] and [PosterCard], as in the player). The poster you
 * rest on widens to its landscape picture, as in Continue watching.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(
    row: ContentRow,
    onOpen: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    onFocusTitle: (Title) -> Unit,
    requester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
) {
    val wideCard = rememberWideCard()
    // A row still loading has no titles yet: it shows placeholders that can take focus. If
    // focus is on one when the titles arrive, it moves to the first title.
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(row.items.isEmpty()) {
        if (row.items.isEmpty() || !waiting) return@LaunchedEffect
        waiting = false
        repeat(20) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(25)
        }
    }
    TrackAttached(requester)
    // Straight to the row above or below, once that row is on screen.
    val upAndDown = Modifier.focusProperties {
        up?.ifAttached()?.let { this.up = it }
        down.ifAttached()?.let { this.down = it }
    }
    // Just the row's name: the notes beside it ("Most popular on…", "Because you watch…") were
    // dropped at Wayne's request to keep the rows clean. The row remembers which poster you were
    // on, and up and down always go to the row directly above or below.
    SectionRow(
        title = row.title,
        edge = Edge,
        rowModifier = Modifier.focusRequester(requester).focusRestorer(),
    ) {
        if (row.items.isEmpty()) {
            items(6) {
                PosterPlaceholder(onFocusChange = { if (it) waiting = true }, modifier = upAndDown)
            }
        }
        itemsIndexed(row.items, key = { _, t -> t.key }) { _, title ->
            TitlePoster(
                title = title,
                onClick = { onOpen(title) },
                onLongClick = { onPlay(title) },
                modifier = upAndDown,
                wide = wideCard.wide == title.key,
                onFocus = {
                    if (it) onFocusTitle(title)
                    wideCard.onFocus(title.key, it)
                },
            )
        }
    }
}

/**
 * A title's poster (the shared [PosterCard]) with where it's on in the top left: the IPTV tag when
 * your provider has it, then your first service. [compact] is the smaller poster without a
 * caption, for search results and "More like this".
 */
@Composable
internal fun TitlePoster(
    title: Title,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
    wide: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
) {
    PosterCard(
        name = title.name,
        posterUrl = title.posterUrl,
        backdropUrl = title.backdropUrl,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        compact = compact,
        wide = wide,
        onFocus = onFocus,
    ) {
        val onIptv = if (title.iptv != null) true else IptvBadges.has(title)
        val source = title.sources.firstOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onIptv) IptvTag()
            source?.let { SourceTag(it) }
        }
    }
}

/** A quiet line of text in place of a row, e.g. while loading. */
@Composable
fun RowMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = Edge, vertical = 12.dp),
        style = WiptvType.metadata.copy(color = WiptvColors.quiet),
    )
}

/** A film or series from your IPTV provider, in the same frame as the other posters, tagged "IPTV". */
@Composable
internal fun IptvPosterCard(title: IptvTitle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PosterCard(
        name = listOfNotNull(title.name, title.year?.takeUnless { title.name.contains(it) }).joinToString(" · "),
        posterUrl = title.posterUrl,
        onClick = onClick,
        modifier = modifier,
    ) { IptvTag() }
}

/**
 * Your channels: the live channels you've starred in WIPTV Player, in its order, as poster-shaped
 * cards in the shared row. The one you rest on widens, like the other rows, to show what's on:
 * the logo, now (title, times, progress) and next, from [programmes] (null until loaded). OK plays
 * it full screen in the player; Back there goes to the channel's group in Live TV.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChannelRow(
    channels: List<IptvChannel>,
    programmes: (IptvChannel) -> List<IptvProgramme>?,
    onWatch: (IptvChannel) -> Unit,
    onFocusChannel: (IptvChannel) -> Unit,
    requester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
) {
    TrackAttached(requester)
    val upAndDown = Modifier.focusProperties {
        up?.ifAttached()?.let { this.up = it }
        down?.ifAttached()?.let { this.down = it }
    }
    val wideCard = rememberWideCard()
    SectionRow(
        title = "Your channels",
        edge = Edge,
        rowModifier = Modifier.focusRequester(requester).focusRestorer(),
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelPoster(
                channel = channel,
                programmes = programmes(channel),
                wide = wideCard.wide == "ch:${channel.id}",
                onFocus = { has ->
                    if (has) onFocusChannel(channel)
                    wideCard.onFocus("ch:${channel.id}", has)
                },
                onClick = { onWatch(channel) },
                modifier = upAndDown,
            )
        }
    }
}

/**
 * A starred channel as a poster: the logo on a soft panel with the name underneath; widened, the
 * logo moves left and what's on now and next fills the rest.
 */
@Composable
private fun ChannelPoster(
    channel: IptvChannel,
    programmes: List<IptvProgramme>?,
    wide: Boolean,
    onFocus: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (wide) PosterWide else PosterWidth, animationSpec = tween(CARD_GROW_MS), label = "channelWidth")
    Column(modifier = Modifier.width(width + ArtFocusGap * 2)) {
        ArtFrame(
            width = width,
            height = PosterHeight,
            onFocusChange = {
                focused = it
                onFocus(it)
            },
            onClick = onClick,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // The logo: the whole card while narrow, a column on the left once wide.
                Box(modifier = Modifier.width(PosterWidth).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (channel.logoUrl != null) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = channel.name,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = WiptvType.metadata.copy(color = WiptvColors.secondary),
                        )
                    }
                }
                if (wide) ChannelNowNext(programmes, Modifier.weight(1f).padding(end = 14.dp))
            }
            SectionDim()
        }
        Text(
            text = channel.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = ArtFocusGap, end = ArtFocusGap, top = 6.dp).height(36.dp),
            style = WiptvType.caption.copy(
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                color = sectionCaption(),
            ),
        )
    }
}

/** Inside a widened channel card: now (title, times, progress) and next. */
@Composable
private fun ChannelNowNext(programmes: List<IptvProgramme>?, modifier: Modifier) {
    val now = System.currentTimeMillis()
    val current = programmes?.firstOrNull { it.start <= now && now < it.end }
    val next = programmes?.firstOrNull { it.start >= (current?.end ?: now) }
    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK) }
    fun hhmm(t: Long) = clock.format(java.util.Date(t))
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            programmes == null -> Text("Loading what's on…", style = WiptvType.hint)
            current == null -> Text("No programme information", style = WiptvType.hint)
            else -> {
                Text("Now", style = WiptvType.hint)
                Text(
                    text = current.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.body.copy(fontWeight = FontWeight.Medium, color = WiptvColors.text),
                )
                Text("${hhmm(current.start)}–${hhmm(current.end)}", style = WiptvType.hint.copy(color = WiptvColors.secondary))
                PlaybackProgress(
                    fraction = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (next != null) {
            Text(
                text = "Next · ${hhmm(next.start)} · ${next.title}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
                style = WiptvType.hint.copy(color = WiptvColors.secondary),
            )
        }
    }
}

/**
 * Continue watching: films and episodes you've started in WIPTV Player, as the shared
 * [ContinueCard] (the player's Continue watching uses the same card). Cards are portrait posters;
 * the one you rest on widens to its landscape picture, with the title, episode, time left and
 * "OK to resume" underneath. OK resumes in the player.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContinueRow(
    items: List<IptvResume>,
    onResume: (IptvResume) -> Unit,
    onFocusItem: (IptvResume) -> Unit,
    requester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
) {
    TrackAttached(requester)
    val upAndDown = Modifier.focusProperties {
        up?.ifAttached()?.let { this.up = it }
        down.ifAttached()?.let { this.down = it }
    }
    val wideCard = rememberWideCard()
    SectionRow(
        title = "Continue watching",
        edge = Edge,
        // focusRestorer: coming back to the row lands on the card you were last on.
        rowModifier = Modifier.focusRequester(requester).focusRestorer(),
        contentStart = Edge,
        spacing = ContinueGap,
    ) {
        items(items, key = { it.key }) { item ->
            ContinueCard(
                title = item.title,
                episode = item.subtitle?.substringBefore(" · "),
                left = item.left,
                posterUrl = item.posterUrl,
                imageUrl = item.imageUrl,
                progress = item.progress,
                wide = wideCard.wide == item.key,
                focused = wideCard.focused == item.key,
                onFocusChange = {
                    if (it) onFocusItem(item)
                    wideCard.onFocus(item.key, it)
                },
                onClick = { onResume(item) },
                modifier = upAndDown,
                tag = { IptvTag() },
            )
        }
    }
}

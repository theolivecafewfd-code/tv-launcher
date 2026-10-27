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
import dev.wiptv.design.NavCapsule
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
 * The top navigation under the greeting, in one glass capsule: TV Shows · Movies · Live TV · Apps,
 * then search and Surprise me as icons. TV Shows and Movies are the destinations (the current one
 * keeps its underline); Live TV opens the player's Live TV, Apps opens the Apps tray, and the
 * icons open search and a surprise pick. Focus alone never does anything; OK does.
 */
@Composable
fun TypeSwitch(
    selected: MediaType,
    onSelect: (MediaType) -> Unit,
    onLiveTv: () -> Unit,
    onApps: () -> Unit,
    onSearch: () -> Unit,
    onSurprise: () -> Unit,
    appsRequester: FocusRequester,
    searchRequester: FocusRequester,
) {
    NavCapsule(
        modifier = Modifier.padding(start = Edge),
        items = MediaType.entries.map { type ->
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
        ),
    )
}

/** A line-drawn magnifying glass. */
@Composable
fun SearchIcon(color: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.13f
        drawCircle(
            color = color,
            radius = w * 0.3f,
            center = androidx.compose.ui.geometry.Offset(w * 0.42f, w * 0.42f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.65f, w * 0.65f),
            end = androidx.compose.ui.geometry.Offset(w * 0.92f, w * 0.92f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/**
 * The featured title, over the big artwork behind the top of the screen (drawn by the home
 * screen): which of the Top 10 it is and where it's on, the name, a details line and a short
 * synopsis. Left and right move through [titles]; OK opens the current one, holding OK plays it.
 *
 * Focus is the list highlight the player uses: a short bright bar at the left edge, with the
 * counter lit. The text block has a fixed height and fades between titles, so nothing below moves.
 */
@Composable
fun Featured(
    titles: List<Title>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onOpen: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = titles.getOrNull(index) ?: return
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val marker by animateFloatAsState(if (focused) 1f else 0f, animationSpec = tween(120), label = "featuredMarker")
    Surface(
        onClick = { onOpen(title) },
        onLongClick = { onPlay(title) },
        modifier = modifier
            .padding(start = Edge - 12.dp)
            .width(500.dp)
            .height(190.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.DirectionLeft && index > 0 -> { onIndexChange(index - 1); true }
                    event.key == Key.DirectionRight && index < titles.size - 1 -> { onIndexChange(index + 1); true }
                    else -> false
                }
            },
        tonalElevation = 0.dp,
        shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.small),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiptvColors.text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = WiptvColors.text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = WiptvColors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        interactionSource = interaction,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    if (marker > 0f) {
                        val h = 24.dp.toPx()
                        drawRoundRect(
                            color = WiptvColors.rowFocusMarker.copy(alpha = marker),
                            topLeft = Offset(3.dp.toPx(), 52.dp.toPx() - h / 2),
                            size = Size(3.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                },
        ) {
            Crossfade(targetState = title, animationSpec = tween(160), label = "featuredText") { shown ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // "‹ 3 of 10 ›": lit when focused, so it's clear left and right move through them.
                        Text(
                            text = "‹  ${index + 1} of ${titles.size}  ›",
                            modifier = Modifier
                                .then(
                                    if (focused) Modifier.background(WiptvColors.focus, WiptvShapes.pill)
                                    else Modifier.glass(WiptvShapes.pill),
                                )
                                .padding(horizontal = 9.dp, vertical = 2.dp),
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (focused) WiptvColors.onLight else WiptvColors.text,
                            ),
                        )
                        if (IptvBadges.has(shown)) IptvTag()
                        shown.sources.forEach { SourceTag(it) }
                    }
                    Text(
                        text = DisplayTitles.withoutYear(shown.name, shown.year),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                        style = WiptvType.hero,
                    )
                    // Up to two lines of details, wrapping between items, then at most three of synopsis.
                    // The block's fixed height leaves room for both, clear of Your apps below.
                    MetaLine(items = metaItems(shown), style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium))
                    if (shown.overview.isNotBlank()) {
                        Text(
                            text = shown.overview,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                            style = WiptvType.metadata.copy(color = WiptvColors.text.copy(alpha = 0.86f)),
                        )
                    }
                }
            }
        }
    }
}

private fun metaItems(title: Title): List<String> = buildList {
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

/** Poster sizes, the same as the player's: about six across, the next peeking in. */
internal val PosterWidth = 126.dp
internal val PosterHeight = 189.dp

/** A row poster once it has widened: 16:9 at the poster's height. */
private val PosterWide = 336.dp

/** How long focus has to rest on a card before it widens, and how long widening takes. */
private const val SETTLE_MS = 180L
private const val GROW_MS = 180

/**
 * Which card in a row is wide: the one with focus, once focus has rested on it for [SETTLE_MS].
 * Only one card is ever wide; a new focus cancels a pending widen, so Left and Right stay quick,
 * and leaving the row returns every card to portrait.
 */
private class WideCard {
    var focused by mutableStateOf<String?>(null)
    var wide by mutableStateOf<String?>(null)

    fun onFocus(key: String, has: Boolean) {
        if (has) focused = key else if (focused == key) focused = null
    }
}

@Composable
private fun rememberWideCard(): WideCard {
    val card = remember { WideCard() }
    LaunchedEffect(card.focused) {
        val key = card.focused
        if (key == null) {
            card.wide = null
            return@LaunchedEffect
        }
        delay(SETTLE_MS)
        card.wide = key
    }
    return card
}

/**
 * Whether the row (section) around a card has the remote's focus. The focused row is at full
 * strength; the others are softer (greyer heading and captions, artwork gently dimmed), so it's
 * clear which row you're in. Screens without rows (search, details) leave it on.
 */
val LocalSectionActive = compositionLocalOf { true }

/** Heading and caption colour for a section that doesn't have focus. */
private val InactiveText = WiptvColors.secondary

/** How dark an unfocused section's artwork goes (a plain black wash, no blur). */
private const val INACTIVE_DIM = 0.14f
private const val EMPHASIS_MS = 150

/** A row's heading in the section's emphasis. */
@Composable
private fun SectionHeading(text: String, active: Boolean) {
    val colour by animateColorAsState(if (active) WiptvColors.text else InactiveText, tween(EMPHASIS_MS), label = "heading")
    Text(text = text, style = RowHeading.copy(color = colour))
}

/** The dark wash over a card's artwork in an unfocused section. */
@Composable
private fun BoxScope.SectionDim() {
    val dim by animateFloatAsState(if (LocalSectionActive.current) 0f else INACTIVE_DIM, tween(EMPHASIS_MS), label = "dim")
    if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
}

/** Caption colour: bright in the focused section, softer elsewhere. */
@Composable
private fun sectionCaption(): Color {
    val colour by animateColorAsState(if (LocalSectionActive.current) WiptvColors.text else InactiveText, tween(EMPHASIS_MS), label = "caption")
    return colour
}

/**
 * One row of posters, laid out like the player's rows. The poster you rest on widens to its
 * landscape picture, as in Continue watching.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(
    row: ContentRow,
    onOpen: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    requester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester?,
) {
    val wideCard = rememberWideCard()
    // The whole row is one section: moving between its posters keeps it bright.
    var active by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().onFocusChanged { active = it.hasFocus }) {
      CompositionLocalProvider(LocalSectionActive provides active) {
        Row(
            modifier = Modifier.padding(start = Edge, end = Edge, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Just the row's name: the notes beside it ("Most popular on…", "Because you watch…")
            // were dropped at Wayne's request to keep the rows clean.
            SectionHeading(row.title, active)
        }
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
            up.ifAttached()?.let { this.up = it }
            down.ifAttached()?.let { this.down = it }
        }
        // The row remembers which poster you were on, and up and down always go to the row
        // directly above or below, whatever the posters' positions.
        RowScrolling(start = Edge, end = 72.dp) {
        LazyRow(
            modifier = Modifier
                .focusRequester(requester)
                .focusRestorer(),
            contentPadding = PaddingValues(start = Edge - ArtFocusGap, end = Edge, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp - ArtFocusGap * 2),
        ) {
            if (row.items.isEmpty()) {
                items(6) {
                    Column(modifier = Modifier.width(PosterWidth + ArtFocusGap * 2)) {
                        ArtFrame(
                            width = PosterWidth,
                            height = PosterHeight,
                            onFocusChange = { if (it) waiting = true },
                            onClick = {},
                            modifier = upAndDown,
                        ) {}
                        Spacer(Modifier.height(42.dp))
                    }
                }
            }
            itemsIndexed(row.items, key = { _, t -> t.key }) { _, title ->
                PosterCard(
                    title = title,
                    onClick = { onOpen(title) },
                    onLongClick = { onPlay(title) },
                    modifier = upAndDown,
                    wide = wideCard.wide == title.key,
                    onFocus = { wideCard.onFocus(title.key, it) },
                )
            }
        }
        }
      }
    }
}

/**
 * A poster in the player's frame (the outline sits just outside the picture, with a slight
 * lift), with its title underneath, brighter when focused. [compact] is the smaller poster
 * without a caption, for search results and "More like this".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PosterCard(
    title: Title,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
    /** In a home row: widened to the landscape picture (see [rememberWideCard]). */
    wide: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        when {
            compact -> 84.dp
            wide -> PosterWide
            else -> PosterWidth
        },
        animationSpec = tween(GROW_MS),
        label = "posterWidth",
    )
    val height = if (compact) 126.dp else PosterHeight
    // On focus, the whole card, caption included, comes on screen (not just the picture), and
    // again once it has widened (the row slides along if needed).
    val wholeCard = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(wide) {
        if (wide) {
            delay(GROW_MS + 20L)
            wholeCard.bringIntoView()
        }
    }
    Column(modifier = Modifier.width(width + ArtFocusGap * 2).bringIntoViewRequester(wholeCard)) {
        ArtFrame(
            width = width,
            height = height,
            onFocusChange = {
                focused = it
                onFocus(it)
                if (it && !compact) scope.launch { wholeCard.bringIntoView() }
            },
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        ) {
            // The name, for posters that are missing or still loading.
            Text(
                text = title.name,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                style = WiptvType.metadata.copy(color = WiptvColors.secondary),
            )
            PosterArtwork(title = title, wide = wide)
            // Where it's on: IPTV first when your provider has it, then your first service.
            val onIptv = if (title.iptv != null) true else IptvBadges.has(title)
            val source = title.sources.firstOrNull()
            if (onIptv || source != null) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onIptv) IptvTag()
                    source?.let { SourceTag(it) }
                }
            }
            SectionDim()
        }
        if (!compact) {
            Text(
                text = title.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = ArtFocusGap, end = ArtFocusGap, top = 6.dp).height(36.dp),
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                    color = sectionCaption(),
                ),
            )
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
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(PosterWidth + ArtFocusGap * 2)) {
        ArtFrame(
            width = PosterWidth,
            height = PosterHeight,
            onFocusChange = { focused = it },
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(
                text = title.name,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                style = WiptvType.metadata.copy(color = WiptvColors.secondary),
            )
            if (title.posterUrl != null) {
                AsyncImage(
                    model = title.posterUrl,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.align(Alignment.TopStart).padding(5.dp)) { IptvTag() }
        }
        Text(
            text = listOfNotNull(title.name, title.year?.takeUnless { title.name.contains(it) }).joinToString(" · "),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = ArtFocusGap, end = ArtFocusGap, top = 6.dp).height(36.dp),
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                color = if (focused) WiptvColors.text else WiptvColors.secondary,
            ),
        )
    }
}

/** Channel tiles: 16:9, smaller than the posters, the logo on a faint panel. */
internal val ChannelTileWidth = 192.dp
internal val ChannelTileHeight = 108.dp

/**
 * Your channels: the live channels you've starred in WIPTV Player, in its order. OK plays one full
 * screen in the player; Back there goes to the channel's group in Live TV.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChannelRow(
    channels: List<IptvChannel>,
    onWatch: (IptvChannel) -> Unit,
    requester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester?,
) {
    TrackAttached(requester)
    val upAndDown = Modifier.focusProperties {
        up.ifAttached()?.let { this.up = it }
        down.ifAttached()?.let { this.down = it }
    }
    // The whole row is one section: moving between channels keeps it bright.
    var active by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().onFocusChanged { active = it.hasFocus }) {
      CompositionLocalProvider(LocalSectionActive provides active) {
        Box(Modifier.padding(start = Edge, end = Edge, bottom = 2.dp)) { SectionHeading("Your channels", active) }
        RowScrolling(start = Edge, end = 72.dp) {
            LazyRow(
                modifier = Modifier.focusRequester(requester).focusRestorer(),
                contentPadding = PaddingValues(start = Edge - ArtFocusGap, end = Edge, top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp - ArtFocusGap * 2),
            ) {
                items(channels, key = { it.id }) { channel ->
                    ChannelTile(channel = channel, onClick = { onWatch(channel) }, modifier = upAndDown)
                }
            }
        }
      }
    }
}

@Composable
private fun ChannelTile(channel: IptvChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(ChannelTileWidth + ArtFocusGap * 2)) {
        ArtFrame(
            width = ChannelTileWidth,
            height = ChannelTileHeight,
            onFocusChange = { focused = it },
            onClick = onClick,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
                )
            } else {
                Text(
                    text = channel.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                    style = WiptvType.metadata.copy(color = WiptvColors.secondary),
                )
            }
            SectionDim()
        }
        Text(
            text = channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = ArtFocusGap, end = ArtFocusGap, top = 6.dp),
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                color = sectionCaption(),
            ),
        )
    }
}

/**
 * Continue watching card sizes: portrait (2:3) at rest; the card you settle on widens to 16:9
 * at the same height, pushing its neighbours along. Captions sit in a fixed space underneath,
 * so the row's height never changes.
 */
private val CardHeight = 216.dp
private val CardNarrow = 144.dp
private val CardWide = 384.dp
private val CardGap = 14.dp
private val CaptionHeight = 58.dp

/**
 * Continue watching: films and episodes you've started in WIPTV Player. Cards are portrait
 * posters; the one you rest on widens to its landscape picture (Netflix style), with the title,
 * episode, time left and "OK to resume" underneath. OK resumes in the player.
 *
 * Focus shows at once as a thin outline; widening waits until focus has settled (see
 * [rememberWideCard]).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContinueRow(
    items: List<IptvResume>,
    onResume: (IptvResume) -> Unit,
    requester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester?,
) {
    TrackAttached(requester)
    val upAndDown = Modifier.focusProperties {
        up.ifAttached()?.let { this.up = it }
        down.ifAttached()?.let { this.down = it }
    }
    val wideCard = rememberWideCard()
    // The whole row is one section: moving between cards keeps it bright.
    var active by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().onFocusChanged { active = it.hasFocus }) {
      CompositionLocalProvider(LocalSectionActive provides active) {
        Box(Modifier.padding(start = Edge, end = Edge, bottom = 2.dp)) { SectionHeading("Continue watching", active) }
        RowScrolling(start = Edge, end = 72.dp) {
            LazyRow(
                // focusRestorer: coming back to the row lands on the card you were last on.
                modifier = Modifier.focusRequester(requester).focusRestorer(),
                contentPadding = PaddingValues(start = Edge, end = Edge, top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                items(items, key = { it.key }) { item ->
                    ContinueCard(
                        item = item,
                        wide = wideCard.wide == item.key,
                        focused = wideCard.focused == item.key,
                        onFocusChange = { wideCard.onFocus(item.key, it) },
                        onClick = { onResume(item) },
                        modifier = upAndDown,
                    )
                }
            }
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    item: IptvResume,
    wide: Boolean,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (wide) CardWide else CardNarrow, animationSpec = tween(GROW_MS), label = "cardWidth")
    // Once it has widened, keep the whole card on screen (the row slides along if needed).
    val wholeCard = remember { BringIntoViewRequester() }
    LaunchedEffect(wide) {
        if (wide) {
            delay(GROW_MS + 20L)
            wholeCard.bringIntoView()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.width(width).bringIntoViewRequester(wholeCard)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(CardHeight)
                .onFocusChanged { onFocusChange(it.isFocused) },
            tonalElevation = 0.dp,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = WiptvColors.surface,
                focusedContainerColor = WiptvColors.surface,
                pressedContainerColor = WiptvColors.surface,
            ),
            // One thin, soft outline for focus; no lift, glow or second border.
            scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, WiptvColors.focus.copy(alpha = 0.85f)), shape = shape),
                pressedBorder = Border(border = BorderStroke(2.dp, WiptvColors.focus.copy(alpha = 0.85f)), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
                ContinueArtwork(item = item, wide = wide)
                SectionDim()
                // Where it's from, top left, clear of the progress bar.
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { IptvTag() }
                if (item.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.24f), RoundedCornerShape(50)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                                .background(WiptvColors.progress, RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
        // A fixed space for the captions: the title (up to two lines), and on the focused card
        // the episode, time left and "OK to resume", quieter.
        Column(modifier = Modifier.fillMaxWidth().height(CaptionHeight).padding(top = 6.dp)) {
            Text(
                text = item.title,
                maxLines = if (focused) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = sectionCaption(),
                ),
            )
            if (focused) {
                Text(
                    text = listOfNotNull(
                        item.subtitle?.substringBefore(" · ")?.takeIf { it.isNotBlank() },
                        item.left,
                        "OK to resume",
                    ).joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.hint.copy(lineHeight = 15.sp),
                )
            }
        }
    }
}

/**
 * A row poster's picture: the poster while narrow; once wide, a crossfade to the title's
 * backdrop, or the sharp poster on a dark wash made from itself when there's no backdrop.
 */
@Composable
private fun PosterArtwork(title: Title, wide: Boolean) {
    Crossfade(targetState = wide, animationSpec = tween(GROW_MS), label = "posterArt") { isWide ->
        Box(Modifier.fillMaxSize()) {
            when {
                isWide && title.backdropUrl != null -> AsyncImage(
                    model = title.backdropUrl,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                isWide && title.posterUrl != null -> {
                    Box(Modifier.fillMaxSize().background(WiptvColors.surface))
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(title.posterUrl).size(12, 18).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AsyncImage(
                        model = title.posterUrl,
                        contentDescription = title.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxHeight().align(Alignment.Center),
                    )
                }
                title.posterUrl != null -> AsyncImage(
                    model = title.posterUrl,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The picture: the portrait poster while narrow; once wide, a crossfade to the landscape still
 * or backdrop. With no landscape picture, the sharp poster sits on a dark wash made from itself
 * (never stretched). Images come from Coil's cache after the first load.
 */
@Composable
private fun ContinueArtwork(item: IptvResume, wide: Boolean) {
    val landscape = item.imageUrl
    Crossfade(targetState = wide, animationSpec = tween(GROW_MS), label = "continueArt") { isWide ->
        Box(Modifier.fillMaxSize().background(WiptvColors.surface)) {
            when {
                isWide && landscape != null -> AsyncImage(
                    model = landscape,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                isWide && item.posterUrl != null -> {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(item.posterUrl).size(12, 18).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxHeight().align(Alignment.Center),
                    )
                }
                item.posterUrl != null -> AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                landscape != null -> AsyncImage(
                    model = landscape,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Text(
                    text = item.title,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    style = WiptvType.hint,
                )
            }
        }
    }
}

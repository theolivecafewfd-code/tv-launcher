package dev.tvlauncher

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
import dev.wiptv.design.SectionCapsule
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

/** The TV Shows | Movies capsule under the header, with search and Surprise me beside it (glass). */
@Composable
fun TypeSwitch(selected: MediaType, onSelect: (MediaType) -> Unit, onSearch: () -> Unit, onSurprise: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = Edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionCapsule(
            labels = MediaType.entries.map { it.label },
            selected = MediaType.entries.indexOf(selected),
            onSelect = { onSelect(MediaType.entries[it]) },
        )
        // Search: a magnifying glass in a glass circle.
        GlassIconButton(onClick = onSearch) {
            SearchIcon(color = LocalContentColor.current, size = 16.dp)
        }
        // Surprise me: a die and its label.
        GlassIconButton(onClick = onSurprise) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiceIcon(color = LocalContentColor.current, size = 15.dp)
                Text(text = "Surprise me", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

/** A round glass button for an icon (or an icon and a short label). */
@Composable
private fun GlassIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = WiptvShapes.pill
    Surface(
        onClick = onClick,
        modifier = Modifier.height(40.dp).glass(shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiptvColors.text,
            focusedContainerColor = WiptvColors.focus,
            focusedContentColor = WiptvColors.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = focusOutline(shape),
    ) {
        Box(modifier = Modifier.height(40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
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
) {
    val title = titles.getOrNull(index) ?: return
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val marker by animateFloatAsState(if (focused) 1f else 0f, animationSpec = tween(120), label = "featuredMarker")
    Surface(
        onClick = { onOpen(title) },
        onLongClick = { onPlay(title) },
        modifier = Modifier
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

/**
 * One row of posters, e.g. Comedy, laid out like the player's rows. Top 10 rows get a big
 * outlined number beside each poster.
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Edge, end = Edge, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = row.title, style = RowHeading)
            if (row.note.isNotBlank()) {
                Text(
                    text = row.note,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = WiptvType.hint,
                )
            }
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
        val upAndDown = Modifier.focusProperties {
            this.up = up
            if (down != null) this.down = down
        }
        // The row remembers which poster you were on, and up and down always go to the row
        // directly above or below, whatever the posters' positions.
        RowScrolling(start = Edge, end = 72.dp) {
        LazyRow(
            modifier = Modifier
                .focusRequester(requester)
                .focusRestorer(),
            contentPadding = PaddingValues(start = Edge - ArtFocusGap, end = Edge, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy((if (row.ranked) 20.dp else 16.dp) - ArtFocusGap * 2),
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
            itemsIndexed(row.items, key = { _, t -> t.key }) { i, title ->
                Row(verticalAlignment = Alignment.Top) {
                    if (row.ranked) {
                        // The number sits against the poster's lower edge.
                        Box(
                            modifier = Modifier.height(PosterHeight + ArtFocusGap * 2),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Text(
                                text = "${i + 1}",
                                modifier = Modifier.padding(end = 2.dp),
                                style = TextStyle(
                                    fontSize = 92.sp,
                                    lineHeight = 92.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WiptvColors.secondary.copy(alpha = 0.6f),
                                    drawStyle = Stroke(width = 3f),
                                ),
                            )
                        }
                    }
                    PosterCard(
                        title = title,
                        onClick = { onOpen(title) },
                        onLongClick = { onPlay(title) },
                        modifier = upAndDown,
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
) {
    var focused by remember { mutableStateOf(false) }
    val width = if (compact) 84.dp else PosterWidth
    val height = if (compact) 126.dp else PosterHeight
    // On focus, the whole card, caption included, comes on screen (not just the picture).
    val wholeCard = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.width(width + ArtFocusGap * 2).bringIntoViewRequester(wholeCard)) {
        ArtFrame(
            width = width,
            height = height,
            onFocusChange = {
                focused = it
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
            if (title.posterUrl != null) {
                AsyncImage(
                    model = title.posterUrl,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            title.sources.firstOrNull()?.let {
                Box(Modifier.align(Alignment.TopStart).padding(5.dp)) { SourceTag(it) }
            }
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
                    color = if (focused) WiptvColors.text else WiptvColors.secondary,
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

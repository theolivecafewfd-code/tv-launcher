package dev.wiptv.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// Posters and rows of them, shared by the launcher's home and the player's Movies and TV Shows,
// so a change (or a fix) to how they look and behave reaches both apps at once.

/** Poster sizes: about six across the screen, the next peeking in. */
val PosterWidth = 126.dp
val PosterHeight = 189.dp

/** The space between posters in a row. */
val PosterGap = 16.dp

/** The smaller poster without a caption (search results, "More like this"). */
private val CompactWidth = 84.dp
private val CompactHeight = 126.dp

/**
 * Which card in a row has focus, for cards that show more underneath when focused (Continue
 * watching). One per row ([rememberFocusedCard]); cards report focus with [onFocus].
 */
class FocusedCard internal constructor() {
    var focused by mutableStateOf<String?>(null)
        private set

    fun onFocus(key: String, has: Boolean) {
        if (has) focused = key else if (focused == key) focused = null
    }
}

@Composable
fun rememberFocusedCard(): FocusedCard = remember { FocusedCard() }

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
fun SectionHeading(text: String, active: Boolean) {
    val colour by animateColorAsState(if (active) WiptvColors.text else InactiveText, tween(EMPHASIS_MS), label = "heading")
    Text(text = text, style = WiptvType.sectionHeading.copy(color = colour))
}

/** The dark wash over a card's artwork in an unfocused section. */
@Composable
fun BoxScope.SectionDim() {
    val dim by animateFloatAsState(if (LocalSectionActive.current) 0f else INACTIVE_DIM, tween(EMPHASIS_MS), label = "dim")
    if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
}

/** Caption colour: bright in the focused section, softer elsewhere. */
@Composable
fun sectionCaption(): Color {
    val colour by animateColorAsState(if (LocalSectionActive.current) WiptvColors.text else InactiveText, tween(EMPHASIS_MS), label = "caption")
    return colour
}

/**
 * A titled row running edge to edge: its heading, then a sideways list of cards. The whole row is
 * one section: while it has focus its heading and captions are bright and its artwork undimmed.
 * [rowModifier] goes on the list itself (focus targets, restoring the last card). With
 * [pinToStart], the focused card always slides to the start of the row.
 */
@Composable
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    edge: Dp = 32.dp,
    gap: Dp = PosterGap,
    /** Where the first card starts: posters sit their focus gap further left, so the art lines up. */
    contentStart: Dp = edge - ArtFocusGap,
    /** Space between the cards themselves; frames with a focus gap take it off both sides. */
    spacing: Dp = gap - ArtFocusGap * 2,
    pinToStart: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    var active by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().onFocusChanged { active = it.hasFocus }) {
        CompositionLocalProvider(LocalSectionActive provides active) {
            Box(Modifier.padding(start = edge, end = edge, bottom = 2.dp)) { SectionHeading(title, active) }
            RowScrolling(start = if (pinToStart) contentStart else edge, end = 72.dp, pinToStart = pinToStart) {
                LazyRow(
                    modifier = rowModifier,
                    contentPadding = PaddingValues(start = contentStart, end = edge, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    content = content,
                )
            }
        }
    }
}

/** A grey poster shown while a row loads. It can take focus, so the remote moves one row at a time. */
@Composable
fun PosterPlaceholder(onFocusChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = Modifier.width(PosterWidth + ArtFocusGap * 2)) {
        ArtFrame(width = PosterWidth, height = PosterHeight, onFocusChange = onFocusChange, onClick = {}, modifier = modifier) {}
        Spacer(Modifier.height(42.dp))
    }
}

/**
 * A poster in the shared frame (the outline just outside the picture, and the card grows a little
 * when focused) with its name underneath, brighter when focused and softer when its row isn't.
 * [compact] is the smaller poster without a caption. [badges] draw over the top left of the art
 * (where it's on).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    name: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
    badges: @Composable BoxScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val width = if (compact) CompactWidth else PosterWidth
    val height = if (compact) CompactHeight else PosterHeight
    // On focus, the whole card, caption included, comes on screen (not just the picture).
    val wholeCard = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
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
                text = name,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                style = WiptvType.metadata.copy(color = WiptvColors.secondary),
            )
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.align(Alignment.TopStart).padding(5.dp)) { badges() }
            SectionDim()
        }
        if (!compact) {
            Text(
                text = name,
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
}

/**
 * Continue watching card sizes: portrait (2:3). Captions sit in a fixed space underneath, so the
 * row's height never changes. The cards have no focus gap, so the row starts at the edge.
 */
private val ContinueHeight = 216.dp
private val ContinueWidth = 144.dp
private val ContinueCaption = 58.dp

/** How much a Continue watching card grows when focused. */
private const val CONTINUE_FOCUS_SCALE = 1.05f

/** The space between Continue watching cards (see [SectionRow]'s spacing). */
val ContinueGap = 14.dp

/**
 * A Continue watching card, the same in both apps: a portrait poster with the progress bar along
 * the bottom. Underneath: the [title], and on the focused card ([focused], from
 * [rememberFocusedCard]) the [episode], time [left] and "OK to resume". Focus is one thin, soft
 * outline and the card grows a little, with no glow. [tag] goes top left (the launcher's "IPTV").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueCard(
    title: String,
    episode: String?,
    left: String?,
    posterUrl: String?,
    imageUrl: String?,
    progress: Float,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.width(ContinueWidth)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(ContinueHeight)
                .onFocusChanged { onFocusChange(it.isFocused) },
            tonalElevation = 0.dp,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = WiptvColors.surface,
                focusedContainerColor = WiptvColors.surface,
                pressedContainerColor = WiptvColors.surface,
            ),
            // One thin, soft outline for focus and a slight grow; no glow or second border.
            scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = CONTINUE_FOCUS_SCALE, pressedScale = CONTINUE_FOCUS_SCALE),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, WiptvColors.focus.copy(alpha = 0.85f)), shape = shape),
                pressedBorder = Border(border = BorderStroke(2.dp, WiptvColors.focus.copy(alpha = 0.85f)), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
                ContinueArtwork(title = title, posterUrl = posterUrl, imageUrl = imageUrl)
                SectionDim()
                // Where it's from, top left, clear of the progress bar.
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { tag() }
                if (progress > 0f) {
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
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(WiptvColors.progress, RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
        // A fixed space for the captions: the title (up to two lines), and on the focused card
        // the episode, time left and "OK to resume", quieter.
        Column(modifier = Modifier.fillMaxWidth().height(ContinueCaption).padding(top = 6.dp)) {
            Text(
                text = title,
                maxLines = if (focused) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.caption.copy(fontWeight = FontWeight.Medium, color = sectionCaption()),
            )
            if (focused) {
                Text(
                    text = listOfNotNull(episode?.takeIf { it.isNotBlank() }, left, "OK to resume").joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.hint.copy(lineHeight = 15.sp),
                )
            }
        }
    }
}

/**
 * The picture: the portrait poster, or with no poster the landscape still or backdrop, cropped
 * to fill the card.
 */
@Composable
private fun ContinueArtwork(title: String, posterUrl: String?, imageUrl: String?) {
    Box(Modifier.fillMaxSize().background(WiptvColors.surface)) {
        when {
            posterUrl != null -> AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                text = title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                style = WiptvType.hint,
            )
        }
    }
}

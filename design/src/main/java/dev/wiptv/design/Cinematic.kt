package dev.wiptv.design

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The cinematic pieces shared by the player's Movies and Series and the launcher's home screen.

/** The gap between artwork and its focus outline, so the outline never touches the picture. */
val ArtFocusGap = 3.dp

/** A picture ready to draw; [soft] when it's a poster stretched into a blurred-looking wash. */
private class Art(val bitmap: ImageBitmap, val soft: Boolean)

/**
 * The big picture behind Movies, Series and their details: the title's backdrop, faded into the
 * background on the left and at the bottom so text over it stays readable.
 *
 * With no backdrop, the poster is loaded tiny and stretched, which gives a soft, blurred-looking
 * wash in its own colours (no real blur, so it costs the Fire Stick nothing).
 *
 * The old picture stays until the new one has loaded, then fades across, so moving quickly
 * between posters never flashes an empty area. [delayMillis] lets focus settle first.
 */
@Composable
fun CinematicBackdrop(
    backdrop: String?,
    poster: String?,
    modifier: Modifier = Modifier,
    delayMillis: Long = 160,
    leftFade: Float = 0.62f,
    /** How far across the screen stays dark enough for text (fraction of the width). */
    textScrim: Float = leftFade * 0.45f,
    /** How dark the screen still is at [textScrim]; the player keeps the default. */
    textScrimAlpha: Float = 0.8f,
    /**
     * Fade the picture itself out instead of painting the background colour over it, so it
     * blends into whatever is behind (the launcher's gradient). Same fades; the player keeps
     * the default.
     */
    fadeToTransparent: Boolean = false,
) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<Art?>(null) }

    LaunchedEffect(backdrop, poster) {
        delay(delayMillis)
        val art = backdrop?.let { loadArt(context, it, soft = false) }
            ?: poster?.let { loadArt(context, it, soft = true) }
        // A picture that fails to load keeps the last one rather than leaving an empty area.
        if (art != null || (backdrop == null && poster == null)) shown = art
    }

    // Fading to transparent needs its own layer, so the masks only cut into the picture.
    Box(modifier = if (fadeToTransparent) modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else modifier) {
        Crossfade(targetState = shown, animationSpec = tween(durationMillis = 320), label = "backdrop") { art ->
            if (art != null) {
                Image(
                    painter = BitmapPainter(art.bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (art.soft) 0.5f else 1f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Readability: dark on the left where the text sits, and into the rows at the bottom.
        if (fadeToTransparent) {
            // The same fades as masks: where the overlay would be x dark, the picture keeps 1 - x.
            Box(
                modifier = Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            textScrim to Color.Black.copy(alpha = 1f - textScrimAlpha),
                            leftFade to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.2f to Color.Black,
                            0.55f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to WiptvColors.background,
                        textScrim to WiptvColors.background.copy(alpha = textScrimAlpha),
                        leftFade to Color.Transparent,
                    ),
                ),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to WiptvColors.background.copy(alpha = 0.55f),
                        0.2f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to WiptvColors.background,
                    ),
                ),
            )
        }
    }
}

private suspend fun loadArt(context: Context, url: String, soft: Boolean): Art? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(if (soft) 12 else 1280, if (soft) 18 else 720)
        .build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
    return Art(bitmap.asImageBitmap(), soft)
}

/**
 * The frame every card uses: artwork inset [ArtFocusGap] inside a clear surface, so the focus
 * outline sits just outside the picture. Focus: outline and a slight lift; no tint on the art.
 */
@Composable
fun ArtFrame(
    width: Dp,
    height: Dp,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    art: @Composable BoxScope.() -> Unit,
) {
    val outer = RoundedCornerShape(14.dp)
    val inner = RoundedCornerShape(11.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width = width + ArtFocusGap * 2, height = height + ArtFocusGap * 2)
            .onFocusChanged { onFocusChange(it.isFocused) },
        shape = ClickableSurfaceDefaults.shape(shape = outer),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = focusOutline(outer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ArtFocusGap)
                .clip(inner)
                .background(Brush.verticalGradient(listOf(WiptvColors.elevated, WiptvColors.surface))),
            content = art,
        )
    }
}


/**
 * Film and series names for display. Providers often add the year to the name ("Predator (1987)"),
 * which then shows twice next to the year in the details line. The provider's name is kept as it is.
 */
object DisplayTitles {
    // The year in brackets, or after a dash or bar, at the very end. A bare "Blade Runner 2049"
    // is never touched: there the number is part of the name.
    private val trailingYear = Regex("""^(.*?\S)\s*(?:\((\d{4})\)|\[(\d{4})\]|[-–|]\s*(\d{4}))\s*$""")

    /** [name] without a trailing year when it matches [year], the one shown in the details line. */
    fun withoutYear(name: String, year: String?): String {
        if (year.isNullOrBlank()) return name
        val match = trailingYear.matchEntire(name) ?: return name
        val found = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }
        val rest = match.groupValues[1].trimEnd(' ', '-', '–', '|', ':')
        if (found != year || rest.none { it.isLetterOrDigit() }) return name
        return rest
    }
}

/**
 * A details-page action. Only the focused button is light (with the outline 3 dp outside it), so
 * focus is never confused with emphasis. At rest the primary one (Play, Resume) has a brighter
 * wash and bolder label; the rest are quiet glass.
 */
@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val outer = RoundedCornerShape(15.dp)
    val inner = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = outer),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = focusOutline(outer),
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .height(42.dp)
                .background(
                    when {
                        focused -> WiptvColors.focus
                        primary -> WiptvColors.focus.copy(alpha = 0.30f)
                        else -> Color.White.copy(alpha = 0.07f)
                    },
                    inner,
                )
                .border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = if (primary) 0.5f else 0.14f), inner)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (focused) WiptvColors.onLight else WiptvColors.text,
                ),
            )
        }
    }
}

/**
 * More info: the whole synopsis and credits in a dark panel on the right, about 42% of the screen
 * wide and only as tall as its text (long text scrolls within the screen). Up and Down scroll
 * it; Back closes it, and the caller puts focus back on its More info button. [sections] are
 * label and text pairs, such as "Starring" and the cast.
 */
@Composable
fun InfoSheet(
    title: String,
    meta: String,
    body: String?,
    sections: List<Pair<String, String>>,
    onClose: () -> Unit,
    /** The details as separate items, which wrap whole (see [MetaLine]); used instead of [meta]. */
    metaItems: List<String>? = null,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).padding(24.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(400.dp)
                    .glass(WiptvShapes.panel, Glass.Sheet)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val step = when (event.key) {
                            Key.DirectionDown -> 160f
                            Key.DirectionUp -> -160f
                            else -> return@onKeyEvent false
                        }
                        scope.launch { scroll.animateScrollBy(step) }
                        true
                    }
                    .verticalScroll(scroll)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = title, style = WiptvType.screen)
                if (metaItems != null) {
                    if (metaItems.isNotEmpty()) MetaLine(items = metaItems, style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium))
                } else if (meta.isNotBlank()) {
                    Text(text = meta, style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium))
                }
                if (!body.isNullOrBlank()) {
                    Text(
                        text = body,
                        modifier = Modifier.padding(top = 6.dp),
                        style = WiptvType.body.copy(lineHeight = 25.sp),
                    )
                }
                sections.forEachIndexed { i, (label, text) ->
                    Text(text = label, modifier = Modifier.padding(top = if (i == 0) 10.dp else 6.dp), style = WiptvType.hint)
                    Text(text = text, style = WiptvType.metadata.copy(color = WiptvColors.text))
                }
                Text(text = "Back to close", modifier = Modifier.padding(top = 14.dp), style = WiptvType.hint)
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
}

/**
 * A details line ("Series · 2011 · 5 seasons · TMDB 8.1") that wraps between items, never inside
 * one: "TMDB 8.1" always stays together, and a separator only appears between two items on the
 * same line, so no line starts or ends with a stray dot. An item too long for a whole line ends
 * in "…".
 */
@Composable
fun MetaLine(
    items: List<String>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    separator: String = "  ·  ",
    lineGap: Dp = 2.dp,
) {
    Layout(
        modifier = modifier,
        content = {
            items.forEach { Text(text = it, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
            repeat((items.size - 1).coerceAtLeast(0)) { Text(text = separator, style = style, maxLines = 1, softWrap = false) }
        },
    ) { measurables, constraints ->
        val limit = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val texts = measurables.take(items.size).map { it.measure(loose) }
        val separators = measurables.drop(items.size).map { it.measure(loose) }
        val gap = lineGap.roundToPx()
        val placed = mutableListOf<Triple<Placeable, Int, Int>>()
        var x = 0
        var y = 0
        var lineHeight = 0
        var width = 0
        texts.forEachIndexed { i, text ->
            if (i > 0) {
                val sep = separators[i - 1]
                if (x + sep.width + text.width <= limit) {
                    placed += Triple(sep, x, y)
                    x += sep.width
                } else {
                    y += lineHeight + gap
                    x = 0
                    lineHeight = 0
                }
            }
            placed += Triple(text, x, y)
            x += text.width
            lineHeight = maxOf(lineHeight, text.height)
            width = maxOf(width, x)
        }
        val height = if (texts.isEmpty()) 0 else y + lineHeight
        layout(width.coerceIn(constraints.minWidth, limit), height.coerceAtLeast(constraints.minHeight)) {
            placed.forEach { (p, px, py) -> p.placeRelative(px, py) }
        }
    }
}

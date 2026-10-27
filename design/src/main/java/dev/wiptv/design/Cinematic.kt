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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.delay

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

    Box(modifier = modifier) {
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
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to WiptvColors.background,
                    textScrim to WiptvColors.background.copy(alpha = 0.8f),
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

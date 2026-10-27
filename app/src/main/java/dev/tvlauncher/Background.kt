package dev.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource

/** The launcher's background: charcoal navy, a little lighter behind the rows, darker at the edges. */
internal object LauncherBackground {
    val base = Color(0xFF101620)
    val centre = Color(0xFF1A2533)
    val edge = Color(0xFF0B1018)

    /** How strongly the grain shows: a matte finish, not a visible pattern. */
    const val GRAIN_ALPHA = 0.02f
}

/**
 * The launcher's screen background (the player keeps its own). A static glow centred on the
 * rows fades out to darker edges, under a very fine monochrome grain: one small bundled tile,
 * repeated (it tiles without seams), which also hides any banding in the gradient. Drawn behind
 * the content only, so posters, text and controls stay sharp.
 */
@Composable
internal fun Modifier.launcherBackground(): Modifier {
    val grainTile = ImageBitmap.imageResource(R.drawable.background_grain)
    val grain = remember(grainTile) { ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated)) }
    return drawBehind {
        drawRect(LauncherBackground.base)
        drawRect(
            Brush.radialGradient(
                0f to LauncherBackground.centre,
                0.5f to LauncherBackground.base,
                1f to LauncherBackground.edge,
                center = Offset(size.width * 0.45f, size.height * 0.55f),
                radius = size.width * 0.72f,
            ),
        )
        drawRect(grain, alpha = LauncherBackground.GRAIN_ALPHA)
    }
}

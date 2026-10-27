package dev.wiptv.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The WIPTV colours, from docs/player-design/tokens.json: cool blue-grey, with artwork and video
 * providing the colour. Text is light on dark; focused small controls flip to dark on light.
 */
object WiptvColors {
    val background = Color(0xFF111624)
    val backgroundTop = Color(0xFF182537)
    val surface = Color(0xFF202B42)
    val elevated = Color(0xFF33425B)
    val panel = Color(0xFF25354E)
    val text = Color(0xFFF5F7FF)
    val secondary = Color(0xFFC0C9DC)
    val quiet = Color(0xFFAEBBD0)
    val focus = Color(0xFFE7EEFF)
    val onLight = Color(0xFF202C42)
    val positive = Color(0xFFB4EED6)
    val warning = Color(0xFFFFD09B)
    val error = Color(0xFFFFB8B4)
    val progress = Color(0xFFDFEAFF)
}

/** Text sizes for the TV's 960 × 540 layout grid (platform sans-serif, no added fonts). */
object WiptvType {
    val hero = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text)
    val screen = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text)
    val section = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text)
    val control = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = WiptvColors.text)
    val metadata = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = WiptvColors.secondary)
    val hint = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = WiptvColors.quiet)
}

/** Corner shapes: small, cards and buttons, panels (preview, drawers, dialogs), and pills. */
object WiptvShapes {
    val small = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val panel = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * Glass recipes. There is no real blur (the Fire Stick can't afford it): the look comes from a
 * tinted panel, a faint white wash from the top, and a light edge that's brightest at the top.
 */
enum class Glass(val tint: Float, val washTop: Float, val washBottom: Float) {
    /** The section capsule and other navigation. */
    Nav(0.86f, 0.12f, 0.04f),

    /** Drawers, dialogs and side panels. Readable over anything. */
    Drawer(0.96f, 0.08f, 0.02f),

    /** Panels over video: at least 94% tint under any text. */
    OverVideo(0.94f, 0.08f, 0.02f),
}

/** Paints a glass panel behind the content, in [shape]. */
fun Modifier.glass(shape: Shape, recipe: Glass = Glass.Nav): Modifier = this
    .background(WiptvColors.panel.copy(alpha = recipe.tint), shape)
    .background(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = recipe.washTop), Color.White.copy(alpha = recipe.washBottom))),
        shape,
    )
    .border(
        BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.16f)))),
        shape,
    )

/** The screen behind everything: a quiet gradient from the top. */
fun Modifier.wiptvBackground(): Modifier = this.background(
    Brush.verticalGradient(0f to WiptvColors.backgroundTop, 0.55f to WiptvColors.background, 1f to WiptvColors.background),
)

/** A bar down the left edge, for "this is the selected one" when it isn't focused. */
fun Modifier.selectedMarker(selected: Boolean, color: Color = WiptvColors.focus): Modifier =
    if (!selected) this else this.drawBehind {
        drawRoundRect(color, topLeft = Offset(0f, size.height * 0.2f), size = Size(3.dp.toPx(), size.height * 0.6f), cornerRadius = CornerRadius(2.dp.toPx()))
    }

/** The 2 dp focus outline every focusable item uses. */
fun focusOutline(shape: Shape, width: Dp = 2.dp) =
    ClickableSurfaceDefaults.border(
        focusedBorder = Border(border = BorderStroke(width, WiptvColors.focus), shape = shape),
    )

/** The W badge and name, top left of each screen. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(24.dp).background(WiptvColors.focus, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "W", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WiptvColors.onLight))
        }
        Text(text = "WIPTV", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WiptvColors.text))
    }
}

/**
 * The floating capsule of sections (Live TV · Movies · Series, or the launcher's). The current one
 * keeps a soft wash and an underline; the focused one flips to a light fill. Focus alone never
 * switches: OK does.
 */
@Composable
fun SectionCapsule(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(40.dp).glass(WiptvShapes.pill).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Surface(
                onClick = { if (!isSelected) onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.pill),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) WiptvColors.focus.copy(alpha = 0.16f) else Color.Transparent,
                    contentColor = if (isSelected) WiptvColors.text else WiptvColors.secondary,
                    focusedContainerColor = WiptvColors.focus,
                    focusedContentColor = WiptvColors.onLight,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Column(
                    modifier = Modifier.height(32.dp).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = label, style = WiptvType.control.copy(fontSize = 15.sp, lineHeight = 18.sp))
                    if (isSelected) {
                        Box(Modifier.padding(top = 2.dp).width(18.dp).height(2.dp).background(WiptvColors.focus, WiptvShapes.pill))
                    }
                }
            }
        }
    }
}

/**
 * A button. Primary (Play, Resume, Sign in) rests on a light fill; others rest on glass. Focus
 * always adds the outline, so a light primary button still shows it has focus.
 */
@Composable
fun WiptvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val shape = WiptvShapes.card
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = if (primary) modifier else modifier.glass(shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) WiptvColors.focus else Color.Transparent,
            contentColor = if (primary) WiptvColors.onLight else WiptvColors.text,
            focusedContainerColor = WiptvColors.focus,
            focusedContentColor = WiptvColors.onLight,
            disabledContainerColor = WiptvColors.surface.copy(alpha = 0.6f),
            disabledContentColor = WiptvColors.quiet,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = focusOutline(shape),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 7.dp else 11.dp),
            style = if (compact) WiptvType.control.copy(fontSize = 14.sp, lineHeight = 18.sp) else WiptvType.control,
        )
    }
}

/** A small glass pill (the update button): quiet at rest, light when focused or when it needs you. */
@Composable
fun GlassPill(text: String, onClick: () -> Unit, loud: Boolean, modifier: Modifier = Modifier) {
    val shape = WiptvShapes.pill
    Surface(
        onClick = onClick,
        modifier = if (loud) modifier else modifier.glass(shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (loud) WiptvColors.focus else Color.Transparent,
            contentColor = if (loud) WiptvColors.onLight else WiptvColors.secondary,
            focusedContainerColor = WiptvColors.focus,
            focusedContentColor = WiptvColors.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = focusOutline(shape),
    ) {
        Text(
            text = text,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

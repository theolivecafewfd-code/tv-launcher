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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The WIPTV colours, shared by both apps (the only place they're defined). Version 0.3, after
 * the max player Wayne liked: near-black neutral greys with artwork and video providing the
 * colour, one lime accent for primary actions and progress, and white for focus (a white fill on
 * the menu, a white outline on cards and buttons). See docs/PLAYER_DESIGN.md, section 16.
 */
object WiptvColors {
    /** The screen: near-black. */
    val background = Color(0xFF0D0D0F)

    /** Raised background: the top of the screen's gradient, quiet side panels. */
    val backgroundTop = Color(0xFF141416)
    val raised = backgroundTop

    /** Panel surface: glass, trays, dialogs, placeholders. */
    val surface = Color(0xFF1C1C1F)

    /** Elevated surface: a focused list row's fill, the selected menu item, placeholders. */
    val elevated = Color(0xFF2C2C30)

    /** The one accent: primary buttons (Play), progress, the "now" line, labels like #1 TRENDING. */
    val accent = Color(0xFFC5E86C)

    /** The base of the glass recipes. */
    val panel = surface

    /** Primary, secondary and muted text. */
    val text = Color(0xFFF2F2F2)
    val secondary = Color(0xFFC4C4CA)
    val quiet = Color(0xFF8E8E94)
    val muted = quiet

    /** Focus: white (outlines, the focused menu item's fill). Primary buttons rest on the accent. */
    val focus = Color(0xFFF2F2F2)
    val primaryFill = accent
    val onLight = Color(0xFF121212)
    val onPrimary = onLight

    /** A subtle edge for panels and quiet buttons. */
    val border = Color.White.copy(alpha = 0.10f)

    val positive = Color(0xFFB4EED6)
    val warning = Color(0xFFFFD09B)
    val error = Color(0xFFFFB8B4)
    val progress = accent

    /** A focused list row (guide, episodes): a subtle raised fill and a short pale bar on the left. */
    val rowFocus = elevated
    val rowFocusMarker = focus

    /** The quiet side panel tint (Live TV categories). */
    val panelQuiet = raised

    /** Information sheets and trays (More info, the schedule, the Apps tray). */
    val sheet = surface
}

/**
 * Text styles for the TV's 960 × 540 layout grid, in the platform sans-serif (no added fonts),
 * shared by both apps. The semantic names (page title, section heading, body, metadata, caption)
 * are the ones to reach for; the others are kept for screens that already use them.
 */
object WiptvType {
    val hero = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text)
    val screen = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = WiptvColors.text)
    val section = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text)
    val control = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = WiptvColors.text)
    val metadata = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = WiptvColors.secondary)
    val hint = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = WiptvColors.quiet)

    /** A screen's title ("Choose your apps", "Live TV"). */
    val pageTitle = screen

    /** A row or tray heading ("Top 10 this week", "Your apps"). */
    val sectionHeading = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text)

    /** A name under a poster or tile. */
    val caption = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = WiptvColors.secondary)
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
enum class Glass(
    val tint: Float,
    val washTop: Float,
    val washBottom: Float,
    val edgeTop: Float = 0.16f,
    val edgeBottom: Float = 0.06f,
    val base: Color = WiptvColors.panel,
) {
    /** The section capsule and other navigation. */
    Nav(0.86f, 0.05f, 0.0f),

    /** Drawers, dialogs and side panels. Readable over anything. */
    Drawer(0.95f, 0.04f, 0.0f),

    /** Panels over video: at least 94% tint under any text. */
    OverVideo(0.94f, 0.04f, 0.0f),

    /** Side panels on a plain background (Live TV categories): barely there, with a faint edge. */
    Quiet(0.70f, 0.02f, 0.0f, edgeTop = 0.10f, edgeBottom = 0.04f, base = WiptvColors.panelQuiet),

    /** Information sheets (More info, a channel's schedule): solid dark slate with a subtle edge. */
    Sheet(0.97f, 0.03f, 0.0f, edgeTop = 0.14f, edgeBottom = 0.05f, base = WiptvColors.sheet),

    /** Floating trays (the launcher's Apps): dark translucent slate, a faint edge. */
    Tray(0.92f, 0.04f, 0.0f, edgeTop = 0.14f, edgeBottom = 0.05f, base = WiptvColors.sheet),
}

/** Paints a glass panel behind the content, in [shape]. */
fun Modifier.glass(shape: Shape, recipe: Glass = Glass.Nav): Modifier = this
    .background(recipe.base.copy(alpha = recipe.tint), shape)
    .background(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = recipe.washTop), Color.White.copy(alpha = recipe.washBottom))),
        shape,
    )
    .border(
        BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = recipe.edgeTop), Color.White.copy(alpha = recipe.edgeBottom)))),
        shape,
    )

/** How strongly the background grain shows: a matte finish, not a visible pattern. */
private const val GRAIN_ALPHA = 0.02f

/**
 * The screen behind everything, in both apps: a quiet gradient from raised charcoal at the top to
 * the background below, under a very fine, static monochrome grain (one small bundled tile,
 * repeated without seams, which also hides banding). Drawn behind the content only.
 */
@Composable
fun Modifier.wiptvBackground(): Modifier {
    val tile = ImageBitmap.imageResource(R.drawable.wiptv_grain)
    val grain = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    return drawBehind {
        drawRect(Brush.verticalGradient(0f to WiptvColors.backgroundTop, 0.55f to WiptvColors.background, 1f to WiptvColors.background))
        drawRect(grain, alpha = GRAIN_ALPHA)
    }
}

/**
 * The player's Home background: near-black, with a soft spotlight falling from the top centre
 * (a wide, faint white glow, no blur) and the same grain as [wiptvBackground].
 */
@Composable
fun Modifier.wiptvSpotlight(): Modifier {
    val tile = ImageBitmap.imageResource(R.drawable.wiptv_grain)
    val grain = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    return drawBehind {
        drawRect(WiptvColors.background)
        val top = Offset(size.width / 2f, 0f)
        // A circle of light stretched sideways into a wide, shallow pool.
        scale(scaleX = 2.2f, scaleY = 1f, pivot = top) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.13f),
                    0.45f to Color.White.copy(alpha = 0.05f),
                    1f to Color.Transparent,
                    center = top,
                    radius = size.height * 0.75f,
                ),
                radius = size.height * 0.75f,
                center = top,
            )
        }
        drawRect(grain, alpha = GRAIN_ALPHA)
    }
}

/** A bar down the left edge, for "this is the selected one" when it isn't focused. */
fun Modifier.selectedMarker(selected: Boolean, color: Color = WiptvColors.focus): Modifier =
    if (!selected) this else this.drawBehind {
        drawRoundRect(color, topLeft = Offset(0f, size.height * 0.2f), size = Size(3.dp.toPx(), size.height * 0.6f), cornerRadius = CornerRadius(2.dp.toPx()))
    }

/** The 2 dp focus outline every focusable item uses. */
@Composable
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
 * One control in a [NavCapsule]: a destination with a [label] (TV Shows, Apps), or an action
 * shown only as an [icon] (search), which then needs a [description] for accessibility. Only a
 * destination can be [selected].
 */
class NavItem(
    val label: String? = null,
    val icon: (@Composable (Color) -> Unit)? = null,
    val description: String = label.orEmpty(),
    val selected: Boolean = false,
    val modifier: Modifier = Modifier,
    val onClick: () -> Unit,
)

/**
 * A row of destinations and actions in one continuous glass capsule, with one background and a
 * faint edge (the top menu). Each item is focusable on its own, in the order given. The focused
 * item fills white with dark text; the selected destination keeps a grey fill and bright text
 * wherever focus is, so the two never look alike. OK acts; focus alone never does.
 *
 * [bare] (the player's top menu) drops the capsule behind the items: they sit straight on the
 * screen, and the selected one wears its own small glass pill instead of the grey fill.
 */
@Composable
fun NavCapsule(items: List<NavItem>, modifier: Modifier = Modifier, bare: Boolean = false) {
    Row(
        modifier = modifier
            .height(40.dp)
            .then(if (bare) Modifier else Modifier.glass(WiptvShapes.pill))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { item ->
            Surface(
                onClick = item.onClick,
                modifier = item.modifier
                    .then(if (bare && item.selected) Modifier.glass(WiptvShapes.pill) else Modifier)
                    .semantics { contentDescription = item.description },
                shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.pill),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (item.selected && !bare) WiptvColors.elevated else Color.Transparent,
                    contentColor = if (item.selected) WiptvColors.text else WiptvColors.secondary,
                    focusedContainerColor = WiptvColors.focus,
                    focusedContentColor = WiptvColors.onLight,
                    pressedContainerColor = WiptvColors.focus,
                    pressedContentColor = WiptvColors.onLight,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Column(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = if (item.label != null) 14.dp else 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val colour = LocalContentColor.current
                    if (item.label != null) {
                        Text(text = item.label, maxLines = 1, style = WiptvType.control.copy(fontSize = 15.sp, lineHeight = 18.sp))
                    } else {
                        item.icon?.invoke(colour)
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

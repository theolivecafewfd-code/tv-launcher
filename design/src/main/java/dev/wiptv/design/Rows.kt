package dev.wiptv.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface

/** How long a list row's focus fill and bar take to fade in and out. */
private const val ROW_FOCUS_MS = 120

/**
 * A row in a list (the Live TV guide, episodes, a channel's schedule, the full-screen channel and
 * group lists, menus), in both apps. No box at rest. Focus is a soft raised fill across the
 * whole row and a short pale bar at its left edge, with no outline, glow or scaling, so it's
 * never mistaken for "selected", "playing" or "favourite", which the row's own content shows.
 * A [selected] row (the current group) keeps a faint wash when it doesn't have focus.
 * [hairline] draws a faint line underneath, for lists without gaps. [content] is told whether
 * the row has focus.
 */
@Composable
fun ListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    hairline: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(10.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val rest = if (selected) WiptvColors.focus.copy(alpha = 0.08f) else WiptvColors.rowFocus.copy(alpha = 0f)
    val fill by animateColorAsState(if (focused) WiptvColors.rowFocus else rest, tween(ROW_FOCUS_MS), label = "rowFill")
    val marker by animateFloatAsState(if (focused) 1f else 0f, tween(ROW_FOCUS_MS), label = "rowMarker")
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .then(
                if (!hairline) Modifier else Modifier.drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(10.dp.toPx(), size.height - 0.5f),
                        end = Offset(size.width - 10.dp.toPx(), size.height - 0.5f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            )
            .fillMaxWidth(),
        tonalElevation = 0.dp,
        shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.small),
        // The fill is drawn below (so it can fade); the Surface itself paints nothing.
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
        // Only as tall as its content, or as the caller makes it (the guide's 48 dp rows):
        // filling the height would stretch a row in a menu over the options below it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(fill, cornerRadius = CornerRadius(8.dp.toPx()))
                    if (marker > 0f) {
                        // 3 × 24 dp (shorter on slim rows), centred, in the gap at the left.
                        val h = minOf(24.dp.toPx(), size.height - 12.dp.toPx())
                        drawRoundRect(
                            color = WiptvColors.rowFocusMarker.copy(alpha = marker),
                            topLeft = Offset(3.dp.toPx(), (size.height - h) / 2),
                            size = Size(3.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                }
                .padding(contentPadding),
            verticalAlignment = verticalAlignment,
            horizontalArrangement = horizontalArrangement,
        ) {
            content(focused)
        }
    }
}

package dev.wiptv.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * How a vertical list scrolls as the remote moves focus: anything already fully on screen stays
 * put, and something partly off screen settles [pivot] from the top every time, so rows land in
 * the same place instead of being nudged just far enough to show them. With [alwaysAlign], even
 * an item that's already on screen moves to the pivot, so the focused row is always at the top.
 *
 * The same rule then reaches every list inside, so wrap sideways rows in [RowScrolling].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScrolling(
    pivot: Dp,
    alwaysAlign: Boolean = false,
    /**
     * Going up to something shorter than this (a details page's buttons or season tabs) carries
     * on to the very top of the list in one movement, so the whole header shows again.
     */
    upToTopBelow: Dp? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val pivotPx = with(density) { pivot.toPx() }
    val upToTopPx = upToTopBelow?.let { with(density) { it.toPx() } }
    val spec = remember(pivotPx, alwaysAlign, upToTopPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                // The list stops at its top, so overshooting there is safe.
                if (upToTopPx != null && offset < 0f && size < upToTopPx) return offset - containerSize
                val fullyVisible = offset >= 0f && offset + size <= containerSize
                return if (fullyVisible && !alwaysAlign) 0f else offset - pivotPx
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * How a sideways row scrolls: the focused item stays fully on screen with [start] space before
 * it and [end] space after, so the next item always peeks in. Same behaviour on every device
 * (Fire TV's own default would otherwise pin focus a third of the way across).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScrolling(start: Dp, end: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val startPx = with(density) { start.toPx() }
    val endPx = with(density) { end.toPx() }
    val spec = remember(startPx, endPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                offset < startPx / 2 -> offset - startPx
                offset + size > containerSize - endPx / 2 -> offset + size - (containerSize - endPx)
                else -> 0f
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

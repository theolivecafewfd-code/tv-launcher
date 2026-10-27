package dev.tvlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Where a short item (the apps, the featured title) settles when it's partly off screen. */
private const val PIVOT = 0.14f

/**
 * How the home screen scrolls when the remote moves focus up or down. Posters (anything at least
 * [TallItem] high) always settle with their row's heading at the top, like the player's rows, so
 * each press moves exactly one row and the row above never peeks in. Shorter items stay put while
 * they're fully on screen; going up to one scrolls right to the top, and going down to one
 * settles it [PIVOT] of the way down.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PivotScroll(private val rowPivot: Float, private val tall: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size >= tall) return offset - rowPivot
        val fullyVisible = offset >= 0f && offset + size <= containerSize
        if (fullyVisible) return 0f
        // Going up to something short (the apps, the featured title, the buttons above it): carry
        // on to the very top in one smooth scroll, so the header shows. The list stops at its top.
        if (offset < 0f) return offset - containerSize
        return offset - containerSize * PIVOT
    }
}

/** Posters are 195 dp with their focus gap; the featured title (176 dp) and app tiles are shorter. */
private val TallItem = 185.dp

/** Space above a focused poster: room for its row's heading. */
private val RowPivot = 34.dp

/** The normal scrolling, kept so the sideways rows can use it inside the pivoting list. */
@OptIn(ExperimentalFoundationApi::class)
private val LocalNormalScroll = staticCompositionLocalOf<BringIntoViewSpec?> { null }

/** Wrap the up-and-down list: rows settle at a fixed spot as focus moves. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScrolling(content: @Composable () -> Unit) {
    val normal = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val spec = remember(density) {
        with(density) { PivotScroll(rowPivot = RowPivot.toPx(), tall = TallItem.toPx()) }
    }
    CompositionLocalProvider(
        LocalNormalScroll provides normal,
        LocalBringIntoViewSpec provides spec,
        content = content,
    )
}

/** Wrap a sideways row inside [PivotScrolling] so it scrolls normally. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalScrolling(content: @Composable () -> Unit) {
    val normal = LocalNormalScroll.current
    if (normal == null) {
        content()
    } else {
        CompositionLocalProvider(LocalBringIntoViewSpec provides normal, content = content)
    }
}

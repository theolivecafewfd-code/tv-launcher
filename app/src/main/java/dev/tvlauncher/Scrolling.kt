package dev.tvlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How the home screen scrolls when the remote moves focus up or down, like Netflix: anything
 * already fully on screen stays put, and a row that's partly off screen settles at the same
 * spot near the top every time, instead of being nudged just far enough to show it.
 */
@OptIn(ExperimentalFoundationApi::class)
private val PivotScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val fullyVisible = offset >= 0f && offset + size <= containerSize
        if (fullyVisible) return 0f
        return offset - containerSize * PIVOT
    }
}

/** Where a focused row settles: this fraction of the way down the screen. */
private const val PIVOT = 0.14f

/** The normal scrolling, kept so the sideways rows can use it inside the pivoting list. */
@OptIn(ExperimentalFoundationApi::class)
private val LocalNormalScroll = staticCompositionLocalOf<BringIntoViewSpec?> { null }

/** Wrap the up-and-down list: rows settle at a fixed spot as focus moves. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScrolling(content: @Composable () -> Unit) {
    val normal = LocalBringIntoViewSpec.current
    CompositionLocalProvider(
        LocalNormalScroll provides normal,
        LocalBringIntoViewSpec provides PivotScroll,
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

package dev.tvlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
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
private class PivotScroll(
    private val rowPivot: Float,
    private val tall: Float,
    private val resumeCard: Float,
    private val channelTile: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Posters, Continue watching's cards and channel tiles settle as rows do.
        if (size >= tall || kotlin.math.abs(size - resumeCard) < 2f || kotlin.math.abs(size - channelTile) < 2f) return offset - rowPivot
        val fullyVisible = offset >= 0f && offset + size <= containerSize
        if (fullyVisible) return 0f
        // Going up to something short (the apps, the featured title, the buttons above it): carry
        // on to the very top in one smooth scroll, so the header shows. The list stops at its top.
        if (offset < 0f) return offset - containerSize
        return offset - containerSize * PIVOT
    }
}

/**
 * Posters are 195 dp with their focus gap (189 + 2 × 3). The featured title (190 dp) and app
 * tiles are shorter and must stay below this, or the header gets scrolled off on the featured title.
 */
private val TallItem = 193.dp

/**
 * A Continue watching card (216 dp, its outline drawn inside): settles like a row, heading at the
 * top, so the hero's description isn't left half on screen above it. (Taller than [TallItem]
 * now, so this is a backstop.)
 */
private val ResumeCard = 216.dp

/** A channel tile with its focus gap (108 + 2 × 3): settles like a row although it's short. */
private val ChannelTile = 114.dp

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
        with(density) { PivotScroll(rowPivot = RowPivot.toPx(), tall = TallItem.toPx(), resumeCard = ResumeCard.toPx(), channelTile = ChannelTile.toPx()) }
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

/**
 * Focus targets that are on screen right now. A row further down the home screen isn't built
 * until it scrolls near, and sending focus to a row that isn't built yet crashed the launcher
 * when Down was pressed quickly. Rows register here while they exist; until then the remote
 * just moves focus the normal way.
 */
private val attachedRequesters = mutableSetOf<FocusRequester>()

/** Marks [requester] as on screen for as long as the calling row is. */
@Composable
internal fun TrackAttached(requester: FocusRequester) {
    DisposableEffect(requester) {
        attachedRequesters += requester
        onDispose { attachedRequesters -= requester }
    }
}

/** This requester if its row is on screen, otherwise null (use the normal focus move). */
internal fun FocusRequester?.ifAttached(): FocusRequester? = this?.takeIf { it in attachedRequesters }

package dev.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

// The home screen's up-and-down scrolling is the shared PivotScrolling (design module), as in
// the player: the focused row always moves to the top of the list.

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

/**
 * Remembers which card you were on in a row, so coming back to the row lands on it again.
 * (Compose's own focusRestorer crashed the launcher — "Release should only be called once" —
 * when a row's cards changed, so this uses a plain enter rule instead, as Live TV does.)
 */
internal class RowMemory {
    private var last: Any? = null
    private val requesters = mutableMapOf<Any, FocusRequester>()
    private val onScreen = mutableSetOf<Any>()

    /** For the row: focus entering it goes to the last card, if that card is still there. */
    @OptIn(ExperimentalComposeUiApi::class)
    fun rowModifier(): Modifier = Modifier
        .focusProperties { enter = { last?.takeIf { it in onScreen }?.let { requesters[it] } ?: FocusRequester.Default } }
        .focusGroup()

    /** For each card, keyed like the row's items. */
    @Composable
    fun card(key: Any): Modifier {
        DisposableEffect(key) {
            onScreen += key
            onDispose { onScreen -= key }
        }
        return Modifier
            .focusRequester(requesters.getOrPut(key) { FocusRequester() })
            .onFocusChanged { if (it.isFocused) last = key }
    }
}

@Composable
internal fun rememberRowMemory(): RowMemory = remember { RowMemory() }

package dev.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester

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

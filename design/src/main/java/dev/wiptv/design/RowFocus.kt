package dev.wiptv.design

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

/**
 * Moving up or down into a row of cards lands on its first card, in both apps. Without it the
 * remote picks the card under the one you left, and a widened card (the expanding cards) put that
 * further and further to the right. Left and Right within the row are unchanged.
 *
 * Give the row [rowModifier] with its first card's key, and each card [card] with its own key.
 * If the first card isn't on screen (the row is scrolled along), the normal move applies.
 * (Compose's focusRestorer isn't used: it crashed with rows whose cards change.)
 */
class RowStart internal constructor() {
    private val requesters = mutableMapOf<Any, FocusRequester>()
    private val onScreen = mutableSetOf<Any>()

    @OptIn(ExperimentalComposeUiApi::class)
    fun rowModifier(firstKey: Any?): Modifier = Modifier
        .focusProperties {
            enter = { firstKey?.takeIf { it in onScreen }?.let { requesters[it] } ?: FocusRequester.Default }
        }
        .focusGroup()

    @Composable
    fun card(key: Any): Modifier {
        DisposableEffect(key) {
            onScreen += key
            onDispose { onScreen -= key }
        }
        return Modifier.focusRequester(requesters.getOrPut(key) { FocusRequester() })
    }
}

@Composable
fun rememberRowStart(): RowStart = remember { RowStart() }

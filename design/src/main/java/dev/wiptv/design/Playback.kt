package dev.wiptv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

// The overlays shown while something plays (live TV's info bar, a film or episode's progress),
// shared so they look the same wherever you watch.

private val PanelShape = RoundedCornerShape(24.dp)

/**
 * The panel along the bottom of the picture while watching: dark slate glass that floats 24 dp
 * in from the edges, readable over any video (at least 94% tint under its text), with no blur.
 */
@Composable
fun PlaybackPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .fillMaxWidth()
            .glass(PanelShape, Glass.OverVideo)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        content = content,
    )
}

/** A thin progress track: how far through the programme or film you are. */
@Composable
fun PlaybackProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(WiptvColors.progress, RoundedCornerShape(2.dp)),
        )
    }
}

/** What the remote does here, in one quiet line ("▲▼ change", "OK pause"), with faint dots between. */
@Composable
fun KeyHints(hints: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEachIndexed { i, hint ->
            if (i > 0) Box(Modifier.width(3.dp).height(3.dp).background(WiptvColors.border, RoundedCornerShape(50)))
            Text(text = hint, maxLines = 1, style = WiptvType.hint)
        }
    }
}

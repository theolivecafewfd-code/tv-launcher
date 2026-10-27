package dev.wiptv.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

// Line-drawn icons shared by both apps' headers.

/** A line-drawn magnifying glass. */
@Composable
fun SearchIcon(color: Color, size: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.13f
        drawCircle(
            color = color,
            radius = w * 0.3f,
            center = androidx.compose.ui.geometry.Offset(w * 0.42f, w * 0.42f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.65f, w * 0.65f),
            end = androidx.compose.ui.geometry.Offset(w * 0.92f, w * 0.92f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/** A line-drawn die, for Surprise Me. */
@Composable
fun DiceIcon(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.08f, w * 0.08f),
            size = Size(w * 0.84f, w * 0.84f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f),
            style = Stroke(width = w * 0.11f),
        )
        for ((x, y) in listOf(0.32f to 0.32f, 0.5f to 0.5f, 0.68f to 0.68f)) {
            drawCircle(color, radius = w * 0.075f, center = Offset(w * x, w * y))
        }
    }
}

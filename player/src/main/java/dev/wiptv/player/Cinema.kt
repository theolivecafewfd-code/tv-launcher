package dev.wiptv.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.focusOutline
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.wiptv.design.WiptvShapes
import kotlinx.coroutines.delay

/** A thin rounded progress bar, readable over any picture. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(
        modifier = modifier
            .height(height)
            .background(Color.White.copy(alpha = 0.24f), WiptvShapes.pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(WiptvColors.progress, WiptvShapes.pill),
        )
    }
}

/** "38 min left" or "1 h 12 min left", or null when the length isn't known. */
internal fun timeLeft(point: ResumePoint): String? {
    if (point.duration <= 0) return null
    val minutes = ((point.duration - point.position).coerceAtLeast(0) + 59_999) / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min left" else "$minutes min left"
}

/** "1h 52m", or "48m" under an hour. */
internal fun runtimeOf(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/** The provider's score, labelled as a score (it isn't a certificate or a named rating). */
internal fun scoreOf(rating: Double?): String? = rating?.let { "Score %.1f".format(it) }

package dev.tvlauncher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import dev.wiptv.design.glass
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shortcuts to Fire TV / Android TV settings pages. Apps aren't allowed to switch Wi-Fi or
 * Bluetooth on and off themselves, so each one opens the right settings page instead.
 * [actions] are tried in order, because not every TV has every page.
 */
enum class QuickSetting(val label: String, val actions: List<String>) {
    WIFI("Wi-Fi", listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)),
    BLUETOOTH("Bluetooth", listOf(Settings.ACTION_BLUETOOTH_SETTINGS)),
    DISPLAY("Display & sound", listOf(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS)),
    ALL("Settings", emptyList());

    val key: String get() = "qs:$name"

    /** Opens the page, falling back to the main Settings screen. */
    fun open(context: Context) {
        for (action in actions + Settings.ACTION_SETTINGS) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // Try the next one.
            }
        }
        Toast.makeText(context, "Couldn't open $label", Toast.LENGTH_SHORT).show()
    }
}

/** A small square tile at the end of Your apps for one quick setting. */
@Composable
fun QuickSettingTile(setting: QuickSetting, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { setting.open(context) },
        modifier = modifier
            .width(92.dp)
            .height(81.dp)
            .glass(shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.quiet,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            SettingIcon(setting, color = Palette.text, size = 22.dp)
            Text(text = setting.label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Line-drawn icons for the quick settings, matching the weather and search icons. */
@Composable
private fun SettingIcon(setting: QuickSetting, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (setting) {
            QuickSetting.WIFI -> {
                val c = Offset(w / 2, w * 0.82f)
                for (r in listOf(0.28f, 0.52f, 0.76f)) {
                    drawArc(color, 225f, 90f, false, Offset(c.x - w * r, c.y - w * r), Size(w * r * 2, w * r * 2), style = stroke)
                }
                drawCircle(color, radius = w * 0.07f, center = c)
            }
            QuickSetting.BLUETOOTH -> {
                val x = w * 0.48f
                val top = Offset(x, w * 0.08f)
                val bottom = Offset(x, w * 0.92f)
                val right1 = Offset(w * 0.74f, w * 0.30f)
                val right2 = Offset(w * 0.74f, w * 0.70f)
                drawLine(color, top, bottom, stroke.width, StrokeCap.Round)
                drawLine(color, top, right1, stroke.width, StrokeCap.Round)
                drawLine(color, right1, Offset(w * 0.24f, w * 0.72f), stroke.width, StrokeCap.Round)
                drawLine(color, bottom, right2, stroke.width, StrokeCap.Round)
                drawLine(color, right2, Offset(w * 0.24f, w * 0.28f), stroke.width, StrokeCap.Round)
            }
            QuickSetting.DISPLAY -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.08f, w * 0.14f),
                    size = Size(w * 0.84f, w * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.32f, w * 0.88f), Offset(w * 0.68f, w * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.70f), Offset(w * 0.5f, w * 0.88f), stroke.width, StrokeCap.Round)
            }
            QuickSetting.ALL -> {
                val c = Offset(w / 2, w / 2)
                drawCircle(color, radius = w * 0.22f, center = c, style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    drawLine(
                        color,
                        Offset(c.x + w * 0.30f * cos(a).toFloat(), c.y + w * 0.30f * sin(a).toFloat()),
                        Offset(c.x + w * 0.44f * cos(a).toFloat(), c.y + w * 0.44f * sin(a).toFloat()),
                        stroke.width,
                        StrokeCap.Round,
                    )
                }
            }
        }
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

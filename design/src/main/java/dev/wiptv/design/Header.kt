package dev.wiptv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Your name for the greeting. */
private const val OWNER_NAME = "Wayne"

/**
 * The header across the top of the launcher and the player, identical in both so moving between
 * them feels like one app: the W badge and greeting on the left; weather, clock and the build in
 * one status pill on the right; and below them the navigation capsule ([nav], in the launcher's
 * order: TV Shows · Movies · Live TV · Apps · search · Surprise me). The weather and clock look
 * after themselves; only the build part of the status pill takes focus ([onUpdateClick] checks
 * again or installs an update). The caller adds the side margins.
 */
@Composable
fun WiptvHeader(
    nav: List<NavItem>,
    updateLabel: String,
    updateLoud: Boolean,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(15_000)
        }
    }
    var weather by remember { mutableStateOf(Weather.last) }
    LaunchedEffect(Unit) {
        while (true) {
            Weather.fetch()?.let { weather = it }
            delay(30 * 60_000L)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // The W badge and the greeting.
            Row(
                modifier = Modifier.height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(26.dp).background(WiptvColors.focus, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "W", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WiptvColors.onLight))
                }
                Text(text = "${greeting(now)}, $OWNER_NAME", style = WiptvType.screen)
            }
            // Weather, clock and the build in one compact glass pill.
            Row(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .height(30.dp)
                    .glass(WiptvShapes.pill)
                    .padding(start = 14.dp, end = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weather?.let { w ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(sky = w.sky, color = WiptvColors.secondary, size = 15.dp)
                        Text(text = "${w.temperature}°", style = StatusText)
                    }
                    StatusDivider()
                }
                Text(text = now.format(DateTimeFormatter.ofPattern("HH:mm")), style = StatusText)
                StatusDivider()
                UpdateButton(label = updateLabel, loud = updateLoud, onClick = onUpdateClick)
            }
        }
        Spacer(Modifier.height(12.dp))
        NavCapsule(items = nav)
    }
}

private fun greeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

/** The status pill's text: small and subdued. */
private val StatusText = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = WiptvColors.secondary)

/** A faint line between the status pill's parts. */
@Composable
private fun StatusDivider() {
    Box(Modifier.width(1.dp).height(12.dp).background(WiptvColors.border))
}

/**
 * The build part of the status pill, always there: the installed build, lit when a newer one is
 * ready. OK checks again, or installs the update when there is one.
 */
@Composable
private fun UpdateButton(label: String, loud: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(24.dp).widthIn(max = 320.dp),
        shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.pill),
        colors = ClickableSurfaceDefaults.colors(
            // Bright when there's something to do, quiet otherwise.
            containerColor = if (loud) WiptvColors.primaryFill else Color.Transparent,
            contentColor = if (loud) WiptvColors.onPrimary else WiptvColors.secondary,
            focusedContainerColor = WiptvColors.primaryFill,
            focusedContentColor = WiptvColors.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.height(24.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(text = label, maxLines = 1, style = StatusText.copy(color = LocalContentColor.current))
        }
    }
}

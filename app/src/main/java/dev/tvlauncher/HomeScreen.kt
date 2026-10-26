package dev.tvlauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Your name for the greeting. Moves to a settings screen in a later phase. */
private const val OWNER_NAME = "Wayne"

// Sizes are in dp on the TV's 960 x 540 layout grid (a 1080p TV at the standard TV density),
// which is three-quarters of the 1280 x 720 mockups.
private object Palette {
    val background = Color(0xFF16111E)
    val text = Color(0xFFF4F1F8)
    val quiet = Color(0xFFB3AAC2)
    val tile = Color(0xFF241D30)
    val tileFocused = Color(0xFF332A44)
    val glow = Color(0xFFEDE4FF)
}

@Composable
fun HomeScreen(refreshKey: Int, onLaunch: (TvApp) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(refreshKey) {
        apps = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(15_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 28.dp, bottom = 24.dp),
            ) {
                Header(now)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Your apps",
                    modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.quiet),
                )
                if (apps.isEmpty()) {
                    Text(
                        text = "Loading apps…",
                        modifier = Modifier.padding(start = 40.dp),
                        style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
                    )
                } else {
                    AppDock(apps = apps, onLaunch = onLaunch)
                }
            }
        }
    }
}

@Composable
private fun Header(now: LocalTime) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${greeting(now)}, $OWNER_NAME",
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.text),
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Palette.text),
        )
    }
}

private fun greeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun AppDock(apps: List<TvApp>, onLaunch: (TvApp) -> Unit) {
    val firstTile = remember { FocusRequester() }

    // Put the remote's focus on the first app when the screen opens.
    // The first tile may not be laid out yet on the first frame, so retry briefly.
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { firstTile.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                onClick = { onLaunch(app) },
                modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
            )
        }
    }
}

@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(144.dp)
            .height(81.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.tile,
            contentColor = Palette.text,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Palette.glow.copy(alpha = 0.45f), elevation = 10.dp),
        ),
    ) {
        val image = app.image
        if (image != null && app.isBanner) {
            Image(
                bitmap = image,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                )
            }
        }
    }
}

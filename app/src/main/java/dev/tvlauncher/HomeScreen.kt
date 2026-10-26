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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Your name for the greeting. Moves to a settings screen in a later phase. */
private const val OWNER_NAME = "Wayne"

// Sizes are in dp on the TV's 960 x 540 layout grid (a 1080p TV at the standard TV density),
// which is three-quarters of the 1280 x 720 mockups.
internal object Palette {
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
    var backdrop by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        apps = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }

    // Check GitHub for a newer build when the screen opens, then at most every 30 minutes.
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var lastUpdateCheck by remember { mutableLongStateOf(0L) }
    val checkNow: () -> Unit = {
        scope.launch {
            update = UpdateState.Checking
            lastUpdateCheck = SystemClock.elapsedRealtime()
            update = Updater.check()
        }
    }
    LaunchedEffect(refreshKey) {
        // Back on screen while still "installing" means the install was cancelled: check again.
        if (update is UpdateState.Installing) lastUpdateCheck = 0L
        val busy = update is UpdateState.Downloading || update is UpdateState.Checking && lastUpdateCheck != 0L
        val due = lastUpdateCheck == 0L || SystemClock.elapsedRealtime() - lastUpdateCheck > 30 * 60_000L
        if (due && !busy) checkNow()
    }
    val onUpdateClick: () -> Unit = onUpdateClick@{
        val target = when (val current = update) {
            is UpdateState.Available -> current
            is UpdateState.Failed -> current.retry
            UpdateState.UpToDate, is UpdateState.CheckFailed, UpdateState.Installing -> {
                checkNow()
                return@onUpdateClick
            }
            else -> return@onUpdateClick
        }
        if (!Updater.canInstall(context)) {
            Updater.openInstallPermissionSettings(context)
            return@onUpdateClick
        }
        scope.launch {
            update = UpdateState.Downloading(0)
            try {
                Updater.downloadAndInstall(context, target) { percent ->
                    update = UpdateState.Downloading(percent)
                }
                update = UpdateState.Installing
            } catch (e: Exception) {
                update = UpdateState.Failed(e.message ?: "download failed", target)
            }
        }
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
            // The featured film's backdrop, faint and darkened, tints the whole screen.
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.22f,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Palette.background.copy(alpha = 0.55f),
                                0.7f to Palette.background.copy(alpha = 0.9f),
                                1f to Palette.background,
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = 16.dp),
            ) {
                Header(now = now, update = update, onUpdateClick = onUpdateClick)
                Spacer(Modifier.height(10.dp))
                FilmCarousel(apps = apps, onLaunch = onLaunch, onBackdrop = { backdrop = it })
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 40.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "Your apps",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.quiet),
                    )
                    // Which build is installed, so it's easy to tell whether an update landed.
                    Text(
                        text = "Build ${BuildConfig.VERSION_CODE}",
                        style = TextStyle(fontSize = 11.sp, color = Palette.quiet.copy(alpha = 0.7f)),
                    )
                }
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
private fun Header(now: LocalTime, update: UpdateState, onUpdateClick: () -> Unit) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpdateButton(update = update, onClick = onUpdateClick)
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Palette.text),
            )
        }
    }
}

/**
 * A pill in the header that's always there: it shows the installed build, and lights up when a
 * newer one is on GitHub. Pressing it checks again, or installs the update when there is one.
 */
@Composable
private fun UpdateButton(update: UpdateState, onClick: () -> Unit) {
    val installed = BuildConfig.VERSION_CODE
    val label = when (update) {
        UpdateState.Checking -> "Checking for updates…"
        UpdateState.UpToDate -> "Up to date · build $installed"
        is UpdateState.CheckFailed -> "Couldn't check: ${update.reason}"
        is UpdateState.Available -> "Update available · build ${update.build}"
        is UpdateState.Downloading -> "Downloading update… ${update.percent}%"
        UpdateState.Installing -> "Installing update…"
        is UpdateState.Failed -> "Update failed · press to retry"
    }
    // Bright when there's something to do, quiet otherwise.
    val loud = update is UpdateState.Available || update is UpdateState.Failed
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (loud) Palette.glow else Palette.tile,
            contentColor = if (loud) Palette.background else Palette.quiet,
            focusedContainerColor = Color.White,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Palette.glow.copy(alpha = 0.5f), elevation = 10.dp),
        ),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
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
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
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

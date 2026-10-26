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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    var weather by remember { mutableStateOf<WeatherNow?>(null) }

    // Weather for the header, refreshed every 30 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            Weather.fetch()?.let { weather = it }
            delay(30 * 60_000L)
        }
    }

    // Which apps you've hidden and the order you've chosen, saved on the device.
    val dockPrefs = remember { DockPrefs(context) }
    var hidden by remember { mutableStateOf(dockPrefs.hidden()) }
    var order by remember { mutableStateOf(dockPrefs.order()) }
    val dockApps = remember(apps, hidden, order) { DockPrefs.arrange(apps, hidden, order) }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var editing by remember { mutableStateOf(false) }
    // After a menu action, which tile the remote's focus should land on.
    var focusKey by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    fun focusOn(key: String?) {
        focusKey = key
        focusTick++
    }

    fun saveOrder(newOrder: List<TvApp>) {
        order = newOrder.map { it.packageName }
        dockPrefs.setOrder(order)
    }
    fun setHidden(newHidden: Set<String>) {
        hidden = newHidden
        dockPrefs.setHidden(newHidden)
    }

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
            // While choosing apps, the editor replaces the home screen so the remote can't
            // wander onto the tiles underneath.
            if (!editing) Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = 16.dp),
            ) {
                Header(now = now, weather = weather, update = update, onUpdateClick = onUpdateClick)
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
                    AppDock(
                        apps = dockApps,
                        onLaunch = onLaunch,
                        onLongPress = { menuFor = it },
                        onEdit = { editing = true },
                        focusKey = focusKey,
                        focusTick = focusTick,
                    )
                }
            }

            // Hold OK on an app: hide it or move it.
            menuFor?.let { app ->
                val index = dockApps.indexOfFirst { it.packageName == app.packageName }
                AppMenu(
                    app = app,
                    canMoveLeft = index > 0,
                    canMoveRight = index in 0 until dockApps.size - 1,
                    onHide = {
                        setHidden(hidden + app.packageName)
                        val neighbour = dockApps.getOrNull(index + 1) ?: dockApps.getOrNull(index - 1)
                        focusOn(neighbour?.packageName ?: EDIT_TILE_KEY)
                        menuFor = null
                    },
                    onMoveLeft = {
                        val list = dockApps.toMutableList()
                        list.add(index - 1, list.removeAt(index))
                        saveOrder(list)
                        focusOn(app.packageName)
                        menuFor = null
                    },
                    onMoveRight = {
                        val list = dockApps.toMutableList()
                        list.add(index + 1, list.removeAt(index))
                        saveOrder(list)
                        focusOn(app.packageName)
                        menuFor = null
                    },
                    onClose = {
                        focusOn(app.packageName)
                        menuFor = null
                    },
                )
            }

            // "Edit apps": every app on the TV, OK to show or hide.
            if (editing) {
                AppEditor(
                    apps = apps,
                    hidden = hidden,
                    onToggle = { app ->
                        setHidden(if (app.packageName in hidden) hidden - app.packageName else hidden + app.packageName)
                    },
                    onDone = {
                        editing = false
                        focusOn(EDIT_TILE_KEY)
                    },
                )
            }
        }
    }
}

/** Focus key for the "Edit apps" tile at the end of the dock. */
private const val EDIT_TILE_KEY = "__edit_apps__"

@Composable
private fun Header(now: LocalTime, weather: WeatherNow?, update: UpdateState, onUpdateClick: () -> Unit) {
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
        Column(horizontalAlignment = Alignment.End) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UpdateButton(update = update, onClick = onUpdateClick)
                if (weather != null) {
                    WeatherIcon(sky = weather.sky, color = Palette.text, size = 20.dp)
                    Text(
                        text = "${weather.temperature}°",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                    )
                    Text(
                        text = weather.description,
                        style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Palette.quiet.copy(alpha = 0.4f)),
                    )
                }
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Palette.text),
                )
            }
            if (weather != null) {
                Text(
                    text = "${Weather.placeName} · ${weather.outlook}",
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
                )
            }
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
private fun AppDock(
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onLongPress: (TvApp) -> Unit,
    onEdit: () -> Unit,
    focusKey: String?,
    focusTick: Int,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String) = requesters.getOrPut(key) { FocusRequester() }

    // Put the remote's focus on the first app when the screen opens, and back on the right
    // tile after the menu or editor closes. Tiles may not be laid out on the first frame,
    // so retry briefly.
    LaunchedEffect(focusKey, focusTick) {
        val key = focusKey ?: apps.firstOrNull()?.packageName ?: EDIT_TILE_KEY
        repeat(20) {
            if (runCatching { requesterFor(key).requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppTile(
                app = app,
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) },
                modifier = Modifier.focusRequester(requesterFor(app.packageName)),
            )
        }
        item(key = EDIT_TILE_KEY) {
            EditAppsTile(
                onClick = onEdit,
                modifier = Modifier.focusRequester(requesterFor(EDIT_TILE_KEY)),
            )
        }
    }
}

/** The last tile in the dock: opens the screen for choosing which apps show. */
@Composable
private fun EditAppsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(144.dp)
            .height(81.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.quiet,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, Palette.quiet.copy(alpha = 0.5f)), shape = shape),
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "+", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium))
            Text(text = "Edit apps", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/**
 * One app tile. [shown] is only set on the "Choose your apps" screen: a tick when the app shows
 * in the dock, dimmed when it's hidden.
 */
@Composable
internal fun AppTile(
    app: TvApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shown: Boolean? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(144.dp)
            .height(81.dp)
            .alpha(if (shown == false) 0.4f else 1f),
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
      Box(Modifier.fillMaxSize()) {
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
        if (shown != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(if (shown) Palette.glow else Palette.background.copy(alpha = 0.8f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (shown) "✓" else "–",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (shown) Palette.background else Palette.quiet),
                )
            }
        }
      }
    }
}

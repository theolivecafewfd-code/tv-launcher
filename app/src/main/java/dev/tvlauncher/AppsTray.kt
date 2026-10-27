package dev.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.ArtFrame
import dev.wiptv.design.Glass
import dev.wiptv.design.RowScrolling
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import kotlinx.coroutines.delay

/** Focus key for the "Edit apps" tile at the end of the tray. */
internal const val EDIT_TILE_KEY = "__edit_apps__"

/**
 * App tiles in the tray: 16:9 like an Android TV banner, so banners show whole. About six fit
 * across, as in Wayne's mockup; they're sized from the tray's width, between these limits.
 */
private val TrayTileMin = 120.dp
private val TrayTileMax = 156.dp
private const val TILES_ACROSS = 6
private val TrayGap = 16.dp

private val TrayShape = RoundedCornerShape(24.dp)
private const val TRAY_MS = 200

/**
 * The Apps tray: your apps in one row on a floating glass tray at the bottom of the screen, opened
 * with OK on Apps at the top of the home screen. The home screen stays as it was behind a light
 * scrim. Focus starts on the app you were last on (or the first), Left and Right move along the
 * row and can't leave the tray, OK opens the app, holding OK opens its menu (hide, move), and
 * Back closes the tray (the caller puts focus back on Apps).
 *
 * Quick settings and "Edit apps" follow the apps, as they did in the old Your apps row.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppsTray(
    open: Boolean,
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onLongPress: (TvApp) -> Unit,
    onEdit: () -> Unit,
    /** After a menu or editor action, the tile to land on; [focusTick] asks again. */
    focusKey: String?,
    focusTick: Int,
    /** False while the app menu or the editor is over the tray. */
    backEnabled: Boolean,
    onClose: () -> Unit,
) {
    // The tile you were last on, kept while the launcher runs.
    var lastKey by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = open && backEnabled, onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(TRAY_MS)),
            exit = fadeOut(tween(TRAY_MS)),
        ) {
            // A light scrim: the home screen dims, without blur.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(TRAY_MS)) + slideInVertically(tween(TRAY_MS)) { it / 4 },
            exit = fadeOut(tween(TRAY_MS)) + slideOutVertically(tween(TRAY_MS)) { it / 4 },
        ) {
            val keys = apps.map { it.packageName } + QuickSetting.entries.map { it.key } + EDIT_TILE_KEY
            val requesters = remember { mutableMapOf<String, FocusRequester>() }
            fun requesterFor(key: String) = requesters.getOrPut(key) { FocusRequester() }
            val rowState = rememberLazyListState()

            // On opening: the tile you were last on, if it's still there, else the first. After a
            // menu or editor action: the tile the caller names.
            val openedTick = remember { focusTick }
            LaunchedEffect(Unit) { focusTile(keys, lastKey, rowState, ::requesterFor) }
            LaunchedEffect(focusKey, focusTick) {
                if (focusTick != openedTick) focusTile(keys, focusKey, rowState, ::requesterFor)
            }

            Column(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .fillMaxWidth()
                    .shadow(elevation = 16.dp, shape = TrayShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .glass(TrayShape, Glass.Tray)
                    // The remote stays in the tray until it's closed.
                    .focusProperties { exit = { if (open) FocusRequester.Cancel else FocusRequester.Default } }
                    .focusGroup()
                    .padding(top = 20.dp, bottom = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Your apps", style = WiptvType.section, modifier = Modifier.weight(1f))
                    BackIcon()
                    Text(text = "Back to close", style = WiptvType.metadata, modifier = Modifier.padding(start = 8.dp))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Six tiles across the tray's inner width, within the size limits.
                val tileWidth = ((maxWidth - 48.dp - TrayGap * (TILES_ACROSS - 1)) / TILES_ACROSS).coerceIn(TrayTileMin, TrayTileMax)
                val tileHeight = tileWidth * 9f / 16f
                val smallWidth = tileHeight * 1.2f
                RowScrolling(start = 24.dp, end = 48.dp) {
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(start = 24.dp - ArtFocusGap, end = 24.dp - ArtFocusGap, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(TrayGap - ArtFocusGap * 2),
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            TrayItem(label = app.label, width = tileWidth) { focused ->
                                AppTile(
                                    app = app,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { onLongPress(app) },
                                    width = tileWidth,
                                    height = tileHeight,
                                    labelInside = false,
                                    modifier = Modifier
                                        .focusRequester(requesterFor(app.packageName))
                                        .onFocusChanged {
                                            focused(it.isFocused)
                                            if (it.isFocused) lastKey = app.packageName
                                        },
                                )
                            }
                        }
                        items(QuickSetting.entries, key = { it.key }) { setting ->
                            TrayItem(label = setting.label, width = smallWidth) { focused ->
                                QuickSettingTile(
                                    setting = setting,
                                    width = smallWidth,
                                    height = tileHeight,
                                    showLabel = false,
                                    modifier = Modifier
                                        .focusRequester(requesterFor(setting.key))
                                        .onFocusChanged {
                                            focused(it.isFocused)
                                            if (it.isFocused) lastKey = setting.key
                                        },
                                )
                            }
                        }
                        item(key = EDIT_TILE_KEY) {
                            TrayItem(label = "Edit apps", width = smallWidth) { focused ->
                                EditAppsTile(
                                    onClick = onEdit,
                                    width = smallWidth,
                                    height = tileHeight,
                                    modifier = Modifier
                                        .focusRequester(requesterFor(EDIT_TILE_KEY))
                                        .onFocusChanged {
                                            focused(it.isFocused)
                                            if (it.isFocused) lastKey = EDIT_TILE_KEY
                                        },
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

/** A thin circle with a left arrow, beside "Back to close". */
@Composable
private fun BackIcon() {
    val colour = WiptvColors.secondary
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val stroke = w * 0.07f
        drawCircle(colour, radius = w / 2 - stroke, style = Stroke(stroke))
        drawLine(colour, Offset(w * 0.30f, w / 2), Offset(w * 0.70f, w / 2), stroke, StrokeCap.Round)
        drawLine(colour, Offset(w * 0.30f, w / 2), Offset(w * 0.47f, w * 0.33f), stroke, StrokeCap.Round)
        drawLine(colour, Offset(w * 0.30f, w / 2), Offset(w * 0.47f, w * 0.67f), stroke, StrokeCap.Round)
    }
}

/** Scrolls the tile on screen and focuses it; falls back to the first tile if it's gone. */
private suspend fun focusTile(
    keys: List<String>,
    wanted: String?,
    rowState: androidx.compose.foundation.lazy.LazyListState,
    requesterFor: (String) -> FocusRequester,
) {
    val key = wanted?.takeIf { it in keys } ?: keys.first()
    runCatching { rowState.scrollToItem(keys.indexOf(key).coerceAtLeast(0)) }
    // Tiles may not be laid out on the first frame, so retry briefly.
    repeat(20) {
        if (runCatching { requesterFor(key).requestFocus() }.isSuccess) return
        delay(50)
    }
}

/** A tray tile with its name centred underneath, a little bolder when the tile has focus. */
@Composable
private fun TrayItem(label: String, width: Dp, tile: @Composable (onFocus: (Boolean) -> Unit) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(width + ArtFocusGap * 2), horizontalAlignment = Alignment.CenterHorizontally) {
        tile { focused = it }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = ArtFocusGap, end = ArtFocusGap, top = 8.dp),
            style = WiptvType.caption.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = WiptvColors.text,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

/** Opens the screen for choosing which apps show. */
@Composable
internal fun EditAppsTile(onClick: () -> Unit, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    ArtFrame(width = width, height = height, onFocusChange = { focused = it }, onClick = onClick, modifier = modifier) {
        Text(
            text = "+",
            modifier = Modifier.align(Alignment.Center),
            style = WiptvType.section.copy(fontSize = 26.sp, color = if (focused) WiptvColors.text else WiptvColors.secondary),
        )
    }
}

package dev.tvlauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.tv.material3.Border
import androidx.tv.material3.Glow
import dev.wiptv.design.Glass
import dev.wiptv.design.WiptvButton
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Full-screen list of every app on the TV. OK on an app shows or hides it in "Your apps";
 * Back (or Done) closes the screen.
 */
@Composable
fun AppEditor(
    apps: List<TvApp>,
    hidden: Set<String>,
    onToggle: (TvApp) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    // Apps you show first, then the hidden ones, so it's clear what's in the dock.
    val sorted = apps.sortedBy { if (it.packageName in hidden) 1 else 0 }
    val shownCount = apps.count { it.packageName !in hidden }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .launcherBackground(),
    ) {
        Column(modifier = Modifier.padding(top = 28.dp, start = Edge, end = Edge)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "Choose your apps", style = WiptvType.screen)
                    Text(
                        text = "Press OK to show or hide an app. $shownCount of ${apps.size} showing. Back when you're done.",
                        modifier = Modifier.padding(top = 2.dp),
                        style = WiptvType.metadata,
                    )
                }
                WiptvButton(text = "Done", onClick = onDone, primary = true, compact = true)
            }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(sorted, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        onClick = { onToggle(app) },
                        shown = app.packageName !in hidden,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * The menu from holding OK on an app in "Your apps": hide it or move it.
 * Back closes it without changes.
 */
@Composable
fun AppMenu(
    app: TvApp,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onHide: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    // The menu opens while OK is still held down. Until that press is released, ignore OK,
    // otherwise letting go would "press" the first option (Hide) straight away.
    var armed by remember { mutableStateOf(false) }

    // A dialog is its own window, so the remote's focus stays inside it; Back closes it.
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    if (armed) return@onPreviewKeyEvent false
                    val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isOk) {
                        if (event.type == KeyEventType.KeyUp) armed = true
                        true // swallow the held press and its release
                    } else {
                        armed = true // moving around means the original press is over
                        false
                    }
                }
                .width(300.dp)
                .glass(WiptvShapes.panel, Glass.Sheet)
                .padding(vertical = 18.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = app.label,
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
                style = WiptvType.section,
            )
            MenuItem("Hide from Your apps", onHide, Modifier.focusRequester(first))
            if (canMoveLeft) MenuItem("Move left", onMoveLeft)
            if (canMoveRight) MenuItem("Move right", onMoveRight)
            MenuItem("Cancel", onClose)
        }
    }
}

/**
 * A choice in the menu, styled like the player's lists: no box at rest; focus is a soft fill
 * across the row and a short bright bar at its left edge, with no outline or scaling.
 */
@Composable
private fun MenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        targetValue = if (focused) WiptvColors.rowFocus else WiptvColors.rowFocus.copy(alpha = 0f),
        animationSpec = tween(120),
        label = "menuFill",
    )
    val marker by animateFloatAsState(if (focused) 1f else 0f, animationSpec = tween(120), label = "menuMarker")
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shape = ClickableSurfaceDefaults.shape(shape = WiptvShapes.small),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiptvColors.secondary,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = WiptvColors.text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = WiptvColors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        interactionSource = interaction,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(fill, cornerRadius = CornerRadius(8.dp.toPx()))
                    if (marker > 0f) {
                        val h = 24.dp.toPx()
                        drawRoundRect(
                            color = WiptvColors.rowFocusMarker.copy(alpha = marker),
                            topLeft = Offset(3.dp.toPx(), (size.height - h) / 2),
                            size = Size(3.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            style = WiptvType.control.copy(fontSize = 15.sp),
        )
    }
}

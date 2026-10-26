package dev.tvlauncher

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
            .background(Palette.background),
    ) {
        Column(modifier = Modifier.padding(top = 28.dp, start = 40.dp, end = 40.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Choose your apps",
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                    )
                    Text(
                        text = "Press OK to show or hide an app. $shownCount of ${apps.size} showing. Back when you're done.",
                        style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
                    )
                }
                PillButton(text = "Done", onClick = onDone)
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
                .background(Palette.tile, RoundedCornerShape(18.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = app.label,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.text),
            )
            Spacer(Modifier.height(4.dp))
            MenuItem("Hide from Your apps", onHide, Modifier.focusRequester(first))
            if (canMoveLeft) MenuItem("Move left", onMoveLeft)
            if (canMoveRight) MenuItem("Move right", onMoveRight)
            MenuItem("Cancel", onClose)
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.tileFocused,
            contentColor = Palette.text,
            focusedContainerColor = Palette.glow,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.glow,
            contentColor = Palette.background,
            focusedContainerColor = Color.White,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

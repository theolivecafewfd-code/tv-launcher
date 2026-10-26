package dev.tvlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Search for shows and films. Results update as you type; titles on your services come first
 * with their tags, the rest are dimmed. OK opens a result's details; holding OK plays it.
 * It's a dialog over the home screen; Back closes it.
 */
@Composable
fun SearchScreen(
    onOpen: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Title>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Open with the cursor in the search box and the keyboard up.
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { field.requestFocus() }.isSuccess) {
                keyboard?.show()
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    // Search shortly after you stop typing, rather than on every letter.
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            results = emptyList()
            searching = false
            failed = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        failed = false
        results = try {
            Catalog.search(text)
        } catch (e: Exception) {
            failed = true
            emptyList()
        }
        searching = false
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.background)
                .padding(top = 28.dp, start = 40.dp, end = 40.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SearchIcon(color = Palette.text, size = 22.dp)
                Box(
                    modifier = Modifier
                        .width(520.dp)
                        .background(Palette.tile, RoundedCornerShape(12.dp))
                        .border(
                            width = if (fieldFocused) 2.dp else 1.dp,
                            color = if (fieldFocused) Palette.glow else Palette.quiet.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search shows and films",
                            style = TextStyle(fontSize = 16.sp, color = Palette.quiet),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = Palette.text, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Palette.glow),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            runCatching { firstResult.requestFocus() }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .onFocusChanged { fieldFocused = it.isFocused },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    query.trim().length < 2 -> "Type at least two letters. Titles on Netflix or Prime come first."
                    searching -> "Searching…"
                    failed -> "Couldn't reach TMDB. Check the internet connection."
                    results.isEmpty() -> "Nothing found for \"${query.trim()}\""
                    else -> {
                        val available = results.count { it.sources.isNotEmpty() }
                        "$available on your services · ${results.size - available} elsewhere (dimmed)"
                    }
                },
                style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(results, key = { it.key }) { title ->
                    val isFirst = title.key == results.firstOrNull()?.key
                    Column(
                        modifier = Modifier.alpha(if (title.sources.isEmpty()) 0.45f else 1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        PosterCard(
                            title = title,
                            onClick = { onOpen(title) },
                            onLongClick = { onPlay(title) },
                            modifier = if (isFirst) Modifier.focusRequester(firstResult) else Modifier,
                        )
                        Text(
                            text = title.name,
                            maxLines = 1,
                            style = TextStyle(fontSize = 10.sp, color = Palette.quiet),
                            modifier = Modifier.width(84.dp),
                        )
                    }
                }
            }
        }
    }
}

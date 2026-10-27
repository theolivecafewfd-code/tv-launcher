package dev.tvlauncher

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalContext
import dev.wiptv.design.wiptvBackground
import dev.wiptv.design.SearchIcon
import dev.wiptv.design.RowScrolling
import androidx.compose.ui.graphics.Color
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
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
    // Your IPTV provider's films and series, from WIPTV Player's list on the TV.
    var iptvResults by remember { mutableStateOf<List<IptvTitle>>(emptyList()) }
    var iptvStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
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
            iptvResults = emptyList()
            searching = false
            failed = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        failed = false
        iptvResults = Iptv.search(context, text)
        iptvStatus = Iptv.status(context)
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
                .wiptvBackground()
                .padding(top = 28.dp, start = Edge, end = Edge),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SearchIcon(color = WiptvColors.text, size = 22.dp)
                // A glass field; focus adds the light outline every focused control has.
                Box(
                    modifier = Modifier
                        .width(520.dp)
                        .glass(WiptvShapes.card)
                        .border(
                            width = 2.dp,
                            color = if (fieldFocused) WiptvColors.focus else Color.Transparent,
                            shape = WiptvShapes.card,
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search shows and films",
                            style = WiptvType.body.copy(color = WiptvColors.quiet),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = WiptvType.body.copy(fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(WiptvColors.focus),
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
                    results.isEmpty() && iptvResults.isEmpty() -> "Nothing found for \"${query.trim()}\""
                    results.isEmpty() -> "${iptvResults.size} on your IPTV"
                    else -> {
                        val available = results.count { it.sources.isNotEmpty() }
                        val onIptv = if (iptvResults.isEmpty()) "" else "${iptvResults.size} on your IPTV · "
                        "$onIptv$available on your services · ${results.size - available} elsewhere (dimmed)"
                    }
                },
                style = WiptvType.hint,
            )
            // How WIPTV Player's list stands, so it's clear why IPTV results do or don't appear.
            if (query.trim().length >= 2 && !searching) {
                iptvStatus?.let { Text(text = it, modifier = Modifier.padding(top = 2.dp), style = WiptvType.hint) }
            }

            // Results as the player's posters, with their names underneath.
            // The results clip at their edges, so they get room for a focused poster's outline and
            // lift: 8 dp each side, pulled back out so the posters still line up with the box above.
            LazyVerticalGrid(
                modifier = Modifier.layout { measurable, constraints ->
                    val room = ResultRoom.roundToPx()
                    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + room * 2, minWidth = 0))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-room, 0) }
                },
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(start = ResultRoom, end = ResultRoom, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Your IPTV provider's matches first, in their own row; OK opens them in the player.
                if (iptvResults.isNotEmpty()) {
                    item(key = "iptv", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(text = "On your IPTV", modifier = Modifier.padding(bottom = 2.dp), style = RowHeading)
                            RowScrolling(start = ResultRoom, end = 72.dp) {
                                LazyRow(
                                    // The same room at the row's own edges, pulled back out the same way.
                                    modifier = Modifier.layout { measurable, constraints ->
                                        val room = ResultRoom.roundToPx()
                                        val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + room * 2, minWidth = 0))
                                        layout(constraints.maxWidth, placeable.height) { placeable.place(-room, 0) }
                                    },
                                    contentPadding = PaddingValues(start = ResultRoom, end = ResultRoom, top = 6.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(iptvResults, key = { "${it.type}:${it.id}" }) { found ->
                                        IptvPosterCard(
                                            title = found,
                                            onClick = { Iptv.open(context, found) },
                                            modifier = if (found == iptvResults.first()) Modifier.focusRequester(firstResult) else Modifier,
                                        )
                                    }
                                }
                            }
                            if (results.isNotEmpty()) {
                                Text(text = "Netflix, Prime and more", modifier = Modifier.padding(top = 10.dp), style = RowHeading)
                            }
                        }
                    }
                }
                items(results, key = { it.key }) { title ->
                    val isFirst = iptvResults.isEmpty() && title.key == results.firstOrNull()?.key
                    Box(modifier = Modifier.alpha(if (title.sources.isEmpty()) 0.45f else 1f)) {
                        TitlePoster(
                            title = title,
                            onClick = { onOpen(title) },
                            onLongClick = { onPlay(title) },
                            modifier = if (isFirst) Modifier.focusRequester(firstResult) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** Room around search results for a focused poster's outline and lift. */
private val ResultRoom = 8.dp

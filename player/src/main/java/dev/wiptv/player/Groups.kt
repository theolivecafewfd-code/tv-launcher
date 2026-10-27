package dev.wiptv.player

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Which Live TV groups you've hidden and the order you've put them in, kept on the TV.
 * Groups are matched by the provider's group id.
 */
class GroupPrefs(context: Context, name: String = "groups") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()

    fun setHidden(ids: Set<String>) {
        prefs.edit().putStringSet("hidden", ids.toSet()).apply()
    }

    fun order(): List<String> = prefs.getString("order", "").orEmpty().split(',').filter { it.isNotBlank() }

    fun setOrder(ids: List<String>) {
        prefs.edit().putString("order", ids.joinToString(",")).apply()
    }

    companion object {
        /** Every group in your order; ones you haven't placed (like new ones) follow in the provider's order. */
        fun arrange(all: List<Group>, order: List<String>): List<Group> {
            val providerRank = all.withIndex().associate { (index, group) -> group.id to index }
            return all.sortedBy { group ->
                val saved = order.indexOf(group.id)
                if (saved >= 0) saved else order.size + (providerRank[group.id] ?: 0)
            }
        }
    }
}

/**
 * Full-screen list of every group. OK shows or hides a group; the arrows beside it move it up,
 * down or to the top. "Hide all" then showing the few you want is quickest with lots of groups.
 */
@Composable
fun GroupEditor(
    groups: List<Group>,
    hidden: Set<String>,
    onToggle: (Group) -> Unit,
    onSetAllHidden: (Boolean) -> Unit,
    onMove: (Group, Int) -> Unit,
    onMoveToTop: (Group) -> Unit,
    onDone: () -> Unit,
    title: String = "Edit groups",
) {
    val first = remember { FocusRequester() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.background)
                .padding(top = 24.dp, start = 48.dp, end = 48.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                    )
                    Text(
                        text = "OK shows or hides a group. Move right for ▲ ▼ and Top to reorder. " +
                            "${groups.count { it.id !in hidden }} of ${groups.size} showing. Back when you're done.",
                        style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                    )
                }
                PlayerButton(text = "Show all", onClick = { onSetAllHidden(false) })
                PlayerButton(text = "Hide all", onClick = { onSetAllHidden(true) })
                PlayerButton(text = "Done", onClick = onDone, primary = true)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(bottom = 24.dp, start = 4.dp, end = 4.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(groups, key = { _, g -> g.id }) { index, group ->
                    val shown = group.id !in hidden
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorButton(
                            onClick = { onToggle(group) },
                            modifier = Modifier
                                .weight(1f)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = group.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).alpha(if (shown) 1f else 0.45f),
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = if (shown) "Shown" else "Hidden",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = if (shown) Palette.good else Palette.quiet,
                                    ),
                                )
                            }
                        }
                        EditorButton(onClick = {
                            onMove(group, -1)
                            if (index - 1 < list.firstVisibleItemIndex) scope.launch { list.scrollToItem((index - 1).coerceAtLeast(0)) }
                        }) { ArrowText("▲") }
                        EditorButton(onClick = { onMove(group, 1) }) { ArrowText("▼") }
                        EditorButton(onClick = {
                            onMoveToTop(group)
                            scope.launch { list.scrollToItem(0) }
                        }) { ArrowText("Top") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrowText(text: String) {
    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun EditorButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.tile,
            contentColor = Palette.text,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) { content() }
    }
}

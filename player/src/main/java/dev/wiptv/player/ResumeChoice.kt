package dev.wiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.wiptv.design.ActionButton
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import kotlinx.coroutines.delay

/**
 * What OK on a Continue watching card opens, Netflix style: the title, where you're up to and how
 * long is left, then a choice. An episode offers Continue (where you stopped) or Episodes (the
 * series' page on that episode, to pick another, say after falling asleep). A film offers Resume
 * or Start over. Back closes it.
 */
@Composable
fun ResumeChoice(
    point: ResumePoint,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    onEpisodes: () -> Unit,
    onClose: () -> Unit,
) {
    val isEpisode = point.key.startsWith("episode:")
    val episode = point.subtitle?.substringBefore(" · ")?.ifBlank { null }
    val episodeName = point.subtitle?.substringAfter(" · ", "")?.ifBlank { null }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(40) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(WiptvColors.background)) {
            CinematicBackdrop(
                backdrop = point.image,
                poster = point.poster,
                modifier = Modifier.fillMaxSize(),
                delayMillis = 0,
                leftFade = 0.98f,
                textScrim = 0.58f,
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, end = 64.dp, bottom = 72.dp).widthIn(max = 640.dp),
            ) {
                Text(
                    text = point.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.hero.copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
                )
                if (isEpisode && (episode != null || episodeName != null)) {
                    Text(
                        text = listOfNotNull(episode, episodeName).joinToString("  ·  "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                        style = WiptvType.metadata.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text),
                    )
                }
                if (point.progress > 0f) {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProgressLine(progress = point.progress, modifier = Modifier.width(220.dp))
                        timeLeft(point)?.let { Text(text = it, style = WiptvType.metadata) }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        text = when {
                            !isEpisode -> "▶  Resume"
                            episode != null -> "▶  Continue $episode"
                            else -> "▶  Continue"
                        },
                        primary = true,
                        onClick = onContinue,
                        modifier = Modifier.focusRequester(first),
                    )
                    if (isEpisode) {
                        ActionButton(text = "Episodes", onClick = onEpisodes)
                    } else {
                        ActionButton(text = "Start over", onClick = onStartOver)
                    }
                }
            }
        }
    }
}

package dev.wiptv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Full-screen playback, using the same player as the preview. Up and down change channel within
 * the group (up goes to the channel above in the list), OK shows the channel info, Back returns
 * to the guide with the channel still playing in the preview.
 */
@Composable
fun WatchScreen(
    login: Login,
    format: String,
    live: LivePlayer,
    channels: List<Channel>,
    groupName: String,
    startIndex: Int,
    nowShowing: (Channel) -> Programme?,
    onChannel: (Channel) -> Unit,
    onClose: (Channel) -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, channels.lastIndex)) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val channel = channels[index]

    // Play the current channel whenever it changes (nothing restarts if it's already playing).
    LaunchedEffect(index) {
        live.play(login, format, channel)
        onChannel(channel)
        infoTick++
    }

    // The info bar shows for a few seconds after a change or an OK press.
    LaunchedEffect(infoTick) {
        showInfo = true
        delay(4000)
        showInfo = false
    }

    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    fun move(step: Int) {
        if (channels.size > 1) index = (index + step + channels.size) % channels.size
    }

    Dialog(
        onDismissRequest = { onClose(channel) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { move(-1); true }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { move(1); true }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_INFO -> { infoTick++; true }
                        else -> false
                    }
                },
        ) {
            PlayerSurface(live, attached = true, modifier = Modifier.fillMaxSize())

            // Loading or a problem, in the middle of the screen.
            val message = live.error ?: if (live.buffering) "Loading ${channel.name}…" else null
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (live.error != null) Palette.warn else Palette.text,
                    ),
                )
            }

            AnimatedVisibility(
                visible = showInfo || live.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                InfoBar(channel, nowShowing(channel), groupName, index, channels.size)
            }
        }
    }
}

/** Channel number, logo, name, what's on, and the group along the bottom of the picture. */
@Composable
private fun InfoBar(channel: Channel, programme: Programme?, groupName: String, index: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = channel.number.toString(),
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.text),
            )
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 72.dp, height = 44.dp),
                )
            }
            Column(modifier = Modifier.width(620.dp)) {
                Text(
                    text = channel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                )
                if (programme != null) {
                    Text(
                        text = "${timeRange(programme)}  ${programme.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, color = Palette.text),
                    )
                }
                Text(
                    text = "$groupName · ${index + 1} of $total · ▲▼ change channel · Back for the guide",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                )
            }
        }
    }
}

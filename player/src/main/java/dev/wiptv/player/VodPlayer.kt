package dev.wiptv.player

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import dev.wiptv.design.Glass
import dev.wiptv.design.KeyHints
import dev.wiptv.design.PlaybackPanel
import dev.wiptv.design.PlaybackProgress
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Full-screen playback of a film or an episode, starting from [start]'s position.
 * - OK or Play/Pause pauses and resumes.
 * - Left goes back 10 seconds, Right forward 30 (hold to keep skipping).
 * - Up or Down shows where you are. Back stops, remembering the position for Continue watching.
 * When it finishes, [onFinished] is told (for the next episode).
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    start: ResumePoint,
    store: VodStore,
    onFinished: () -> Unit,
    onClose: () -> Unit,
    /** False for catch-up: nothing goes into Continue watching. */
    rememberPosition: Boolean = true,
    /**
     * The next episode, for the Play Next card in the last minute; OK on it (or the end of its
     * countdown) goes on to it through [onFinished], as the end of the episode does.
     */
    upNext: ResumePoint? = null,
) {
    val context = LocalContext.current
    val finished by rememberUpdatedState(onFinished)
    val player = remember { newExoPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(start.position) }
    var duration by remember { mutableLongStateOf(start.duration) }
    var overlayTick by remember { mutableIntStateOf(0) }
    // Play Next: shown for the last minute of an episode that has a next one, unless Back hid it.
    var nextHidden by remember { mutableStateOf(false) }
    val showNext = upNext != null && !nextHidden && error == null && duration > 0 &&
        duration - position in 1..NEXT_CARD_MS
    var showOverlay by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }

    fun save() {
        if (!rememberPosition) return
        val pos = player.currentPosition.takeIf { it > 0 } ?: return
        val length = player.duration.takeIf { it > 0 } ?: duration
        store.savePosition(start.copy(position = pos, duration = length, updated = System.currentTimeMillis()))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    if (player.duration > 0) duration = player.duration
                }
                if (state == Player.STATE_ENDED) {
                    save()
                    finished()
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                buffering = false
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "The provider refused this. It may be unavailable, or all your screens are in use."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Couldn't reach the provider. Check the internet connection."
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        "This TV can't play this file's format."
                    else -> "This isn't playing (${e.errorCodeName})."
                }
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(start.url), start.position)
        player.prepare()
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause when the app goes to the background, remembering the position.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                save()
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    KeepScreenOn(playing)

    // Keep the progress up to date, and save it every 15 seconds.
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            delay(1000)
            position = player.currentPosition
            if (++ticks % 15 == 0 && player.isPlaying) save()
        }
    }

    // The bar shows for a few seconds after any key, and stays while paused.
    LaunchedEffect(overlayTick) {
        showOverlay = true
        delay(4000)
        showOverlay = false
    }

    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    // Counts down while playing (not while paused), then goes on to the next episode.
    var countdown by remember { mutableIntStateOf(NEXT_COUNTDOWN_S) }
    LaunchedEffect(showNext) {
        if (!showNext) return@LaunchedEffect
        countdown = NEXT_COUNTDOWN_S
        while (countdown > 0) {
            delay(1000)
            if (player.isPlaying) countdown--
        }
        save()
        finished()
    }

    fun seekBy(ms: Long) {
        val length = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0, length))
        position = player.currentPosition
        overlayTick++
    }

    // Back hides the Play Next card first (the episode plays on to its end); then it closes.
    Dialog(
        onDismissRequest = { if (showNext) nextHidden = true else onClose() },
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
                        // OK with the Play Next card up: the next episode, now.
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                if (showNext) {
                                    save()
                                    finished()
                                } else {
                                    if (player.isPlaying) player.pause() else player.play()
                                    overlayTick++
                                }
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                if (player.isPlaying) player.pause() else player.play()
                                overlayTick++
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); overlayTick++; true }
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); overlayTick++; true }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000); true }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); true }
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_INFO -> { overlayTick++; true }
                        else -> false
                    }
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        this.player = player
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )

            val message = error ?: if (buffering) "Loading…" else null
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .glass(WiptvShapes.pill, Glass.OverVideo)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (error != null) Palette.warn else Palette.text,
                    ),
                )
            }

            AnimatedVisibility(
                visible = showOverlay || !playing || error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ProgressBar(
                    title = start.title,
                    subtitle = start.subtitle,
                    position = position,
                    duration = duration,
                    paused = !playing && !buffering,
                )
            }

            val next = upNext
            AnimatedVisibility(
                visible = showNext && next != null,
                enter = fadeIn(),
                exit = fadeOut(),
                // Above the progress panel when that's showing.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = if (showOverlay || !playing) 150.dp else 24.dp),
            ) {
                if (next != null) PlayNextCard(next = next, countdown = countdown)
            }
        }
    }
}

/** How long before the end the Play Next card shows, and its countdown. */
private const val NEXT_CARD_MS = 60_000L
private const val NEXT_COUNTDOWN_S = 15

/**
 * Play Next, in the last minute of an episode: the next one's picture, "Play Next" lit as the
 * thing OK does, which episode it is, and the countdown. Back hides it.
 */
@Composable
private fun PlayNextCard(next: ResumePoint, countdown: Int) {
    Row(
        modifier = Modifier
            .width(400.dp)
            .glass(RoundedCornerShape(18.dp), Glass.OverVideo)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 128.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(WiptvColors.elevated),
        ) {
            val picture = next.image ?: next.poster
            if (picture != null) {
                AsyncImage(
                    model = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "▶  Play Next",
                modifier = Modifier
                    .background(WiptvColors.primaryFill, WiptvShapes.pill)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                style = WiptvType.control.copy(color = WiptvColors.onPrimary, fontWeight = FontWeight.SemiBold),
            )
            next.subtitle?.let {
                Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = WiptvType.metadata.copy(color = WiptvColors.text))
            }
            Text(text = "In ${countdown}s · Back to hide", style = WiptvType.hint)
        }
    }
}

/**
 * Along the bottom of the picture, in the shared playback panel: the title (with ❚❚ when paused)
 * and what time it'll finish, the episode, the progress bar, then where you are, what the remote
 * does and the full length.
 */
@Composable
private fun ProgressBar(title: String, subtitle: String?, position: Long, duration: Long, paused: Boolean) {
    PlaybackPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (paused) "❚❚  $title" else title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = WiptvType.section.copy(fontWeight = FontWeight.SemiBold),
            )
            if (duration > 0) {
                Text(
                    text = "Ends " + timeOf(System.currentTimeMillis() + (duration - position).coerceAtLeast(0)),
                    style = WiptvType.control.copy(color = WiptvColors.secondary),
                )
            }
        }
        if (subtitle != null) {
            Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = WiptvType.metadata)
        }
        PlaybackProgress(
            fraction = if (duration > 0) position.toFloat() / duration else 0f,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = clockOf(position), style = WiptvType.hint.copy(color = WiptvColors.text))
            KeyHints(hints = listOf("◀ 10s", "OK pause", "30s ▶"), modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
            if (duration > 0) Text(text = clockOf(duration), style = WiptvType.hint.copy(color = WiptvColors.text))
        }
    }
}

/** 1:05:09 or 5:09. */
internal fun clockOf(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

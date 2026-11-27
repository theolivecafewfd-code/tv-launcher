package dev.wiptv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wiptv.design.Glass
import dev.wiptv.design.KeyHints
import dev.wiptv.design.ListRow
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import kotlinx.coroutines.delay

/** The round buttons along the panel, after Play/Pause. */
private enum class PlayerControl(val label: String) { PLAY("Play"), AUDIO("Audio"), SUBTITLES("Subtitles"), SKIP("Skip intro"), SLEEP("Sleep timer") }

/** Which part of Audio & subtitles is open. */
private enum class TracksShown { ALL, AUDIO, SUBTITLES }

/**
 * Full-screen playback of a film or an episode, starting from [start]'s position.
 * - OK or Play/Pause pauses and resumes.
 * - Left goes back 10 seconds, Right forward 30 (hold to keep skipping).
 * - Up goes to the round buttons (Play/Pause, Audio, Subtitles, Skip intro, Sleep timer):
 *   Left and Right move along them, OK uses one, Down goes back to the progress bar.
 * - Down (on the progress bar) opens Audio & subtitles, as before.
 * - Back stops, remembering the position for Continue watching.
 * When it finishes, [onFinished] is told (for the next episode), unless the sleep timer is set
 * for the end of this one.
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
    val close by rememberUpdatedState(onClose)
    val nextEpisode by rememberUpdatedState(upNext)
    val player = remember { newExoPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(start.position) }
    var duration by remember { mutableLongStateOf(start.duration) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var showOverlay by remember { mutableStateOf(true) }
    // On the round buttons (Up) rather than the progress bar, and which one.
    var onButtons by remember { mutableStateOf(false) }
    var button by remember { mutableStateOf(PlayerControl.PLAY) }
    // A short note in the panel ("No subtitles in this one"), for a few seconds.
    var note by remember { mutableStateOf<String?>(null) }
    // Play Next: shown for the last minute of an episode that has a next one, unless Back hid it,
    // or the sleep timer is set to stop at the end of this one.
    var nextHidden by remember { mutableStateOf(false) }
    val showNext = upNext != null && !nextHidden && !SleepTimer.atEnd && error == null && duration > 0 &&
        duration - position in 1..NEXT_CARD_MS
    val focus = remember { FocusRequester() }
    // Audio & subtitles: the file's languages and subtitles, kept up to date as it loads.
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var tracksShown by remember { mutableStateOf<TracksShown?>(null) }
    var sleepOpen by remember { mutableStateOf(false) }
    val panelOpen = tracksShown != null || sleepOpen
    // Skip intro, for episodes: once you've skipped in a series, it shows around the same point
    // in its other episodes; until then, in the first five minutes. Skipping or Back hides it.
    // Only when you start before that point: resuming part-way through never offers it, and an
    // intro point remembered later than [INTRO_LATEST_MS] (a skip saved by mistake) is ignored.
    val seriesId = start.seriesId
    val introAt = remember { seriesId?.let { store.introAt(it) }?.takeIf { it <= INTRO_LATEST_MS } }
    var introDone by remember { mutableStateOf(false) }
    val introWindow = if (introAt != null) (introAt - 30_000).coerceAtLeast(0)..(introAt + 60_000) else 10_000L..INTRO_WINDOW_MS
    val startedBeforeIntro = start.position < introWindow.first + 10_000
    val showSkip = seriesId != null && startedBeforeIntro && !introDone && error == null && duration > 0 &&
        position in introWindow && !showNext && !panelOpen
    val controls = remember(seriesId) {
        PlayerControl.entries.filter { it != PlayerControl.SKIP || seriesId != null }
    }
    // Going on to the next episode keeps the sleep timer; leaving the player turns it off.
    var advancing by remember { mutableStateOf(false) }

    fun save() {
        if (!rememberPosition) return
        val pos = player.currentPosition.takeIf { it > 0 } ?: return
        val length = player.duration.takeIf { it > 0 } ?: duration
        store.savePosition(start.copy(position = pos, duration = length, updated = System.currentTimeMillis()))
    }

    /** The end of this one: on to the next episode, or stop here if the sleep timer says so. */
    fun ended() {
        save()
        if (SleepTimer.atEnd) {
            SleepTimer.off()
            close()
        } else {
            // On to the next episode keeps the sleep timer; anything else is leaving.
            if (nextEpisode != null) advancing = true
            finished()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoHeight = size.height
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    if (player.duration > 0) duration = player.duration
                }
                if (state == Player.STATE_ENDED) ended()
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
        // A timer left over from earlier that has already run out doesn't stop this.
        if (SleepTimer.expired()) SleepTimer.off()
        onDispose {
            save()
            if (!advancing) SleepTimer.off()
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

    // Keep the progress and clock up to date, save every 15 seconds, and stop when the sleep
    // timer runs out (the TV can then go to sleep as it normally would).
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            delay(1000)
            position = player.currentPosition
            now = System.currentTimeMillis()
            if (++ticks % 15 == 0 && player.isPlaying) save()
            if (SleepTimer.expired()) {
                SleepTimer.off()
                save()
                player.pause()
                close()
                return@LaunchedEffect
            }
        }
    }

    // The panel shows for a few seconds after any key, and stays while paused.
    LaunchedEffect(overlayTick) {
        showOverlay = true
        delay(5000)
        showOverlay = false
        // Paused, the panel stays up, and so does the highlighted button.
        if (player.isPlaying) onButtons = false
    }

    LaunchedEffect(note) {
        if (note == null) return@LaunchedEffect
        delay(3000)
        note = null
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
        ended()
    }

    fun seekBy(ms: Long) {
        val length = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0, length))
        position = player.currentPosition
        overlayTick++
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        overlayTick++
    }

    fun closePanels() {
        tracksShown = null
        sleepOpen = false
        overlayTick++
        runCatching { focus.requestFocus() }
    }

    fun skipIntro() {
        val at = player.currentPosition
        if (at <= INTRO_LATEST_MS) seriesId?.let { store.setIntroAt(it, at) }
        introDone = true
        seekBy(INTRO_SKIP_MS)
    }

    fun use(control: PlayerControl) {
        overlayTick++
        when (control) {
            PlayerControl.PLAY -> togglePlay()
            PlayerControl.AUDIO ->
                if (audioChoices(tracks).size > 1) tracksShown = TracksShown.AUDIO else note = "Only one language in this one"
            PlayerControl.SUBTITLES ->
                if (subtitleChoices(tracks).isNotEmpty()) tracksShown = TracksShown.SUBTITLES else note = "No subtitles in this one"
            PlayerControl.SKIP -> skipIntro()
            PlayerControl.SLEEP -> sleepOpen = true
        }
    }

    // Back closes a panel, then hides Skip intro or the Play Next card (the episode plays on);
    // then it closes the player.
    Dialog(
        onDismissRequest = {
            when {
                panelOpen -> closePanels()
                showSkip -> introDone = true
                showNext -> nextHidden = true
                else -> close()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    // A panel has the remote while it's open.
                    if (panelOpen) return@onPreviewKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val code = event.nativeKeyEvent.keyCode
                    if (code == AndroidKeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                    // In the sleep timer's last minute, any key means "still watching".
                    if (SleepTimer.endingSoon(now)) {
                        SleepTimer.off()
                        note = "Sleep timer off"
                        overlayTick++
                        return@onPreviewKeyEvent true
                    }
                    val visible = showOverlay || !playing
                    val firstPress = event.nativeKeyEvent.repeatCount == 0
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (firstPress) {
                                when {
                                    // On the round buttons: that button.
                                    visible && onButtons -> use(button)
                                    // Skip intro or Play Next showing: that.
                                    showSkip -> skipIntro()
                                    showNext -> ended()
                                    else -> togglePlay()
                                }
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (firstPress) togglePlay()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); overlayTick++; true }
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); overlayTick++; true }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val step = if (code == AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                            if (visible && onButtons) {
                                val at = controls.indexOf(button).coerceAtLeast(0)
                                button = controls[(at + step).coerceIn(0, controls.size - 1)]
                                overlayTick++
                            } else {
                                seekBy(if (step < 0) -10_000 else 30_000)
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000); true }
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); true }
                        // Up: to the round buttons.
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            onButtons = true
                            overlayTick++
                            true
                        }
                        // Down: from the buttons back to the progress bar; from there, Audio &
                        // subtitles, when the file has a choice.
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            when {
                                visible && onButtons -> onButtons = false
                                hasTrackChoices(tracks) -> tracksShown = TracksShown.ALL
                                else -> Unit
                            }
                            overlayTick++
                            true
                        }
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

            val overlayVisible = showOverlay || !playing || error != null
            AnimatedVisibility(
                visible = overlayVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerPanel(
                    start = start,
                    position = position,
                    duration = duration,
                    now = now,
                    quality = qualityOf(videoHeight),
                    playing = playing,
                    buffering = buffering,
                    controls = controls,
                    highlighted = if (onButtons) button else null,
                    note = note,
                )
            }

            // The sleep timer's last minute: a reminder at the top.
            if (SleepTimer.endingSoon(now)) {
                val left = ((SleepTimer.stopAt ?: now) - now).coerceAtLeast(0) / 1000
                Text(
                    text = "Sleep timer: stopping in ${left}s. Press any key to keep watching.",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .glass(WiptvShapes.pill, Glass.OverVideo)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WiptvColors.text),
                )
            }

            // Skip intro, bottom right, above the panel when that's showing.
            AnimatedVisibility(
                visible = showSkip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 42.dp, bottom = if (overlayVisible) PanelClearance else 24.dp),
            ) {
                Text(
                    text = "⏭  Skip intro",
                    modifier = Modifier
                        .background(WiptvColors.primaryFill, WiptvShapes.pill)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    style = WiptvType.control.copy(color = WiptvColors.onPrimary, fontWeight = FontWeight.SemiBold),
                )
            }

            // Audio & subtitles, on the right.
            tracksShown?.let { shown ->
                TracksPanel(
                    audio = if (shown == TracksShown.SUBTITLES) emptyList() else audioChoices(tracks),
                    subtitles = if (shown == TracksShown.AUDIO) emptyList() else subtitleChoices(tracks),
                    onAudio = {
                        choose(player, it, C.TRACK_TYPE_AUDIO)
                        closePanels()
                    },
                    onSubtitles = {
                        choose(player, it, C.TRACK_TYPE_TEXT)
                        closePanels()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                )
            }

            if (sleepOpen) {
                SleepPanel(
                    isEpisode = seriesId != null,
                    onChoose = { minutes ->
                        when (minutes) {
                            null -> SleepTimer.off()
                            SLEEP_AT_END -> SleepTimer.setAtEnd()
                            else -> SleepTimer.setIn(minutes)
                        }
                        note = SleepTimer.describe() ?: "Sleep timer off"
                        closePanels()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                )
            }

            val next = upNext
            AnimatedVisibility(
                visible = showNext && next != null,
                enter = fadeIn(),
                exit = fadeOut(),
                // Above the panel when that's showing.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 42.dp, bottom = if (overlayVisible) PanelClearance else 24.dp),
            ) {
                if (next != null) PlayNextCard(next = next, countdown = countdown)
            }
        }
    }
}

/** Skip intro: how far it jumps, and (until you've skipped in a series) how long it's offered. */
private const val INTRO_SKIP_MS = 85_000L
private const val INTRO_WINDOW_MS = 5 * 60_000L

/** The latest an intro is taken to be: a remembered skip later than this is ignored. */
private const val INTRO_LATEST_MS = 10 * 60_000L

/** How long before the end the Play Next card shows, and its countdown. */
private const val NEXT_CARD_MS = 60_000L
private const val NEXT_COUNTDOWN_S = 15

/** How far up Skip intro and Play Next sit while the panel shows, to stay clear of it. */
private val PanelClearance = 236.dp

/** The sleep timer's "end of this film or episode" choice. */
private const val SLEEP_AT_END = -1

/**
 * The sleep timer, for the whole app: it carries on into the next episode, and stops what's
 * playing when it runs out. Leaving the player turns it off.
 */
object SleepTimer {
    /** When it stops, or null. */
    var stopAt by mutableStateOf<Long?>(null)
        private set

    /** Stop at the end of the film or episode playing. */
    var atEnd by mutableStateOf(false)
        private set

    fun setIn(minutes: Int) {
        stopAt = System.currentTimeMillis() + minutes * 60_000L
        atEnd = false
    }

    fun setAtEnd() {
        stopAt = null
        atEnd = true
    }

    fun off() {
        stopAt = null
        atEnd = false
    }

    fun expired(): Boolean = stopAt?.let { System.currentTimeMillis() >= it } == true

    /** In its last minute, when it shows a reminder. */
    fun endingSoon(now: Long): Boolean = stopAt?.let { it - now in 0..60_000 } == true

    /** "Sleep timer: stops at 23:15", or null when it's off. */
    fun describe(): String? = when {
        atEnd -> "Sleep timer: stops at the end"
        stopAt != null -> "Sleep timer: stops at ${timeOf(stopAt!!)}"
        else -> null
    }
}

/** 4K, 1080p, 720p or SD, from the picture's height. */
private fun qualityOf(height: Int): String? = when {
    height >= 1800 -> "4K"
    height >= 1000 -> "1080p"
    height >= 700 -> "720p"
    height > 0 -> "SD"
    else -> null
}

/**
 * The panel over the picture: along the top, a back arrow, WIPTV and the time; along the bottom,
 * on a dark band, the quality and what this is, the title, its details, Play/Pause with when it
 * ends, the round buttons, and the progress bar with time gone and time left. The highlighted
 * button is white; the others are grey.
 */
@Composable
private fun PlayerPanel(
    start: ResumePoint,
    position: Long,
    duration: Long,
    now: Long,
    quality: String?,
    playing: Boolean,
    buffering: Boolean,
    controls: List<PlayerControl>,
    highlighted: PlayerControl?,
    note: String?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A soft dark band behind the text at the bottom, and a faint one at the top.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, WiptvColors.background.copy(alpha = 0.82f), WiptvColors.background.copy(alpha = 0.9f)))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(90.dp)
                .background(Brush.verticalGradient(listOf(WiptvColors.background.copy(alpha = 0.6f), Color.Transparent))),
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 42.dp, end = 42.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(33.dp).background(WiptvColors.elevated.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Glyph(PlayerGlyph.BACK, WiptvColors.text, Modifier.size(15.dp)) }
            Text(
                text = "WIPTV",
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WiptvColors.text),
            )
            Text(text = timeOf(now), style = TextStyle(fontSize = 13.sp, color = WiptvColors.secondary))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 42.dp, end = 42.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quality != null) {
                    Text(
                        text = quality,
                        modifier = Modifier
                            .border(1.dp, WiptvColors.quiet, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        style = TextStyle(fontSize = 10.sp, color = WiptvColors.secondary),
                    )
                }
                Text(
                    text = when {
                        start.key.startsWith("movie:") -> "MOVIES"
                        start.key.startsWith("episode:") -> "TV SHOWS"
                        else -> "CATCH-UP"
                    },
                    style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp, color = WiptvColors.secondary),
                )
            }
            Text(
                text = start.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = WiptvColors.text),
            )
            val details = listOfNotNull(
                start.subtitle?.takeIf { it.isNotBlank() },
                duration.takeIf { it > 0 }?.let { runtimeOf((it / 60_000).toInt().coerceAtLeast(1)) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(text = details, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = WiptvColors.secondary))
            }

            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val playLit = highlighted == PlayerControl.PLAY
                RoundButton(
                    glyph = if (playing) PlayerGlyph.PAUSE else PlayerGlyph.PLAY,
                    lit = playLit,
                    size = 42,
                )
                val status = when {
                    note != null -> note
                    highlighted != null && highlighted != PlayerControl.PLAY ->
                        if (highlighted == PlayerControl.SLEEP) SleepTimer.describe() ?: "Sleep timer: off" else highlighted.label
                    buffering -> "Loading…"
                    else -> listOfNotNull(
                        if (playing) "Playing" else "Paused",
                        duration.takeIf { it > 0 }?.let { "ends at " + timeOf(now + (it - position).coerceAtLeast(0)) },
                        SleepTimer.describe()?.removePrefix("Sleep timer: ")?.let { "sleep timer $it" },
                    ).joinToString(" · ")
                }
                Text(
                    text = status,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                    style = TextStyle(fontSize = 12.sp, color = WiptvColors.secondary),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    controls.filter { it != PlayerControl.PLAY }.forEach { control ->
                        RoundButton(
                            glyph = when (control) {
                                PlayerControl.AUDIO -> PlayerGlyph.AUDIO
                                PlayerControl.SUBTITLES -> PlayerGlyph.SUBTITLES
                                PlayerControl.SKIP -> PlayerGlyph.SKIP
                                else -> PlayerGlyph.SLEEP
                            },
                            lit = highlighted == control,
                            size = 36,
                            // A dot on the moon while the sleep timer is on.
                            marked = control == PlayerControl.SLEEP && (SleepTimer.stopAt != null || SleepTimer.atEnd),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = clockOf(position), style = TextStyle(fontSize = 11.sp, color = WiptvColors.secondary))
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(12.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(WiptvColors.elevated, RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(WiptvColors.progress, RoundedCornerShape(2.dp)))
                    // The knob, bigger while Left and Right move along the bar.
                    Knob(fraction = fraction, big = highlighted == null)
                }
                Text(
                    text = if (duration > 0) "−" + clockOf((duration - position).coerceAtLeast(0)) else "",
                    style = TextStyle(fontSize = 11.sp, color = WiptvColors.secondary),
                )
            }
            KeyHints(
                hints = if (highlighted == null) {
                    listOf("◀ 10s", "OK pause", "30s ▶", "▲ buttons", "▼ audio & subtitles")
                } else {
                    listOf("◀ ▶ choose", "OK select", "▼ back to the bar")
                },
            )
        }
    }
}

/** The round marker on the progress bar, at [fraction] of the way along. */
@Composable
private fun Knob(fraction: Float, big: Boolean) {
    val size = if (big) 12.dp else 9.dp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .offset(x = (maxWidth * fraction - size / 2).coerceAtLeast(0.dp))
                .size(size)
                .background(WiptvColors.focus, CircleShape),
        )
    }
}

/** One of the panel's round buttons: white with a dark symbol when highlighted, grey otherwise. */
@Composable
private fun RoundButton(glyph: PlayerGlyph, lit: Boolean, size: Int, marked: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(if (lit) WiptvColors.focus else WiptvColors.elevated.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, if (lit) WiptvColors.onLight else WiptvColors.text, Modifier.size((size * 0.42f).dp))
        if (marked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(8.dp)
                    .background(WiptvColors.accent, CircleShape),
            )
        }
    }
}

private enum class PlayerGlyph { BACK, PLAY, PAUSE, AUDIO, SUBTITLES, SKIP, SLEEP }

/** The panel's small symbols, drawn on a 24-unit square. */
@Composable
private fun Glyph(glyph: PlayerGlyph, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            PlayerGlyph.BACK -> drawPath(
                Path().apply { moveTo(15f * u, 18f * u); lineTo(9f * u, 12f * u); lineTo(15f * u, 6f * u) },
                color, style = line,
            )
            PlayerGlyph.PLAY -> drawPath(
                Path().apply { moveTo(7f * u, 4.5f * u); lineTo(19.5f * u, 12f * u); lineTo(7f * u, 19.5f * u); close() },
                color,
            )
            PlayerGlyph.PAUSE -> {
                drawRoundRect(color, topLeft = p(6f, 5f), size = Size(4f * u, 14f * u), cornerRadius = CornerRadius(u, u))
                drawRoundRect(color, topLeft = p(14f, 5f), size = Size(4f * u, 14f * u), cornerRadius = CornerRadius(u, u))
            }
            PlayerGlyph.AUDIO -> {
                drawPath(
                    Path().apply {
                        moveTo(11f * u, 5f * u); lineTo(6f * u, 9f * u); lineTo(3f * u, 9f * u)
                        lineTo(3f * u, 15f * u); lineTo(6f * u, 15f * u); lineTo(11f * u, 19f * u); close()
                    },
                    color, style = line,
                )
                drawArc(color, -45f, 90f, false, topLeft = p(10.5f, 7f), size = Size(10f * u, 10f * u), style = line)
                drawArc(color, -50f, 100f, false, topLeft = p(10f, 3.5f), size = Size(17f * u, 17f * u), style = line)
            }
            PlayerGlyph.SUBTITLES -> {
                drawRoundRect(color, topLeft = p(3f, 5f), size = Size(18f * u, 14f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = line)
                drawLine(color, p(7f, 13f), p(11f, 13f), strokeWidth = 2f * u, cap = StrokeCap.Round)
                drawLine(color, p(13f, 13f), p(17f, 13f), strokeWidth = 2f * u, cap = StrokeCap.Round)
                drawLine(color, p(7f, 16f), p(13f, 16f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
            PlayerGlyph.SKIP -> {
                drawPath(Path().apply { moveTo(5f * u, 5f * u); lineTo(14f * u, 12f * u); lineTo(5f * u, 19f * u); close() }, color)
                drawLine(color, p(18f, 5f), p(18f, 19f), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
            }
            PlayerGlyph.SLEEP -> {
                // A crescent moon: a disc with an overlapping disc taken out of it.
                val disc = Path().apply { addOval(Rect(p(3f, 3f), Size(18f * u, 18f * u))) }
                val bite = Path().apply { addOval(Rect(p(9f, 0f), Size(16f * u, 16f * u))) }
                drawPath(Path.combine(PathOperation.Difference, disc, bite), color)
            }
        }
    }
}

/**
 * The sleep timer's choices, over the right of the picture: off, a number of minutes, or the end
 * of what's playing; the current one ticked. OK picks one; Back closes it.
 */
@Composable
private fun SleepPanel(isEpisode: Boolean, onChoose: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val current: Int? = when {
        SleepTimer.atEnd -> SLEEP_AT_END
        SleepTimer.stopAt != null -> 0 // a number of minutes, already counting down
        else -> null
    }
    val choices: List<Pair<Int?, String>> = listOf(
        null to "Off",
        15 to "15 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "1 hour",
        90 to "1 hour 30 minutes",
        SLEEP_AT_END to if (isEpisode) "End of this episode" else "End of this film",
    )
    Column(
        modifier = modifier
            .width(320.dp)
            .glass(RoundedCornerShape(18.dp), Glass.OverVideo)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Sleep timer", style = WiptvType.hint, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp))
        SleepTimer.describe()?.let {
            Text(it.removePrefix("Sleep timer: ").replaceFirstChar { c -> c.uppercase() }, style = WiptvType.hint, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
        }
        choices.forEachIndexed { index, (minutes, label) ->
            val ticked = minutes == current
            ListRow(
                onClick = { onChoose(minutes) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            ) { focused ->
                Text(
                    text = (if (ticked) "✓  " else "     ") + label,
                    maxLines = 1,
                    style = WiptvType.metadata.copy(
                        color = if (focused || ticked) WiptvColors.text else WiptvColors.secondary,
                        fontWeight = if (ticked) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
        Text("Back to close", style = WiptvType.hint, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
    }
}

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

/** 1:05:09 or 5:09. */
internal fun clockOf(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

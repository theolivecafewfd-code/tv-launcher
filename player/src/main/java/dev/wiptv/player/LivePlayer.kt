package dev.wiptv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * The one player behind both the small preview and full screen. Sharing it means only one
 * connection to the provider (many accounts allow just one) and going full screen is instant.
 */
@OptIn(UnstableApi::class)
@Stable
class LivePlayer(context: Context) {

    val exo: ExoPlayer = newExoPlayer(context)

    /** The channel playing (or loading) now. */
    var channel by mutableStateOf<Channel?>(null)
        private set

    /** The channel before this one, from any group, for "back to the last channel". */
    var previous by mutableStateOf<Channel?>(null)
        private set
    var buffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** True while a channel is actually playing (not loading, stopped or failed). */
    var isPlaying by mutableStateOf(false)
        private set

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Fell too far behind the live stream: jump back to live.
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                buffering = false
                error = describe(e)
            }
        })
    }

    /** Plays [target], unless it's already the channel playing. */
    fun play(login: Login, format: String, target: Channel) {
        if (channel?.id == target.id && exo.playbackState != Player.STATE_IDLE) return
        channel?.let { if (it.id != target.id) previous = it }
        channel = target
        error = null
        buffering = true
        val item = MediaItem.Builder()
            .setUri(Xtream.liveUrl(login, target, format))
            .setMimeType(if (format == "m3u8") MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP2T)
            .build()
        exo.setMediaItem(item)
        exo.prepare()
    }

    fun pauseForBackground() = exo.stop()

    fun resumeFromBackground() {
        if (exo.currentMediaItem != null) {
            exo.seekToDefaultPosition()
            exo.prepare()
        }
    }

    fun release() = exo.release()
}

/**
 * One shared player for the screen, stopped when the app goes to the background. While it's
 * playing, the TV is kept awake so the screen saver doesn't come on mid-programme.
 */
@Composable
fun rememberLivePlayer(): LivePlayer {
    val context = LocalContext.current
    val live = remember { LivePlayer(context) }

    KeepScreenOn(live.isPlaying)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> live.pauseForBackground()
                Lifecycle.Event.ON_START -> live.resumeFromBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            live.release()
        }
    }
    return live
}

/**
 * Shows the shared player's picture. Only one of these should be [attached] at a time: the
 * preview lets go while full screen is open, and takes the picture back afterwards.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(live: LivePlayer, attached: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        update = { view -> view.player = if (attached) live.exo else null },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

/** A short reason a channel won't play. */
private fun describe(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "The provider refused this channel. It may be offline, or all your screens are in use."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Couldn't reach the provider. Check the internet connection."
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "This TV can't play this channel's video format."
    else -> "This channel isn't playing (${e.errorCodeName}). Try another channel."
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** An ExoPlayer set up for IPTV providers: follows http/https redirects, sensible timeouts. */
@OptIn(UnstableApi::class)
fun newExoPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("WIPTVPlayer/${BuildConfig.VERSION_NAME}")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .build()
        .apply { playWhenReady = true }
}

/**
 * Keeps the TV awake while [active], so the screen saver doesn't come on mid-programme. Asked of
 * the app's main window, which stays on screen behind full screen too.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    DisposableEffect(window, active) {
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

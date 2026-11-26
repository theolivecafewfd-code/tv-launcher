package dev.wiptv.player

import android.widget.Toast
import dev.wiptv.update.UpdateState
import dev.wiptv.update.Updater
import dev.wiptv.update.label
import dev.wiptv.update.loud
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import dev.wiptv.design.GlassPill
import dev.wiptv.design.NavItem
import dev.wiptv.design.SearchIcon
import dev.wiptv.design.WiptvTopMenu
import dev.wiptv.design.WiptvButton
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.Wordmark
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The player's colours, now taken from the shared WIPTV glass tokens (the design module), so the
 * launcher and the player match. Names kept so existing screens pick the new look up as they are.
 */
internal object Palette {
    val background = WiptvColors.background
    val text = WiptvColors.text
    val quiet = WiptvColors.secondary
    val tile = WiptvColors.surface
    val tileFocused = WiptvColors.elevated
    val glow = WiptvColors.focus
    val good = WiptvColors.positive
    val warn = WiptvColors.warning
}

/**
 * The player's parts, in the top menu's order (after search). Live TV holds the guide and
 * Favourites; My Page (LIBRARY, so other apps' "section" extras still work) holds Continue
 * watching, My List and Settings.
 */
enum class Section(val label: String) {
    HOME("Home"),
    LIVE("Live TV"),
    MOVIES("Movies"),
    SERIES("TV Shows"),
    LIBRARY("My Page"),
}

/** A title another app asked the player to open (the launcher's "Watch on IPTV"). */
data class OpenTitle(val kind: VodKind, val id: Long, val resumeKey: String? = null)

/** Shows the sign-in screen until there are saved details, then Live TV, Movies or Series. */
@Composable
fun PlayerRoot(
    initialSection: Section = Section.HOME,
    open: OpenTitle? = null,
    openChannel: Long? = null,
    onOpenFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    var login by remember { mutableStateOf(LoginStore.load(context)) }
    val update = rememberUpdateState()

    val current = login
    if (current == null) {
        LoginScreen(
            update = update,
            onSignedIn = { newLogin ->
                LoginStore.save(context, newLogin)
                login = newLogin
            },
        )
    } else {
        var section by remember { mutableStateOf(initialSection) }
        val store = remember { VodStore(context) }
        val scope = rememberCoroutineScope()
        // The title the launcher opened us on: only until you move to another section, so going
        // back to that section doesn't open it again.
        var launchOpen by remember { mutableStateOf(open) }
        // A title to open on its details page in its section (Home's More info, My Page's posters);
        // closing that page goes back to the section it was opened from.
        var surprise by remember { mutableStateOf<IndexedTitle?>(null) }
        var surpriseFrom by remember { mutableStateOf<Section?>(null) }
        val surpriseClosed = {
            surpriseFrom?.let { section = it }
            surpriseFrom = null
        }
        // Keep the list of films and series the launcher asks about up to date (about daily).
        LaunchedEffect(current) { CatalogIndex.refreshIfStale(context, current) }
        // Where a title Home or My Page opened to play or resume came from: closing it goes back
        // there (rather than to the launcher, as one the launcher opened does).
        var returnTo by remember { mutableStateOf<Section?>(null) }
        // A channel to play full screen on opening Live TV: the launcher's, or one picked in My Page.
        var channelToOpen by remember { mutableStateOf(openChannel) }
        // That channel's details when it isn't one of your favourites (Home's Sports row).
        var channelInfo by remember { mutableStateOf<Channel?>(null) }
        val openFinished: () -> Unit = {
            val back = returnTo
            if (back != null) {
                returnTo = null
                launchOpen = null
                section = back
            } else {
                onOpenFinished()
            }
        }
        val tabs: @Composable () -> Unit = {
            SectionTabs(
                selected = section,
                onSelect = {
                    launchOpen = null
                    returnTo = null
                    channelToOpen = null
                    channelInfo = null
                    section = it
                },
                update = update,
            )
        }
        fun sectionOf(kind: VodKind) = if (kind == VodKind.MOVIE) Section.MOVIES else Section.SERIES
        // Only one part is on screen at a time, so leaving Live TV also stops its stream.
        key(section) {
            val signOut = {
                LoginStore.clear(context)
                login = null
            }
            /** The details page of a title, in its section; closing it comes back to [from]. */
            fun details(kind: VodKind, id: Long, from: Section) {
                launchOpen = null
                surprise = IndexedTitle(kind, id, "", null, null, null, null, "")
                surpriseFrom = from
                section = sectionOf(kind)
            }
            /** Continue watching a film or episode; closing the player comes back to [from]. */
            fun resume(point: ResumePoint, from: Section) {
                val kind = if (point.key.startsWith("movie:")) VodKind.MOVIE else VodKind.SERIES
                val id = point.seriesId ?: point.key.substringAfter(':').toLongOrNull() ?: 0L
                returnTo = from
                launchOpen = OpenTitle(kind, id, resumeKey = point.key)
                section = sectionOf(kind)
            }
            when (section) {
                Section.HOME -> HomeScreen(
                    login = current,
                    store = store,
                    header = tabs,
                    onPlay = { title ->
                        // A film plays at once; a series opens its page.
                        returnTo = Section.HOME
                        launchOpen = OpenTitle(title.kind, title.id)
                        section = sectionOf(title.kind)
                    },
                    onDetails = { kind, id -> details(kind, id, Section.HOME) },
                    onResume = { point -> resume(point, Section.HOME) },
                    onWatchChannel = { channel ->
                        // Full screen; Back there goes to its group in Live TV.
                        channelToOpen = channel.id
                        channelInfo = channel
                        section = Section.LIVE
                    },
                    onOpenGroup = { groupId ->
                        LiveGroupPrefs.setGroup(context, groupId)
                        section = Section.LIVE
                    },
                )
                Section.LIBRARY -> LibraryScreen(
                    login = current,
                    store = store,
                    header = tabs,
                    onSignOut = signOut,
                    update = update,
                    onDetails = { kind, id -> details(kind, id, Section.LIBRARY) },
                    onResume = { point -> resume(point, Section.LIBRARY) },
                    onWatchChannel = { channel ->
                        // Full screen; Back there goes to its group in Live TV.
                        channelToOpen = channel.id
                        section = Section.LIVE
                    },
                )
                Section.LIVE -> LiveScreen(
                    login = current,
                    update = update,
                    header = tabs,
                    openChannel = channelToOpen,
                    openChannelInfo = channelInfo,
                    onSignOut = signOut,
                )
                Section.MOVIES -> CatalogScreen(
                    VodKind.MOVIE, current, update, store, tabs,
                    openId = launchOpen?.takeIf { it.kind == VodKind.MOVIE && it.resumeKey == null }?.id,
                    openResume = launchOpen?.takeIf { it.kind == VodKind.MOVIE }?.resumeKey,
                    onOpenFinished = openFinished,
                    surpriseId = surprise?.takeIf { it.kind == VodKind.MOVIE }?.id,
                    onSurpriseShown = { surprise = null },
                    onSurpriseClosed = surpriseClosed,
                )
                Section.SERIES -> CatalogScreen(
                    VodKind.SERIES, current, update, store, tabs,
                    openId = launchOpen?.takeIf { it.kind == VodKind.SERIES && it.resumeKey == null }?.id,
                    openResume = launchOpen?.takeIf { it.kind == VodKind.SERIES }?.resumeKey,
                    onOpenFinished = openFinished,
                    surpriseId = surprise?.takeIf { it.kind == VodKind.SERIES }?.id,
                    onSurpriseShown = { surprise = null },
                    onSurpriseClosed = surpriseClosed,
                )
            }
        }
    }
}

/** The update pill's state and what pressing it does, shared by every screen. */
class UpdateHandle(val state: UpdateState, val onClick: () -> Unit)

@Composable
fun rememberUpdateState(): UpdateHandle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

    // Check when the app opens, then every 30 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            if (state !is UpdateState.Downloading && state != UpdateState.Installing) {
                state = Updater.check(context, BuildConfig.UPDATE_REPO)
            }
            delay(30 * 60 * 1000L)
        }
    }

    return UpdateHandle(state) {
        when (val s = state) {
            is UpdateState.Available, is UpdateState.Failed -> {
                val target = if (s is UpdateState.Failed) s.retry else s as UpdateState.Available
                if (!Updater.canInstall(context)) {
                    Updater.openInstallPermissionSettings(context)
                } else scope.launch {
                    state = UpdateState.Downloading(0)
                    state = try {
                        Updater.downloadAndInstall(context, target) { app, percent ->
                            state = UpdateState.Downloading(percent, app)
                        }
                        UpdateState.Installing
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        UpdateState.Failed(e.message ?: "download failed", target)
                    }
                    // Only the launcher was updated (this app would have restarted): check again
                    // once its install has had time to finish, so the pill doesn't stay on "Installing".
                    if (state == UpdateState.Installing && target.updates.none { it.app.packageName == context.packageName }) {
                        delay(20_000)
                        state = Updater.check(context, BuildConfig.UPDATE_REPO)
                    }
                }
            }
            UpdateState.Installing -> scope.launch {
                state = UpdateState.Checking
                state = Updater.check(context, BuildConfig.UPDATE_REPO)
            }
            UpdateState.UpToDate, is UpdateState.CheckFailed -> scope.launch {
                state = UpdateState.Checking
                state = Updater.check(context, BuildConfig.UPDATE_REPO)
            }
            else -> Unit
        }
    }
}

/** The pill in the top corner: build number, or a button to install a newer one. */
@Composable
fun UpdatePill(update: UpdateHandle, modifier: Modifier = Modifier) {
    val state = update.state
    val label = state.label(BuildConfig.VERSION_CODE)
    GlassPill(
        text = label,
        onClick = update.onClick,
        loud = state.loud,
        modifier = modifier.widthIn(max = 320.dp),
    )
}

/** A button in the WIPTV glass style (primary ones rest on a light fill). */
@Composable
fun PlayerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    WiptvButton(text = text, onClick = onClick, modifier = modifier, primary = primary, enabled = enabled)
}

/**
 * The top menu on every screen (after Netflix's): search, then Home, Live TV, Movies, TV Shows
 * and My Page in one centred capsule, the clock at the right (with a lit Update when there's a
 * newer build). OK on a section switches to it; focus alone doesn't. Search isn't built yet: OK
 * on it just says so.
 */
@Composable
fun SectionTabs(selected: Section, onSelect: (Section) -> Unit, update: UpdateHandle) {
    val context = LocalContext.current
    val search = NavItem(
        icon = { colour -> SearchIcon(color = colour, size = 18.dp) },
        description = "Search",
        onClick = { Toast.makeText(context, "Search is coming soon", Toast.LENGTH_SHORT).show() },
    )
    WiptvTopMenu(
        nav = listOf(search) + Section.entries.map { target ->
            NavItem(label = target.label, selected = selected == target, onClick = { if (selected != target) onSelect(target) })
        },
        updateLabel = update.state.label(BuildConfig.VERSION_CODE),
        updateLoud = update.state.loud,
        onUpdateClick = update.onClick,
    )
}

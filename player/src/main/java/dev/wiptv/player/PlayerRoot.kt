package dev.wiptv.player

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
import dev.wiptv.design.DiceIcon
import dev.wiptv.design.NavItem
import dev.wiptv.design.SearchIcon
import dev.wiptv.design.WiptvHeader
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

/** The player's three parts, switched with the tabs at the top left. */
enum class Section(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series") }

/** A title another app asked the player to open (the launcher's "Watch on IPTV"). */
data class OpenTitle(val kind: VodKind, val id: Long, val resumeKey: String? = null)

/** Shows the sign-in screen until there are saved details, then Live TV, Movies or Series. */
@Composable
fun PlayerRoot(
    initialSection: Section = Section.LIVE,
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
        // A surprise pick (the dice), opened on its details page in its section.
        var surprise by remember { mutableStateOf<IndexedTitle?>(null) }
        // Keep the list of films and series the launcher asks about up to date (about daily).
        LaunchedEffect(current) { CatalogIndex.refreshIfStale(context, current) }
        val tabs: @Composable () -> Unit = {
            SectionTabs(
                selected = section,
                onSelect = {
                    launchOpen = null
                    section = it
                },
                onSurprise = {
                    scope.launch {
                        val pick = withContext(Dispatchers.IO) { CatalogIndex.random(context) }
                        if (pick == null) {
                            android.widget.Toast.makeText(context, "Your provider's list isn't ready yet", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            launchOpen = null
                            surprise = pick
                            section = if (pick.kind == VodKind.MOVIE) Section.MOVIES else Section.SERIES
                        }
                    }
                },
                update = update,
            )
        }
        // Only one part is on screen at a time, so leaving Live TV also stops its stream.
        key(section) {
            when (section) {
                Section.LIVE -> LiveScreen(
                    login = current,
                    update = update,
                    header = tabs,
                    openChannel = openChannel,
                    onSignOut = {
                        LoginStore.clear(context)
                        login = null
                    },
                )
                Section.MOVIES -> CatalogScreen(
                    VodKind.MOVIE, current, update, store, tabs,
                    openId = launchOpen?.takeIf { it.kind == VodKind.MOVIE && it.resumeKey == null }?.id,
                    openResume = launchOpen?.takeIf { it.kind == VodKind.MOVIE }?.resumeKey,
                    onOpenFinished = onOpenFinished,
                    surpriseId = surprise?.takeIf { it.kind == VodKind.MOVIE }?.id,
                    onSurpriseShown = { surprise = null },
                )
                Section.SERIES -> CatalogScreen(
                    VodKind.SERIES, current, update, store, tabs,
                    openId = launchOpen?.takeIf { it.kind == VodKind.SERIES && it.resumeKey == null }?.id,
                    openResume = launchOpen?.takeIf { it.kind == VodKind.SERIES }?.resumeKey,
                    onOpenFinished = onOpenFinished,
                    surpriseId = surprise?.takeIf { it.kind == VodKind.SERIES }?.id,
                    onSurpriseShown = { surprise = null },
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
 * The header across the top of Live TV, Movies and Series: the same as the launcher's (greeting,
 * weather, clock and build, then the navigation capsule), so moving between the two apps feels
 * like one. TV Shows is the player's Series and Movies its Movies; Live TV is Live TV. Apps and
 * search go back to the launcher with the Apps tray or search open; the dice opens a surprise
 * pick from your provider's films and series. OK on a section switches to it.
 */
@Composable
fun SectionTabs(selected: Section, onSelect: (Section) -> Unit, onSurprise: () -> Unit, update: UpdateHandle) {
    val context = LocalContext.current
    fun section(label: String, target: Section) =
        NavItem(label = label, selected = selected == target, onClick = { if (selected != target) onSelect(target) })
    WiptvHeader(
        nav = listOf(
            section("TV Shows", Section.SERIES),
            section("Movies", Section.MOVIES),
            section("Live TV", Section.LIVE),
            NavItem(label = "Apps", onClick = { Launcher.open(context, "apps") }),
            NavItem(
                icon = { colour -> SearchIcon(color = colour, size = 20.dp) },
                description = "Search",
                onClick = { Launcher.open(context, "search") },
            ),
            NavItem(icon = { colour -> DiceIcon(color = colour, size = 20.dp) }, description = "Surprise me", onClick = onSurprise),
        ),
        updateLabel = update.state.label(BuildConfig.VERSION_CODE),
        updateLoud = update.state.loud,
        onUpdateClick = update.onClick,
    )
}

/** The launcher, for the header's Apps and search. */
private object Launcher {
    private const val PACKAGE = "dev.tvlauncher"

    /** Hands over to the launcher with the Apps tray or search open, then closes the player. */
    fun open(context: android.content.Context, what: String) {
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .setClassName(PACKAGE, "$PACKAGE.MainActivity")
                    .putExtra("wiptv_open", what)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle(),
            )
            (context as? android.app.Activity)?.finish()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Couldn't open WIPTV", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

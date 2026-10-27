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
import dev.wiptv.design.SectionCapsule
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

/** Shows the sign-in screen until there are saved details, then Live TV, Movies or Series. */
@Composable
fun PlayerRoot() {
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
        var section by remember { mutableStateOf(Section.LIVE) }
        val store = remember { VodStore(context) }
        val tabs: @Composable () -> Unit = { SectionTabs(selected = section, onSelect = { section = it }, update = update) }
        // Only one part is on screen at a time, so leaving Live TV also stops its stream.
        key(section) {
            when (section) {
                Section.LIVE -> LiveScreen(
                    login = current,
                    update = update,
                    header = tabs,
                    onSignOut = {
                        LoginStore.clear(context)
                        login = null
                    },
                )
                Section.MOVIES -> CatalogScreen(VodKind.MOVIE, current, update, store, tabs)
                Section.SERIES -> CatalogScreen(VodKind.SERIES, current, update, store, tabs)
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
 * The header across the top of Live TV, Movies and Series: the wordmark, the section capsule in
 * the middle and the update pill on the right. OK on a section switches to it.
 */
@Composable
fun SectionTabs(selected: Section, onSelect: (Section) -> Unit, update: UpdateHandle) {
    Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        Wordmark(modifier = Modifier.align(Alignment.CenterStart))
        SectionCapsule(
            labels = Section.entries.map { it.label },
            selected = Section.entries.indexOf(selected),
            onSelect = { onSelect(Section.entries[it]) },
            modifier = Modifier.align(Alignment.Center),
        )
        UpdatePill(update, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

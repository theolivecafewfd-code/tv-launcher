package dev.wiptv.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The player's colours: the same dark purple family as the launcher. */
internal object Palette {
    val background = Color(0xFF16111E)
    val text = Color(0xFFF4F1F8)
    val quiet = Color(0xFFB3AAC2)
    val tile = Color(0xFF241D30)
    val tileFocused = Color(0xFF332A44)
    val glow = Color(0xFFEDE4FF)
    val good = Color(0xFF7FD69A)
    val warn = Color(0xFFFFB86B)
}

/** Shows the sign-in screen until there are saved details, then Live TV. */
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
        LiveScreen(
            login = current,
            update = update,
            onSignOut = {
                LoginStore.clear(context)
                login = null
            },
        )
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
                state = Updater.check()
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
                        Updater.downloadAndInstall(context, target) { percent ->
                            state = UpdateState.Downloading(percent)
                        }
                        UpdateState.Installing
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        UpdateState.Failed(e.message ?: "download failed", target)
                    }
                }
            }
            UpdateState.UpToDate, is UpdateState.CheckFailed -> scope.launch {
                state = UpdateState.Checking
                state = Updater.check()
            }
            else -> Unit
        }
    }
}

/** The pill in the top corner: build number, or a button to install a newer one. */
@Composable
fun UpdatePill(update: UpdateHandle, modifier: Modifier = Modifier) {
    val state = update.state
    val label = when (state) {
        UpdateState.Checking -> "Checking for updates…"
        UpdateState.UpToDate -> "Up to date · build ${BuildConfig.VERSION_CODE}"
        is UpdateState.CheckFailed -> "Couldn't check: ${state.reason}"
        is UpdateState.Available -> "Update available · build ${state.build}"
        is UpdateState.Downloading -> "Downloading update… ${state.percent}%"
        UpdateState.Installing -> "Installing update…"
        is UpdateState.Failed -> "Update failed · press to retry"
    }
    val loud = state is UpdateState.Available || state is UpdateState.Failed
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = update.onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (loud) Palette.glow else Palette.tile,
            contentColor = if (loud) Palette.background else Palette.quiet,
            focusedContainerColor = Color.White,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** A wide, rounded button in the player's style. */
@Composable
fun PlayerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Palette.glow else Palette.tile,
            contentColor = if (primary) Palette.background else Palette.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Palette.background,
            disabledContainerColor = Palette.tile.copy(alpha = 0.5f),
            disabledContentColor = Palette.quiet,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

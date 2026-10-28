package dev.wiptv.player

import dev.wiptv.design.wiptvBackground
import dev.wiptv.design.glass
import dev.wiptv.design.Wordmark
import dev.wiptv.design.WiptvType
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.Glass
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Your provider account: whether it's active, when it runs out and how many screens you can
 * watch on at once. Live TV, the guide and films are added to this screen in the next phases.
 */
@Composable
fun AccountScreen(login: Login, update: UpdateHandle, onSignOut: () -> Unit) {
    var account by remember { mutableStateOf<Account?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        error = null
        try {
            account = Xtream.signIn(login)
        } catch (e: CancellationException) {
            throw e
        } catch (e: XtreamException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your account: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { firstButton.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    // The sign-out confirmation goes away by itself if it isn't pressed again.
    LaunchedEffect(confirmSignOut) {
        if (confirmSignOut) {
            delay(4000)
            confirmSignOut = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wiptvBackground()
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        UpdatePill(update, modifier = Modifier.align(Alignment.TopEnd))

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Wordmark()
            Spacer(Modifier.height(10.dp))
            Text(text = "Your account", style = WiptvType.pageTitle)
            Text(text = "${login.username} · ${hostOf(login.server)}", style = WiptvType.metadata)
            Spacer(Modifier.height(18.dp))

            val current = account
            when {
                current != null -> AccountCard(current)
                error != null -> Text(text = error!!, style = WiptvType.metadata.copy(color = WiptvColors.warning))
                else -> Text(text = "Checking your account…", style = WiptvType.metadata)
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerButton(
                    text = "Refresh",
                    onClick = { attempt++ },
                    modifier = Modifier.focusRequester(firstButton),
                )
                PlayerButton(
                    text = if (confirmSignOut) "Press again to sign out" else "Sign out",
                    onClick = { if (confirmSignOut) onSignOut() else confirmSignOut = true },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(text = "Back returns to Live TV.", style = WiptvType.hint)
        }
    }
}

@Composable
private fun AccountCard(account: Account) {
    Column(
        modifier = Modifier
            .width(520.dp)
            .glass(WiptvShapes.panel, Glass.Sheet)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InfoRow(
            label = "Status",
            value = account.status + if (account.trial) " (trial)" else "",
            colour = if (account.isActive) Palette.good else Palette.warn,
        )
        InfoRow(label = "Expires", value = describeExpiry(account.expires))
        InfoRow(
            label = "Screens",
            value = "Watch on ${account.maxConnections} at once · ${account.activeConnections} in use now",
        )
        val groups = listOfNotNull(
            account.liveGroups?.let { "$it Live TV" },
            account.movieGroups?.let { "$it Movie" },
            account.seriesGroups?.let { "$it Series" },
        )
        if (groups.isNotEmpty()) {
            InfoRow(label = "Groups", value = groups.joinToString(" · "))
        }
        account.timezone?.let { InfoRow(label = "Provider time zone", value = it) }
    }
}

@Composable
private fun InfoRow(label: String, value: String, colour: Color = WiptvColors.text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.width(150.dp), style = WiptvType.metadata)
        Text(text = value, style = WiptvType.body.copy(fontWeight = FontWeight.Medium, color = colour))
    }
}

/** "12 Mar 2027 (in 4 months)", "Never", or "Ended 2 Jan 2026". */
private fun describeExpiry(expires: Long?): String {
    if (expires == null) return "Never"
    val date = Instant.ofEpochSecond(expires).atZone(ZoneId.systemDefault()).toLocalDate()
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> "Ended $formatted"
        days == 0L -> "$formatted (today)"
        days == 1L -> "$formatted (tomorrow)"
        days < 45 -> "$formatted (in $days days)"
        else -> "$formatted (in ${ChronoUnit.MONTHS.between(today, date)} months)"
    }
}

private fun hostOf(server: String): String = runCatching { URL(server).host }.getOrNull() ?: server

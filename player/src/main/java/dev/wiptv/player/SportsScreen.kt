package dev.wiptv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.SectionRow
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import dev.wiptv.design.rememberRowStart
import dev.wiptv.design.wiptvSpotlight
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Edge = 32.dp

/**
 * Sports: the Premier League today and the next two days, a row of match cards for each day
 * ("Today", "Tomorrow", "Sunday"), with the badges, each team's record, live scores and the
 * channel. OK on a match watches the channel you chose for it, or opens the list of channels that
 * might be showing it, from your provider's guide; Menu (≡) opens the list to change it. Days
 * without a game are listed at the bottom.
 */
@Composable
fun SportsScreen(
    login: Login,
    header: @Composable () -> Unit,
    onWatchChannel: (Channel) -> Unit,
) {
    val sports = rememberSports(login, allDays = true)
    val today = LocalDate.now()
    val days = (0 until SPORTS_DAYS).map { today.plusDays(it.toLong()) }
    val all = sports.all
    val byDay = all.orEmpty().groupBy { it.day }
    var choosing by remember { mutableStateOf<Match?>(null) }

    // Focus starts on the first match.
    val first = remember { FocusRequester() }
    val firstMatch = days.firstNotNullOfOrNull { byDay[it]?.firstOrNull() }?.id
    LaunchedEffect(firstMatch) {
        if (firstMatch == null) return@LaunchedEffect
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().wiptvSpotlight()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Box(modifier = Modifier.padding(horizontal = Edge)) { header() }
            Row(
                modifier = Modifier.padding(start = Edge, end = Edge, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Premier League",
                    style = WiptvType.body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Today and the next two days",
                    modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                    style = WiptvType.metadata.copy(color = WiptvColors.quiet),
                )
            }

            PivotScrolling(pivot = 24.dp, upToTopBelow = 64.dp) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    if (all == null) {
                        item(key = "loading") {
                            Text(
                                text = "Loading fixtures…",
                                modifier = Modifier.padding(horizontal = Edge),
                                style = WiptvType.metadata.copy(color = WiptvColors.quiet),
                            )
                        }
                    }
                    for (day in days) {
                        val matches = byDay[day].orEmpty()
                            .sortedWith(compareByDescending<Match> { it.live }.thenBy { it.kickOff })
                        if (matches.isEmpty()) continue
                        item(key = "day:$day") {
                            val rowStart = rememberRowStart()
                            SectionRow(
                                title = dayTitle(day, today),
                                edge = Edge,
                                contentStart = Edge,
                                spacing = 14.dp,
                                rowModifier = rowStart.rowModifier(matches.first().id),
                            ) {
                                items(matches, key = { it.id }) { match ->
                                    MatchCard(
                                        match = match,
                                        state = sports,
                                        onWatch = onWatchChannel,
                                        onChoose = { choosing = match },
                                        label = "PREMIER LEAGUE",
                                        modifier = rowStart.card(match.id)
                                            .then(if (match.id == firstMatch) Modifier.focusRequester(first) else Modifier),
                                    )
                                }
                            }
                        }
                    }
                    // "No games: Tomorrow · Sunday".
                    val empty = if (all == null) emptyList() else days.filter { byDay[it].isNullOrEmpty() }
                    if (empty.isNotEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "No games: " + empty.joinToString("  ·  ") { dayTitle(it, today) },
                                modifier = Modifier.padding(horizontal = Edge),
                                style = WiptvType.metadata.copy(color = WiptvColors.quiet),
                            )
                        }
                    }
                }
            }
        }
    }

    choosing?.let { match ->
        ChannelPanel(match = match, state = sports, onWatch = onWatchChannel, onDismiss = { choosing = null })
    }
}

/** "Today", "Tomorrow", then the weekday ("Sunday"), with the date: "Sunday 29 Nov". */
private fun dayTitle(day: LocalDate, today: LocalDate): String {
    val date = day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))
    return when (day) {
        today -> "Today  ·  $date"
        today.plusDays(1) -> "Tomorrow  ·  $date"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK))
    }
}

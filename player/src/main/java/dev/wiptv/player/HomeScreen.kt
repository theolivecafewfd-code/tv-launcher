package dev.wiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wiptv.design.ActionButton
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import dev.wiptv.design.wiptvSpotlight
import kotlinx.coroutines.delay

private val Edge = 32.dp

/** Home's featured title: picked once per run, so coming back to Home shows the same one. */
private object HomeFeature {
    var pick: LibraryPick? = null
}

/** How tall the featured card is: the top of the row underneath peeks in below it. */
private val FeatureHeight = 392.dp

/**
 * Home, where the player opens (after Netflix's new home page): near-black with a soft spotlight
 * from the top, the top menu, and one large rounded card featuring a title from this week's top
 * 10 on TMDB that your provider has, picked at random — its artwork filling the card, with the
 * title, details, synopsis, Play and More info over it, and its place this week at the bottom
 * right. Play starts a film (a series opens its page); More info opens the details page.
 *
 * Under the card, Continue watching (films and episodes together), where the focused card always
 * slides to the start of the row. Down scrolls the page to it; Up goes back to the whole card.
 * Then today's Premier League ([SportsShelf]), when there's a match on, ending with All fixtures
 * (the Sports page).
 */
@Composable
fun HomeScreen(
    login: Login,
    store: VodStore,
    header: @Composable () -> Unit,
    onPlay: (IndexedTitle) -> Unit,
    onDetails: (VodKind, Long) -> Unit,
    onResume: (ResumePoint) -> Unit,
    onWatchChannel: (Channel) -> Unit,
    onOpenGroup: (String) -> Unit,
    onSeeAllSports: () -> Unit,
) {
    val picks = rememberTrendingPicks()
    val pick = remember(picks) {
        HomeFeature.pick ?: picks.orEmpty().let { all ->
            // This week's top 10 (films or shows) if your provider has any of them, else any match.
            all.filter { it.trending.rank <= 10 }.ifEmpty { all }.randomOrNull()
        }?.also { HomeFeature.pick = it }
    }

    // Read each time Home opens, so it's up to date after watching something.
    val resume = remember { store.resumePoints() }
    val sports = rememberSports(login)

    // Focus starts on Play once the card is there.
    val play = remember { FocusRequester() }
    LaunchedEffect(pick != null) {
        if (pick == null) return@LaunchedEffect
        repeat(20) {
            if (runCatching { play.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().wiptvSpotlight()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Box(modifier = Modifier.padding(horizontal = Edge)) { header() }
            // Down to a row settles it near the top; Up to the card's buttons goes back to the
            // very top, so the whole card shows again.
            PivotScrolling(pivot = 34.dp, upToTopBelow = 64.dp) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item(key = "feature") {
                        FeatureCard(
                            pick = pick,
                            loading = picks == null,
                            playRequester = play,
                            onPlay = onPlay,
                            onDetails = onDetails,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(FeatureHeight)
                                .padding(horizontal = Edge),
                        )
                    }
                    if (resume.isNotEmpty()) {
                        item(key = "resume") { ResumeShelf(resume, onResume, pinToStart = true) }
                    }
                    if (sports.visible) {
                        item(key = "sports") { SportsShelf(login, sports, Edge, onWatchChannel, onOpenGroup, onSeeAllSports) }
                    }
                }
            }
        }
    }
}

/** The big card: the artwork, darkened on the left and at the bottom for the text over it. */
@Composable
private fun FeatureCard(
    pick: LibraryPick?,
    loading: Boolean,
    playRequester: FocusRequester,
    onPlay: (IndexedTitle) -> Unit,
    onDetails: (VodKind, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(WiptvColors.surface)
            .border(1.dp, WiptvColors.border, shape),
    ) {
        if (pick == null) {
            Text(
                text = when {
                    loading -> "Finding this week's top 10…"
                    !Tmdb.hasKey -> "Welcome to WIPTV"
                    else -> "Nothing from this week's top 10 is on your provider right now."
                },
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                style = WiptvType.metadata,
            )
            return@Box
        }
        val t = pick.trending
        AsyncImage(
            model = t.backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Shade for the text: from the left, and up from the bottom.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.85f),
                    0.42f to Color.Black.copy(alpha = 0.5f),
                    0.72f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)),
            ),
        )
        // A faint sheen along the top edge, for the glass look.
        Box(
            Modifier.fillMaxWidth().height(70.dp).background(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 1f to Color.Transparent),
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 26.dp)
                .width(480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.hero.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(
                text = listOfNotNull(
                    if (t.kind == VodKind.MOVIE) "Movie" else "TV show",
                    t.genres.joinToString(", ").ifBlank { null },
                    t.year,
                    t.rating.takeIf { it > 0 }?.let { "TMDB %.1f".format(it) },
                ).joinToString(" · "),
                style = WiptvType.metadata.copy(color = WiptvColors.text, fontWeight = FontWeight.Medium),
            )
            if (t.overview.isNotBlank()) {
                Text(
                    text = t.overview,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = WiptvType.metadata.copy(color = WiptvColors.text.copy(alpha = 0.88f)),
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = if (t.kind == VodKind.MOVIE) "▶  Play" else "▶  Watch",
                    onClick = { onPlay(pick.title) },
                    primary = true,
                    modifier = Modifier.focusRequester(playRequester),
                )
                ActionButton(text = "More info", onClick = { onDetails(pick.title.kind, pick.title.id) })
            }
        }

        // Where it is this week.
        Text(
            text = "#${t.rank} in ${if (t.kind == VodKind.MOVIE) "Movies" else "TV Shows"} this week",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 30.dp)
                .glass(WiptvShapes.pill)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = WiptvType.hint.copy(color = WiptvColors.text, fontWeight = FontWeight.SemiBold),
        )
    }
}

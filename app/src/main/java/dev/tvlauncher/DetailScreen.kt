package dev.tvlauncher

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import dev.wiptv.design.ActionButton
import dev.wiptv.design.ArtFocusGap
import dev.wiptv.design.CinematicBackdrop
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.InfoSheet
import dev.wiptv.design.MetaLine
import dev.wiptv.design.PivotScrolling
import dev.wiptv.design.RowScrolling
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * A title's details, full screen: what it is, who's in it, and buttons to watch it, play the
 * trailer, add it to My List or mark it as watched, with similar titles below.
 * It's a dialog, so the home screen keeps its place underneath; Back closes it.
 */
@Composable
fun DetailScreen(
    title: Title,
    onWatch: (Source) -> Unit,
    onTrailer: (String) -> Unit,
    onOpenOther: (Title) -> Unit,
    onPlayOther: (Title) -> Unit,
    onLibraryChange: () -> Unit,
    onClose: () -> Unit,
    /** Set when this page is a Surprise Me pick: adds an "Another one" button. */
    onAnother: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val library = remember { Library(context) }
    var details by remember(title.key) { mutableStateOf<TitleDetails?>(null) }
    var inMyList by remember(title.key) { mutableStateOf(library.isInMyList(title)) }
    var watched by remember(title.key) { mutableStateOf(library.isWatched(title)) }
    val primary = remember { FocusRequester() }

    LaunchedEffect(title.key) {
        details = runCatching { Catalog.details(title) }.getOrNull()
    }
    // Whether your IPTV provider has it (asked of WIPTV Player, from its list on the TV).
    var iptv by remember(title.key) { mutableStateOf<IptvTitle?>(null) }
    var iptvChecked by remember(title.key) { mutableStateOf(false) }
    LaunchedEffect(title.key) {
        iptv = Iptv.match(context, title)
        iptvChecked = true
    }
    // Focus starts on the main action. It waits briefly for the IPTV answer, so it lands on
    // Watch on IPTV when that's the first button.
    LaunchedEffect(title.key, iptvChecked) {
        if (!iptvChecked) delay(600)
        repeat(20) {
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    var plotCut by remember(title.key) { mutableStateOf(false) }
    var showMore by remember(title.key) { mutableStateOf(false) }
    val moreInfoButton = remember { FocusRequester() }
    var refocusMoreInfo by remember { mutableStateOf(false) }
    // Closing More info puts focus back on its button.
    LaunchedEffect(refocusMoreInfo) {
        if (!refocusMoreInfo) return@LaunchedEffect
        repeat(20) {
            if (runCatching { moreInfoButton.requestFocus() }.isSuccess) {
                refocusMoreInfo = false
                return@LaunchedEffect
            }
            delay(50)
        }
        refocusMoreInfo = false
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val name = DisplayTitles.withoutYear(title.name, title.year)
    val cast = details?.cast?.takeIf { it.isNotEmpty() }?.joinToString(", ")
    val similar = details?.moreLikeThis.orEmpty()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().launcherBackground()) {
            // Full-screen artwork, faded on the left and at the bottom, as in the player's details.
            CinematicBackdrop(
                backdrop = title.backdropUrl,
                poster = title.posterUrl,
                modifier = Modifier.fillMaxSize(),
                delayMillis = 0,
                // Darker than the player's: the text and the whole row of buttons run further
                // across here, so the left two-thirds stay dark and the picture shows on the right.
                leftFade = 1f,
                textScrim = 0.66f,
                textScrimAlpha = 0.88f,
                fadeToTransparent = true,
            )

            // Back up to the buttons or season tabs: one smooth scroll to the top, header included.
            PivotScrolling(pivot = 110.dp, upToTopBelow = 64.dp) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, end = 48.dp, top = 44.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            // Poster, sharp, with a faint edge.
                            Box(
                                modifier = Modifier
                                    .size(width = 150.dp, height = 225.dp)
                                    .clip(WiptvShapes.card)
                                    .background(Brush.verticalGradient(listOf(WiptvColors.elevated, WiptvColors.surface)))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), WiptvShapes.card),
                            ) {
                                Text(
                                    text = name,
                                    maxLines = 4,
                                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                                    style = WiptvType.metadata,
                                )
                                if (title.posterUrl != null) {
                                    AsyncImage(
                                        model = title.posterUrl,
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = WiptvType.hero.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
                                )
                                MetaLine(
                                    items = metaItems(title, details),
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = WiptvType.metadata.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                )
                                val places = listOfNotNull("your IPTV".takeIf { iptv != null }) + title.sources.map { it.longName }
                                if (places.isNotEmpty()) {
                                    Text(
                                        text = "On " + places.dropLast(1).joinToString(", ").let { if (it.isEmpty()) "" else "$it and " } +
                                            places.last() + if (title.sources.isNotEmpty()) " in Ireland" else "",
                                        modifier = Modifier.padding(top = 4.dp),
                                        style = WiptvType.metadata.copy(color = WiptvColors.text),
                                    )
                                }
                                if (title.overview.isNotBlank()) {
                                    Text(
                                        text = title.overview,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { plotCut = it.hasVisualOverflow },
                                        modifier = Modifier.padding(top = 14.dp).widthIn(max = 540.dp),
                                        style = WiptvType.body.copy(lineHeight = 23.sp, color = WiptvColors.text.copy(alpha = 0.9f)),
                                    )
                                }

                                // Watch on your first service is the main action; the rest are quiet.
                                // When they don't all fit, the row slides along as focus moves right.
                                // The sliding row clips at its edges, so it has 8 dp of room all round
                                // for the focused button's outline and lift (shifted back so the
                                // buttons still line up with the text).
                                RowScrolling(start = 8.dp, end = 72.dp) {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .offset(x = (-8).dp)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    // Your IPTV provider first when it has the title, then your services.
                                    iptv?.let { found ->
                                        ActionButton(
                                            text = "▶  Watch on IPTV",
                                            primary = true,
                                            onClick = {
                                                History(context).add(title) // Recommended learns from it
                                                if (!Iptv.open(context, found)) {
                                                    Toast.makeText(context, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.focusRequester(primary),
                                        )
                                    }
                                    title.sources.forEachIndexed { i, source ->
                                        val first = i == 0 && iptv == null
                                        ActionButton(
                                            // "Watch on" once; a second service is just its name.
                                            text = "▶  " + (if (first) "Watch on " else "") + source.longName,
                                            primary = first,
                                            onClick = { onWatch(source) },
                                            modifier = if (first) Modifier.focusRequester(primary) else Modifier,
                                        )
                                    }
                                    if (onAnother != null) {
                                        ActionButton(text = "↻  Another one", onClick = onAnother)
                                    }
                                    details?.trailerKey?.let { key ->
                                        ActionButton(text = "Trailer", onClick = { onTrailer(key) })
                                    }
                                    ActionButton(
                                        text = if (inMyList) "✓  My List" else "+  My List",
                                        onClick = {
                                            inMyList = library.toggleMyList(title)
                                            if (inMyList) History(context).add(title)
                                            onLibraryChange()
                                        },
                                        modifier = if (title.sources.isEmpty() && iptv == null) Modifier.focusRequester(primary) else Modifier,
                                    )
                                    ActionButton(
                                        text = if (watched) "✓  Watched" else "Mark as watched",
                                        onClick = {
                                            watched = library.toggleWatched(title)
                                            if (watched) History(context).add(title)
                                            onLibraryChange()
                                        },
                                    )
                                    if (plotCut || cast != null || details?.madeBy != null) {
                                        ActionButton(
                                            text = "More info",
                                            onClick = { showMore = true },
                                            modifier = Modifier.focusRequester(moreInfoButton),
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }

                    if (similar.isNotEmpty()) {
                        item(key = "similar") {
                            Column(modifier = Modifier.padding(top = 18.dp)) {
                                Text(
                                    text = "More like this",
                                    modifier = Modifier.padding(start = 48.dp, bottom = 2.dp),
                                    style = RowHeading,
                                )
                                RowScrolling(start = 48.dp, end = 72.dp) {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 48.dp - ArtFocusGap, end = 48.dp, top = 6.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp - ArtFocusGap * 2),
                                    ) {
                                        items(similar, key = { it.key }) { other ->
                                            PosterCard(
                                                title = other,
                                                onClick = { onOpenOther(other) },
                                                onLongClick = { onPlayOther(other) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMore) {
        InfoSheet(
            title = name,
            meta = "",
            metaItems = metaItems(title, details),
            body = title.overview,
            sections = listOfNotNull(
                details?.madeBy?.let { made -> made.substringBefore(" by ") + " by" to made.substringAfter(" by ") },
                cast?.let { "Starring" to it },
            ),
            onClose = {
                showMore = false
                refocusMoreInfo = true
            },
        )
    }
}

/** "Prime Video" in full on the details page. */
private val Source.longName: String get() = if (this == Source.PRIME) "Prime Video" else label

private fun metaItems(title: Title, details: TitleDetails?): List<String> = buildList {
    add(if (title.type == MediaType.TV) "Series" else "Film")
    if (title.year.isNotBlank()) add(title.year)
    details?.length?.let { add(it) }
    val genres = details?.genres?.takeIf { it.isNotEmpty() }
        ?: title.genreIds.mapNotNull { Catalog.genreName(it) }
    if (genres.isNotEmpty()) add(genres.take(3).joinToString(", "))
    if (title.rating > 0) add("TMDB " + String.format(Locale.UK, "%.1f", title.rating))
}

package dev.tvlauncher

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
    LaunchedEffect(title.key) {
        repeat(20) {
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.background),
        ) {
            // Backdrop on the right, fading into the background behind the text.
            AsyncImage(
                model = title.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(620.dp)
                    .height(350.dp),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(620.dp)
                    .height(350.dp)
                    .background(Brush.horizontalGradient(0f to Palette.background, 0.55f to Color.Transparent)),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(620.dp)
                    .height(350.dp)
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Palette.background)),
            )

            Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .width(500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Palette.text),
                    )
                    Text(
                        text = metaLine(title, details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                    )
                    if (title.sources.isNotEmpty()) {
                        Text(
                            text = "On " + title.sources.joinToString(" and ") { if (it == Source.PRIME) "Prime Video" else it.label } + " in Ireland",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC9B6FF)),
                        )
                    }
                    Text(
                        text = title.overview,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Palette.text),
                    )
                    val people = listOfNotNull(
                        details?.madeBy,
                        details?.cast?.takeIf { it.isNotEmpty() }?.let { "Starring " + it.joinToString(", ") },
                    )
                    if (people.isNotEmpty()) {
                        Text(
                            text = people.joinToString("\n"),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = Palette.quiet),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    title.sources.forEachIndexed { i, source ->
                        val label = "▶  Watch on " + if (source == Source.PRIME) "Prime Video" else source.label
                        ActionButton(
                            text = label,
                            bright = i == 0,
                            onClick = { onWatch(source) },
                            modifier = if (i == 0) Modifier.focusRequester(primary) else Modifier,
                        )
                    }
                    if (onAnother != null) {
                        ActionButton(text = "↻  Another one", onClick = onAnother)
                    }
                    details?.trailerKey?.let { key ->
                        ActionButton(text = "Trailer", onClick = { onTrailer(key) })
                    }
                    ActionButton(
                        text = if (inMyList) "✓  In My List" else "+  My List",
                        onClick = {
                            inMyList = library.toggleMyList(title)
                            if (inMyList) History(context).add(title)
                            onLibraryChange()
                        },
                        modifier = if (title.sources.isEmpty()) Modifier.focusRequester(primary) else Modifier,
                    )
                    ActionButton(
                        text = if (watched) "✓  Watched" else "Mark as watched",
                        onClick = {
                            watched = library.toggleWatched(title)
                            if (watched) History(context).add(title)
                            onLibraryChange()
                        },
                    )
                }

                val similar = details?.moreLikeThis.orEmpty()
                if (similar.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "More like this",
                        modifier = Modifier.padding(horizontal = 40.dp),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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

private fun metaLine(title: Title, details: TitleDetails?): String = buildList {
    add(if (title.type == MediaType.TV) "Series" else "Film")
    if (title.year.isNotBlank()) add(title.year)
    details?.length?.let { add(it) }
    val genres = details?.genres?.takeIf { it.isNotEmpty() }
        ?: title.genreIds.mapNotNull { Catalog.genreName(it) }
    if (genres.isNotEmpty()) add(genres.take(3).joinToString(", "))
    if (title.rating > 0) add("TMDB " + String.format(Locale.UK, "%.1f", title.rating))
}.joinToString("  ·  ")

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bright: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (bright) Palette.glow else Color.White.copy(alpha = 0.1f),
            contentColor = if (bright) Palette.background else Palette.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Palette.glow.copy(alpha = 0.5f), elevation = 10.dp),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

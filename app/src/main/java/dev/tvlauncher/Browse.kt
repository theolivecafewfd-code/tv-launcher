package dev.tvlauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import java.util.Locale

/** Colours for the little "where to watch" tags. */
private fun Source.colours(): Pair<Color, Color> = when (this) {
    Source.NETFLIX -> Color(0xFFE5484D) to Color.White
    Source.PRIME -> Color(0xFF3EC1D3) to Color(0xFF0B1A1F)
}

/** The TV Shows | Movies switch under the header. */
@Composable
fun TypeSwitch(selected: MediaType, onSelect: (MediaType) -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 40.dp)
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MediaType.entries.forEach { type ->
            val isSelected = type == selected
            Surface(
                onClick = { onSelect(type) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Palette.glow else Color.Transparent,
                    contentColor = if (isSelected) Palette.background else Palette.text,
                    focusedContainerColor = if (isSelected) Color.White else Palette.tileFocused,
                    focusedContentColor = if (isSelected) Palette.background else Palette.text,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Text(
                    text = type.label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/**
 * The big featured title with the next two beside it. Left and right move through [titles];
 * OK opens the current one.
 */
@Composable
fun Featured(
    titles: List<Title>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onOpen: (Title) -> Unit,
) {
    val title = titles.getOrNull(index) ?: return
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            onClick = { onOpen(title) },
            modifier = Modifier
                .width(570.dp)
                .height(188.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionLeft && index > 0 -> { onIndexChange(index - 1); true }
                        event.key == Key.DirectionRight && index < titles.size - 1 -> { onIndexChange(index + 1); true }
                        else -> false
                    }
                },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Palette.tile, focusedContainerColor = Palette.tile),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Palette.glow.copy(alpha = 0.45f), elevation = 12.dp),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = title.backdropUrl,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xE60A080E))),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = title.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    )
                    Text(
                        text = metaLine(title),
                        maxLines = 1,
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFFE6E1EE)),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Watch on", style = TextStyle(fontSize = 11.sp, color = Color(0xFFE6E1EE)))
                        title.sources.forEach { SourceTag(it) }
                    }
                }
            }
        }
        titles.getOrNull(index + 1)?.let { SidePoster(it) }
        titles.getOrNull(index + 2)?.let { SidePoster(it) }
    }
}

private fun metaLine(title: Title): String = buildList {
    add(if (title.type == MediaType.TV) "Series" else "Film")
    if (title.year.isNotBlank()) add(title.year)
    title.genreIds.mapNotNull { Catalog.genreName(it) }.take(2).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    if (title.rating > 0) add("TMDB " + String.format(Locale.UK, "%.1f", title.rating))
}.joinToString("  ·  ")

@Composable
private fun SidePoster(title: Title) {
    Box(
        modifier = Modifier
            .size(width = 126.dp, height = 188.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.tile),
    ) {
        AsyncImage(
            model = title.posterUrl,
            contentDescription = title.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        title.sources.firstOrNull()?.let {
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { SourceTag(it) }
        }
    }
}

@Composable
private fun SourceTag(source: Source) {
    val (bg, fg) = source.colours()
    Text(
        text = source.label,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg),
    )
}

/** One row of posters, e.g. Comedy. Top 10 rows get a big number beside each poster. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(
    row: ContentRow,
    onOpen: (Title) -> Unit,
    requester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 40.dp, end = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = row.title,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
            )
            if (row.note.isNotBlank()) {
                Text(
                    text = row.note,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, color = Palette.quiet),
                )
            }
        }
        // The row remembers which poster you were on, and up and down always go to the row
        // directly above or below, whatever the posters' positions.
        NormalScrolling {
        LazyRow(
            modifier = Modifier
                .focusRequester(requester)
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (row.ranked) 16.dp else 12.dp),
        ) {
            itemsIndexed(row.items, key = { _, t -> t.key }) { i, title ->
                Row(verticalAlignment = Alignment.Bottom) {
                    if (row.ranked) {
                        Text(
                            text = "${i + 1}",
                            modifier = Modifier.padding(end = 2.dp),
                            style = TextStyle(
                                fontSize = 64.sp,
                                lineHeight = 64.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Palette.quiet.copy(alpha = 0.7f),
                                drawStyle = Stroke(width = 3f),
                            ),
                        )
                    }
                    PosterCard(
                        title = title,
                        onClick = { onOpen(title) },
                        modifier = Modifier.focusProperties {
                            this.up = up
                            if (down != null) this.down = down
                        },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun PosterCard(title: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 84.dp, height = 126.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Palette.tile, focusedContainerColor = Palette.tile),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Palette.glow.copy(alpha = 0.45f), elevation = 10.dp),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = title.posterUrl,
                contentDescription = title.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            title.sources.firstOrNull()?.let {
                Box(Modifier.align(Alignment.TopStart).padding(5.dp)) { SourceTag(it) }
            }
        }
    }
}

/** A quiet line of text in place of a row, e.g. while loading. */
@Composable
fun RowMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp),
        style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
    )
}

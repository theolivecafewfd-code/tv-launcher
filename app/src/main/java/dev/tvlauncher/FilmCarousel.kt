package dev.tvlauncher

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

/**
 * Genre tabs over a big featured film, with the next two films beside it.
 * Left and right on the featured film move through the list; OK opens the streaming app
 * that has it. [onBackdrop] reports the featured film's backdrop for the screen behind.
 */
@Composable
fun FilmCarousel(
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onBackdrop: (String?) -> Unit,
) {
    if (!Tmdb.hasKey) {
        Box(Modifier.padding(horizontal = 40.dp)) {
            Message("Add your TMDB key to GitHub secrets as TMDB_API_KEY to see films here.")
        }
        return
    }

    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var films by remember { mutableStateOf<List<Film>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var filmIndex by remember { mutableIntStateOf(0) }
    var providers by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(tabIndex) {
        loading = true
        failed = false
        films = try {
            Tmdb.films(Tmdb.tabs[tabIndex])
        } catch (e: Exception) {
            failed = true
            emptyList()
        }
        filmIndex = 0
        loading = false
    }

    val film = films.getOrNull(filmIndex)
    LaunchedEffect(film?.id) {
        onBackdrop(film?.backdropUrl)
        providers = emptyList()
        if (film != null) providers = Tmdb.whereToWatch(film.id)
    }

    Column(modifier = Modifier.padding(horizontal = 40.dp)) {
        GenreTabs(selected = tabIndex, onSelect = { tabIndex = it })
        Spacer(Modifier.height(10.dp))

        when {
            loading -> Message("Loading films…")
            failed -> Message("Couldn't reach TMDB. Check the internet connection.")
            film == null -> Message("No films found for this tab.")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FeaturedFilm(
                        film = film,
                        onPrevious = { if (filmIndex > 0) { filmIndex--; true } else false },
                        onNext = { if (filmIndex < films.size - 1) { filmIndex++; true } else false },
                        onClick = {
                            val match = findAppFor(providers, apps)
                            when {
                                match != null -> onLaunch(match.second)
                                providers.isEmpty() -> Toast.makeText(context, "Not sure where ${film.title} is streaming", Toast.LENGTH_SHORT).show()
                                else -> Toast.makeText(context, "${film.title} is on ${providers.joinToString()}, which isn't installed", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    films.getOrNull(filmIndex + 1)?.let { Poster(it) }
                    films.getOrNull(filmIndex + 2)?.let { Poster(it) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = film.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Palette.text),
                )
                Text(
                    text = metaLine(film, providers),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = Palette.quiet),
                )
            }
        }
    }
}

private fun metaLine(film: Film, providers: List<String>): String = buildList {
    if (film.year.isNotBlank()) add(film.year)
    if (film.rating > 0) add("TMDB " + String.format(Locale.UK, "%.1f", film.rating))
    if (providers.isNotEmpty()) add("On " + providers.take(3).joinToString(", "))
    add("Data from TMDB")
}.joinToString("  ·  ")

@Composable
private fun GenreTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tmdb.tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
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
                    text = tab.name,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FeaturedFilm(
    film: Film,
    onPrevious: () -> Boolean,
    onNext: () -> Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(520.dp)
            .height(210.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> onPrevious()
                    Key.DirectionRight -> onNext()
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.tile,
            focusedContainerColor = Palette.tile,
        ),
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
                model = film.backdropUrl,
                contentDescription = film.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color(0xD90A080E),
                        ),
                    ),
            )
            Text(
                text = film.overview,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Color.White),
            )
        }
    }
}

@Composable
private fun Poster(film: Film) {
    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.tile),
    ) {
        AsyncImage(
            model = film.posterUrl,
            contentDescription = film.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = TextStyle(fontSize = 14.sp, color = Palette.quiet))
    }
}

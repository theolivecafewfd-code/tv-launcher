package dev.wiptv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.wiptv.design.DisplayTitles
import dev.wiptv.design.Glass
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvShapes
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What the grid's side panel shows about a title, once its details have loaded. */
internal class Peek(
    val plot: String?,
    val year: String?,
    /** "1h 52m" for a film, "3 seasons" for a series. */
    val length: String?,
    val rating: Double?,
    val genres: List<String>,
    val cast: List<String>,
    val director: String?,
    val backdrop: String?,
)

/** The last few titles' details, so moving back over them shows at once (kept while the player runs). */
private object PeekCache {
    private const val KEEP = 60
    private val map = object : LinkedHashMap<String, Peek>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Peek>?) = size > KEEP
    }

    fun get(key: String): Peek? = synchronized(map) { map[key] }
    fun put(key: String, peek: Peek) = synchronized(map) { map[key] = peek }
}

private fun pills(text: String?, max: Int): List<String> =
    text.orEmpty().split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(max)

private suspend fun loadPeek(login: Login, item: VodItem): Peek = if (item.kind == VodKind.MOVIE) {
    val m = Xtream.movieDetails(login, item)
    Peek(
        plot = m.plot.ifBlank { null },
        year = m.year.ifBlank { null },
        length = m.durationMinutes?.let(::runtimeOf),
        rating = m.rating,
        genres = pills(m.genre, 3),
        cast = pills(m.cast, 4),
        director = m.director.ifBlank { null },
        backdrop = m.backdrop,
    )
} else {
    val s = Xtream.seriesDetails(login, item)
    Peek(
        plot = s.plot.ifBlank { null },
        year = s.year.ifBlank { null },
        length = s.seasons.size.takeIf { it > 0 }?.let { "$it season" + if (it == 1) "" else "s" },
        rating = s.rating,
        genres = pills(s.genre, 3),
        cast = pills(s.cast, 4),
        director = null,
        backdrop = s.backdrop,
    )
}

/**
 * The panel beside the Movies and TV Shows grids: the focused title's artwork, name, year, length
 * and score, synopsis, genres, cast and (for films) director. The details are asked for once focus
 * has rested on a poster for a moment, so moving quickly across the grid doesn't load each one;
 * what the list already knows (name, year, score) shows straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogPeek(item: VodItem?, login: Login, modifier: Modifier = Modifier) {
    var peek by remember { mutableStateOf<Peek?>(null) }
    var peekKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item?.key) {
        val current = item ?: return@LaunchedEffect
        PeekCache.get(current.key)?.let {
            peek = it
            peekKey = current.key
            return@LaunchedEffect
        }
        delay(450)
        try {
            val loaded = loadPeek(login, current)
            PeekCache.put(current.key, loaded)
            peek = loaded
            peekKey = current.key
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Just the list's own facts, then.
        }
    }
    val details = peek?.takeIf { peekKey == item?.key }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .glass(WiptvShapes.panel, Glass.Drawer)
            .clip(WiptvShapes.panel),
    ) {
        if (item == null) return@Column
        // The artwork across the top, fading into the panel.
        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val backdrop = details?.backdrop ?: item.backdrop
            if (backdrop != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(backdrop).size(600, 338).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (item.poster != null) {
                // No backdrop: the poster loaded tiny and stretched, a soft wash in its colours.
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(item.poster).size(24, 36).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.7f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to WiptvColors.panel)),
            )
        }

        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 16.dp)) {
            val year = details?.year ?: item.year?.ifBlank { null }
            Text(
                text = DisplayTitles.withoutYear(item.name, year),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = WiptvType.body.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            )
            val facts = listOfNotNull(year?.take(4), details?.length, scoreOf(details?.rating ?: item.rating))
            if (facts.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    facts.forEach { Text(text = it, style = WiptvType.metadata.copy(fontSize = 13.sp, color = WiptvColors.text, fontWeight = FontWeight.Medium)) }
                }
            }

            AnimatedVisibility(visible = details != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
                val d = details ?: return@AnimatedVisibility
                Column {
                    val plot = d.plot ?: item.plot
                    if (!plot.isNullOrBlank()) {
                        Text(
                            text = plot,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp),
                            style = WiptvType.metadata.copy(fontSize = 13.sp, lineHeight = 19.sp, color = WiptvColors.text.copy(alpha = 0.85f)),
                        )
                    }
                    PeekPills(label = "GENRES", values = d.genres)
                    PeekPills(label = "CAST", values = d.cast)
                    d.director?.let { PeekPills(label = "DIRECTOR", values = pills(it, 2)) }
                }
            }
        }
    }
}

/** A quiet label, then the values as small glass pills that wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeekPills(label: String, values: List<String>, maxWidth: Dp = 200.dp) {
    if (values.isEmpty()) return
    Text(
        text = label,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        style = WiptvType.hint.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .glass(WiptvShapes.pill, Glass.Nav)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                style = WiptvType.hint.copy(fontSize = 12.sp, color = WiptvColors.text),
            )
        }
    }
}

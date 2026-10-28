package dev.wiptv.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** What the top of a browse screen says about the focused card. */
data class Hero(
    val title: String,
    val meta: String,
    val plot: String?,
    val backdrop: String?,
    val poster: String?,
)

/**
 * The focused card's title, details and synopsis, under the header and over its artwork
 * ([CinematicBackdrop]), in both apps' TV Shows and Movies. Fixed height, so the rows below never
 * move. With no [hero], [placeholder] fills the space (a loading or error message).
 */
@Composable
fun HeroInfo(
    hero: Hero?,
    start: Dp,
    modifier: Modifier = Modifier,
    placeholder: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .padding(start = start, top = 14.dp)
            .width(470.dp)
            .height(144.dp),
    ) {
        if (hero == null) {
            placeholder()
            return@Column
        }
        var titleLines by remember { mutableIntStateOf(1) }
        Text(
            text = hero.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleLines = it.lineCount },
            style = WiptvType.hero,
        )
        if (hero.meta.isNotBlank()) {
            Text(
                text = hero.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
                style = WiptvType.metadata.copy(fontWeight = FontWeight.Medium),
            )
        }
        if (!hero.plot.isNullOrBlank()) {
            Text(
                text = hero.plot,
                maxLines = if (titleLines > 1) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
                style = WiptvType.metadata.copy(color = WiptvColors.text.copy(alpha = 0.86f)),
            )
        }
    }
}

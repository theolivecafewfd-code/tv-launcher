package dev.wiptv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.tv.material3.Text
import dev.wiptv.design.Glass
import dev.wiptv.design.ListRow
import dev.wiptv.design.WiptvColors
import dev.wiptv.design.WiptvType
import dev.wiptv.design.glass
import kotlinx.coroutines.delay
import java.util.Locale

/** One choice in the Audio & subtitles panel. [group] is null for "Off" (subtitles). */
data class TrackChoice(val label: String, val selected: Boolean, val group: Tracks.Group?, val index: Int)

/** The audio choices in what's playing, or an empty list when there's nothing to choose. */
fun audioChoices(tracks: Tracks): List<TrackChoice> = choices(tracks, C.TRACK_TYPE_AUDIO)

/** "Off" first, then the subtitles in what's playing; empty when it has none. */
fun subtitleChoices(tracks: Tracks): List<TrackChoice> {
    val found = choices(tracks, C.TRACK_TYPE_TEXT)
    if (found.isEmpty()) return emptyList()
    return listOf(TrackChoice("Off", found.none { it.selected }, null, -1)) + found
}

/** Whether the panel has anything to offer: more than one language, or any subtitles. */
fun hasTrackChoices(tracks: Tracks): Boolean = audioChoices(tracks).size > 1 || subtitleChoices(tracks).isNotEmpty()

private fun choices(tracks: Tracks, type: Int): List<TrackChoice> {
    val out = mutableListOf<TrackChoice>()
    for (group in tracks.groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                ?.let { Locale(it).displayLanguage.replaceFirstChar { c -> c.titlecase(Locale.UK) } }
            val name = format.label?.takeIf { it.isNotBlank() } ?: language ?: "Track ${out.size + 1}"
            val extra = when {
                type == C.TRACK_TYPE_AUDIO && format.channelCount >= 6 -> " · 5.1"
                type == C.TRACK_TYPE_TEXT && (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0 -> " · forced"
                else -> ""
            }
            out += TrackChoice(name + extra, group.isTrackSelected(i), group, i)
        }
    }
    return out
}

/** Plays [choice]: an audio language, a subtitle language, or subtitles off. */
fun choose(player: Player, choice: TrackChoice, type: Int) {
    val params = player.trackSelectionParameters.buildUpon()
    val group = choice.group
    if (group == null) {
        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        params.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.index))
    }
    player.trackSelectionParameters = params.build()
}

/**
 * Audio & subtitles, over the right of the picture (Down while watching opens it): the languages
 * the file has and its subtitles, the playing ones ticked. OK picks one; Back closes it.
 */
@Composable
fun TracksPanel(
    audio: List<TrackChoice>,
    subtitles: List<TrackChoice>,
    onAudio: (TrackChoice) -> Unit,
    onSubtitles: (TrackChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    Column(
        modifier = modifier
            .width(320.dp)
            .glass(RoundedCornerShape(18.dp), Glass.OverVideo)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var firstUsed = false
        fun firstModifier(): Modifier = if (!firstUsed) Modifier.focusRequester(first).also { firstUsed = true } else Modifier
        if (audio.size > 1) {
            Heading("Audio")
            audio.forEach { choice -> ChoiceRow(choice, firstModifier()) { onAudio(choice) } }
        }
        if (subtitles.isNotEmpty()) {
            Heading("Subtitles")
            subtitles.forEach { choice -> ChoiceRow(choice, firstModifier()) { onSubtitles(choice) } }
        }
        Text("Back to close", style = WiptvType.hint, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(text = text, style = WiptvType.hint, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp))
}

/** A language in the panel: the shared list row, ✓ on the one playing. */
@Composable
private fun ChoiceRow(choice: TrackChoice, modifier: Modifier, onClick: () -> Unit) {
    ListRow(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) { focused ->
        Text(
            text = (if (choice.selected) "✓  " else "     ") + choice.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = WiptvType.metadata.copy(
                color = if (focused || choice.selected) WiptvColors.text else WiptvColors.secondary,
                fontWeight = if (choice.selected) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

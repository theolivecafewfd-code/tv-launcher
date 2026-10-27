package dev.wiptv.player

/**
 * Episode titles for display. Many providers send "Harbour Lights - S01E01 - The Stolen Eagle";
 * on the series page that shows as "1 · The Stolen Eagle". The provider's title is kept as it is
 * in [Episode.title]; this only changes what's shown.
 *
 * Only removed when it's safe: the series name when a separator or episode code follows it
 * (so "Lost in Space" in a series called "Lost" is left alone); a full code like S01E01 or 1x01
 * at the start; a looser one like "Episode 4" only when its number matches this episode.
 */
internal object EpisodeTitles {
    private const val SEPARATORS = " -–—:|._"
    private val CODE = Regex(
        "^(?:S(\\d{1,3})\\s*[ .]?E(\\d{1,4})|(\\d{1,3})x(\\d{1,4})|(?:Season\\s*(\\d{1,3})\\s*)?Episode\\s*(\\d{1,4})|Ep\\.?\\s*(\\d{1,4})|E(\\d{1,4}))\\b",
        RegexOption.IGNORE_CASE,
    )

    /** The title without the series name or episode code, or null when nothing else is left. */
    fun clean(raw: String, seriesName: String, season: Int, number: Int): String? {
        var title = raw.trim()
        val name = seriesName.trim()
        if (name.isNotEmpty() && title.startsWith(name, ignoreCase = true)) {
            val rest = title.substring(name.length)
            val next = rest.trimStart()
            val separated = rest.isEmpty() ||
                rest.first() in "-–—:|" ||
                (rest.first() == ' ' && (next.firstOrNull()?.let { it in "-–—:|" } == true || CODE.find(next) != null))
            if (separated) title = rest.trimStart(*SEPARATORS.toCharArray())
        }
        CODE.find(title)?.let { match ->
            val g = match.groupValues
            val season2 = g[1].ifEmpty { g[3] }.ifEmpty { g[5] }.toIntOrNull()
            val number2 = g[2].ifEmpty { g[4] }.ifEmpty { g[6] }.ifEmpty { g[7] }.ifEmpty { g[8] }.toIntOrNull()
            // A full code (S01E01, 1x01) is clearly a code even when the provider's numbering is
            // off by one, so it always goes; "Episode 4" or "E4" only when it's this episode.
            val fullCode = g[1].isNotEmpty() || g[3].isNotEmpty()
            if (fullCode || (number2 == number && (season2 == null || season2 == season))) {
                title = title.substring(match.range.last + 1).trimStart(*SEPARATORS.toCharArray())
            }
        }
        return title.ifBlank { null }
    }

    /** "1 · The Stolen Eagle", or "Episode 4" when the provider gave no real title. */
    fun display(episode: Episode, seriesName: String): String =
        clean(episode.title, seriesName, episode.season, episode.number)?.let { "${episode.number} · $it" }
            ?: "Episode ${episode.number}"

    /** The name alone ("The Stolen Eagle"), or "Episode 4". */
    fun name(episode: Episode, seriesName: String): String =
        clean(episode.title, seriesName, episode.season, episode.number) ?: "Episode ${episode.number}"
}

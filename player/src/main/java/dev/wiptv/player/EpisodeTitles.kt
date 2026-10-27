package dev.wiptv.player

/**
 * Episode titles for display. Many providers send "Harbour Lights - S01E01 - The Stolen Eagle";
 * on the series page that shows as "1 · The Stolen Eagle". The provider's title is kept as it is
 * in [Episode.title]; this only changes what's shown.
 *
 * Only removed when it's safe: the series name when a separator or episode code follows it
 * (so "Lost in Space" in a series called "Lost" is left alone), and an episode code only when its
 * numbers match this episode.
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
            if (number2 == number && (season2 == null || season2 == season)) {
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

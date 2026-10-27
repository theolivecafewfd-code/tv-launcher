package dev.wiptv.player

/**
 * Film and series names for display. Providers often add the year to the name ("Predator (1987)"),
 * which then shows twice next to the year in the details line. The provider's name is kept as it is.
 */
object VodTitles {
    // The year in brackets, or after a dash or bar, at the very end. A bare "Blade Runner 2049"
    // is never touched: there the number is part of the name.
    private val trailingYear = Regex("""^(.*?\S)\s*(?:\((\d{4})\)|\[(\d{4})\]|[-–|]\s*(\d{4}))\s*$""")

    /** [name] without a trailing year when it matches [year], the one shown in the details line. */
    fun withoutYear(name: String, year: String?): String {
        if (year.isNullOrBlank()) return name
        val match = trailingYear.matchEntire(name) ?: return name
        val found = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }
        val rest = match.groupValues[1].trimEnd(' ', '-', '–', '|', ':')
        if (found != year || rest.none { it.isLetterOrDigit() }) return name
        return rest
    }
}

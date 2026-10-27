package dev.wiptv.player

/** A channel's name for display, with a trailing picture-quality tag (FHD, HD, SD…) split off as a badge. */
data class ChannelLabel(val name: String, val quality: String?)

/**
 * Splits a reliable trailing quality tag off a provider's channel name, so "Sky Sports News | FHD |"
 * shows as "Sky Sports News" with an FHD badge. Only for display: the channel itself is untouched.
 * The tag must be in capitals, at the very end, and set apart by a space, bar, dash or brackets;
 * anything else ("SKYHD", "News HD (backup)", a name that is only "HD") is left as it is.
 */
object ChannelLabels {
    private val trailingQuality = Regex(
        """^(.*?[\p{L}\p{N}+)\]!'.&])(?:\s*[|:\-]\s*|\s*[(\[]\s*|\s+)(FHD|UHD|HD|SD|4K)\s*[)\]]?(?:\s*\|)*\s*$""",
    )
    private val trailingSeparators = Regex("""[\s|:\-]+$""")

    fun of(name: String): ChannelLabel {
        val match = trailingQuality.matchEntire(name) ?: return ChannelLabel(name, null)
        val clean = match.groupValues[1].replace(trailingSeparators, "")
        if (clean.none { it.isLetterOrDigit() }) return ChannelLabel(name, null)
        return ChannelLabel(clean, match.groupValues[2])
    }
}

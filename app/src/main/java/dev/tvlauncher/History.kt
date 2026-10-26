package dev.tvlauncher

import android.content.Context

/** A show or film you opened from the launcher, used for "Recommended for you". */
data class HistoryEntry(val type: MediaType, val id: Int, val genreIds: List<Int>)

/** The last 30 titles you opened from the launcher, newest first, saved on the device. */
class History(context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun entries(): List<HistoryEntry> =
        prefs.getString(KEY, "").orEmpty().split(';').mapNotNull { parse(it) }

    fun add(title: Title) {
        val entry = HistoryEntry(title.type, title.id, title.genreIds)
        val updated = (listOf(entry) + entries().filterNot { it.type == entry.type && it.id == entry.id }).take(30)
        prefs.edit().putString(KEY, updated.joinToString(";") { format(it) }).apply()
    }

    private fun format(e: HistoryEntry) = "${e.type.path}:${e.id}:${e.genreIds.joinToString(",")}"

    private fun parse(raw: String): HistoryEntry? {
        val parts = raw.split(':')
        if (parts.size != 3) return null
        val type = MediaType.entries.firstOrNull { it.path == parts[0] } ?: return null
        val id = parts[1].toIntOrNull() ?: return null
        val genres = parts[2].split(',').mapNotNull { it.toIntOrNull() }
        return HistoryEntry(type, id, genres)
    }

    companion object {
        private const val KEY = "entries"
    }
}

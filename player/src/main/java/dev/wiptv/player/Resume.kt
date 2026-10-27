package dev.wiptv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Something you've started watching: a film, or an episode of a series. Kept so Continue watching
 * can pick up where you stopped. [seriesId] is set for episodes, so the row can show the series.
 */
data class ResumePoint(
    /** "movie:<id>" or "episode:<id>". */
    val key: String,
    val title: String,
    /** For episodes, like "S2 E5 · The Long Night". */
    val subtitle: String?,
    val poster: String?,
    val url: String,
    val position: Long,
    val duration: Long,
    val updated: Long,
    val seriesId: Long? = null,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * Playback positions and favourite films and series, kept on the TV. Positions are saved while
 * you watch; once you're within the last few minutes the item counts as watched and leaves
 * Continue watching.
 */
class VodStore(context: Context) {
    private val prefs = context.getSharedPreferences("vod", Context.MODE_PRIVATE)

    fun resumePoints(): List<ResumePoint> = runCatching {
        val array = JSONArray(prefs.getString("resume", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ResumePoint(
                key = o.getString("key"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                poster = o.optString("poster").takeIf { it.isNotBlank() },
                url = o.getString("url"),
                position = o.optLong("position"),
                duration = o.optLong("duration"),
                updated = o.optLong("updated"),
                seriesId = o.optLong("seriesId", -1).takeIf { it >= 0 },
            )
        }.sortedByDescending { it.updated }
    }.getOrDefault(emptyList())

    fun resumePoint(key: String): ResumePoint? = resumePoints().firstOrNull { it.key == key }

    /** Saves where you are, or clears it once you're near the end (or barely started). */
    fun savePosition(point: ResumePoint) {
        val nearEnd = point.duration > 0 && point.duration - point.position < 3 * 60_000
        val barelyStarted = point.position < 60_000
        val others = resumePoints().filterNot { it.key == point.key || (point.seriesId != null && it.seriesId == point.seriesId) }
        val kept = if (nearEnd || barelyStarted) others else listOf(point) + others
        if (nearEnd) markWatched(point.key)
        write(kept.take(30))
    }

    fun watched(): Set<String> = prefs.getStringSet("watched", emptySet())?.toSet() ?: emptySet()

    private fun markWatched(key: String) {
        prefs.edit().putStringSet("watched", watched() + key).apply()
    }

    fun favourites(): List<VodItem> = runCatching {
        val array = JSONArray(prefs.getString("favourites", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VodItem(
                kind = VodKind.valueOf(o.getString("kind")),
                id = o.getLong("id"),
                name = o.optString("name"),
                poster = o.optString("poster").takeIf { it.isNotBlank() },
                categoryId = o.optString("categoryId"),
                rating = o.optDouble("rating").takeIf { !it.isNaN() },
                extension = o.optString("extension").takeIf { it.isNotBlank() },
                plot = o.optString("plot").takeIf { it.isNotBlank() },
                backdrop = o.optString("backdrop").takeIf { it.isNotBlank() },
                year = o.optString("year").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    /** Adds the film or series to My List, or takes it off; returns true when it was added. */
    fun toggleFavourite(item: VodItem): Boolean {
        val current = favourites()
        val added = current.none { it.key == item.key }
        val next = if (added) listOf(item) + current else current.filterNot { it.key == item.key }
        val array = JSONArray()
        for (f in next) {
            val o = JSONObject()
                .put("kind", f.kind.name)
                .put("id", f.id)
                .put("name", f.name)
                .put("poster", f.poster ?: "")
                .put("categoryId", f.categoryId)
                .put("extension", f.extension ?: "")
                .put("plot", f.plot ?: "")
                .put("backdrop", f.backdrop ?: "")
                .put("year", f.year ?: "")
            // JSON can't hold "no number", so a missing score is simply left out.
            f.rating?.let { o.put("rating", it) }
            array.put(o)
        }
        prefs.edit().putString("favourites", array.toString()).apply()
        return added
    }

    private fun write(points: List<ResumePoint>) {
        val array = JSONArray()
        for (p in points) {
            array.put(
                JSONObject()
                    .put("key", p.key)
                    .put("title", p.title)
                    .put("subtitle", p.subtitle ?: "")
                    .put("poster", p.poster ?: "")
                    .put("url", p.url)
                    .put("position", p.position)
                    .put("duration", p.duration)
                    .put("updated", p.updated)
                    .put("seriesId", p.seriesId ?: -1),
            )
        }
        prefs.edit().putString("resume", array.toString()).apply()
    }
}

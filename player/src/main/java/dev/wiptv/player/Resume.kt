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
    /**
     * Where it plays from. Never saved: it holds the sign-in, so a saved point keeps only its
     * [extension] and [withUrl] rebuilds the address from the current (encrypted) login.
     */
    val url: String,
    val position: Long,
    val duration: Long,
    val updated: Long,
    val seriesId: Long? = null,
    /** Wide artwork for Continue watching: the episode's still or the film's backdrop. */
    val image: String? = null,
    /** The file type ("mkv", "mp4"), for rebuilding [url]. */
    val extension: String? = null,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    /** This point with its address built from [login], for a film or episode saved without one. */
    fun withUrl(login: Login): ResumePoint {
        val ext = extension ?: return this
        val id = key.substringAfter(':').toLongOrNull() ?: return this
        return when {
            key.startsWith("movie:") -> copy(url = Xtream.movieUrl(login, id, ext))
            key.startsWith("episode:") -> copy(url = Xtream.episodeUrl(login, id, ext))
            else -> this
        }
    }
}

/** The file type at the end of an older saved address ("…/123.mkv" → "mkv"). */
private fun extensionOf(url: String): String? =
    url.substringAfterLast('/').substringAfterLast('.', "").takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) }

/**
 * Playback positions and favourite films and series, kept on the TV. Positions are saved while
 * you watch; once you're within the last few minutes the item counts as watched and leaves
 * Continue watching.
 */
class VodStore(context: Context) {
    companion object {
        /**
         * Changes read the whole list and write it back, from the player and from the launcher
         * (through CatalogProvider) at once, so they take turns: two at the same moment would
         * otherwise lose one of them.
         */
        private val lock = Any()
    }

    private val prefs = context.getSharedPreferences("vod", Context.MODE_PRIVATE)

    // Read under the lock too: the one-off clean-up below rewrites what it read, and a save
    // landing in between would be lost.
    fun resumePoints(): List<ResumePoint> = synchronized(lock) { readResumePoints() }

    private fun readResumePoints(): List<ResumePoint> = runCatching {
        val array = JSONArray(prefs.getString("resume", "[]"))
        var hadAddresses = false
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            // Older builds saved the whole address, sign-in included: keep only its file type.
            val oldUrl = o.optString("url")
            if (oldUrl.isNotBlank()) hadAddresses = true
            ResumePoint(
                key = o.getString("key"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                poster = o.optString("poster").takeIf { it.isNotBlank() },
                url = "",
                position = o.optLong("position"),
                duration = o.optLong("duration"),
                updated = o.optLong("updated"),
                seriesId = o.optLong("seriesId", -1).takeIf { it >= 0 },
                image = o.optString("image").takeIf { it.isNotBlank() },
                extension = o.optString("ext").takeIf { it.isNotBlank() } ?: extensionOf(oldUrl),
            )
        }.sortedByDescending { it.updated }.also { points ->
            // Rewrite them once without the addresses.
            if (hadAddresses) write(points)
        }
    }.getOrDefault(emptyList())

    fun resumePoint(key: String): ResumePoint? = resumePoints().firstOrNull { it.key == key }

    /** Saves where you are, or clears it once you're near the end (or a film barely started). */
    fun savePosition(point: ResumePoint) {
        synchronized(lock) {
            val nearEnd = point.duration > 0 && point.duration - point.position < 3 * 60_000
            val barelyStarted = point.position < 60_000
            val others = resumePoints().filterNot { it.key == point.key || (point.seriesId != null && it.seriesId == point.seriesId) }
            // An episode is kept even when barely started, so a series stays in Continue
            // watching after one episode ends and the next has only just begun (Play Next).
            // A film barely started still isn't kept.
            val dropped = nearEnd || (barelyStarted && point.seriesId == null)
            val kept = if (dropped) others else listOf(point) + others
            if (nearEnd) markWatched(point.key)
            write(kept.take(30))
        }
    }

    fun watched(): Set<String> = prefs.getStringSet("watched", emptySet())?.toSet() ?: emptySet()

    private fun markWatched(key: String) {
        synchronized(lock) {
            prefs.edit().putStringSet("watched", watched() + key).apply()
        }
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
        synchronized(lock) {
            val current = favourites()
            val added = current.none { it.key == item.key }
            writeFavourites(if (added) listOf(item) + current else current.filterNot { it.key == item.key })
            return added
        }
    }

    /** Puts it at the front of My List, if it isn't there already (from the launcher). */
    fun addFavourite(item: VodItem) {
        synchronized(lock) {
            val current = favourites()
            if (current.none { it.key == item.key }) writeFavourites(listOf(item) + current)
        }
    }

    /** Replaces its My List entry, where it is, with this fuller one (details found later). */
    fun fillInFavourite(item: VodItem) {
        synchronized(lock) {
            val current = favourites()
            if (current.none { it.key == item.key }) return
            writeFavourites(current.map { if (it.key == item.key) item else it })
        }
    }

    /** Takes it off My List; returns true when it was there. */
    fun removeFavourite(key: String): Boolean {
        synchronized(lock) {
            val current = favourites()
            val next = current.filterNot { it.key == key }
            if (next.size == current.size) return false
            writeFavourites(next)
            return true
        }
    }

    private fun writeFavourites(next: List<VodItem>) {
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
                    .put("ext", p.extension ?: extensionOf(p.url) ?: "")
                    .put("position", p.position)
                    .put("duration", p.duration)
                    .put("updated", p.updated)
                    .put("seriesId", p.seriesId ?: -1)
                    .put("image", p.image ?: ""),
            )
        }
        prefs.edit().putString("resume", array.toString()).apply()
    }
}

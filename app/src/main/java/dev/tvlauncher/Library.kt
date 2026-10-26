package dev.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Your My List and the titles you've marked as watched, saved on the device. My List keeps
 * enough about each title to show its row without going online.
 */
class Library(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun myList(): List<Title> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LIST, "[]"))
        (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun isInMyList(title: Title): Boolean = myList().any { it.key == title.key }

    /** Adds or removes [title]; returns true when it's now in My List. */
    fun toggleMyList(title: Title): Boolean {
        val list = myList()
        val inList = list.any { it.key == title.key }
        val updated = if (inList) list.filterNot { it.key == title.key } else listOf(title) + list
        prefs.edit().putString(KEY_LIST, JSONArray(updated.map { toJson(it) }).toString()).apply()
        return !inList
    }

    fun isWatched(title: Title): Boolean = title.key in watched()

    /** Marks or unmarks [title] as watched; returns true when it's now watched. */
    fun toggleWatched(title: Title): Boolean {
        val set = watched()
        val nowWatched = title.key !in set
        val updated = if (nowWatched) set + title.key else set - title.key
        prefs.edit().putStringSet(KEY_WATCHED, updated).apply()
        return nowWatched
    }

    fun watched(): Set<String> = prefs.getStringSet(KEY_WATCHED, emptySet())?.toSet() ?: emptySet()

    private fun toJson(t: Title) = JSONObject().apply {
        put("id", t.id)
        put("type", t.type.path)
        put("name", t.name)
        put("year", t.year)
        put("overview", t.overview)
        put("rating", t.rating)
        put("genres", JSONArray(t.genreIds))
        put("backdrop", t.backdropUrl ?: "")
        put("poster", t.posterUrl ?: "")
        put("sources", JSONArray(t.sources.map { it.name }))
    }

    private fun fromJson(o: JSONObject): Title? {
        val type = MediaType.entries.firstOrNull { it.path == o.optString("type") } ?: return null
        val genres = o.optJSONArray("genres")
        val sources = o.optJSONArray("sources")
        return Title(
            id = o.optInt("id"),
            type = type,
            name = o.optString("name"),
            year = o.optString("year"),
            overview = o.optString("overview"),
            rating = o.optDouble("rating", 0.0),
            popularity = 0.0,
            genreIds = if (genres == null) emptyList() else (0 until genres.length()).map { genres.getInt(it) },
            backdropUrl = o.optString("backdrop").ifBlank { null },
            posterUrl = o.optString("poster").ifBlank { null },
            sources = if (sources == null) emptyList() else (0 until sources.length()).mapNotNull { i ->
                Source.entries.firstOrNull { it.name == sources.getString(i) }
            },
        )
    }

    companion object {
        private const val KEY_LIST = "my_list"
        private const val KEY_WATCHED = "watched"
    }
}

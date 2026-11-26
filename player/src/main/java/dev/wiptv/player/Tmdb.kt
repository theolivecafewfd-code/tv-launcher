package dev.wiptv.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A film or series that's trending on TMDB this week. */
data class TrendingTitle(
    val tmdbId: Long,
    val kind: VodKind,
    val name: String,
    val year: String?,
    val overview: String,
    val rating: Double,
    val genres: List<String>,
    val backdrop: String?,
    val poster: String?,
    /** Its place in this week's trending list for its kind (1 is the most popular). */
    val rank: Int,
)

/**
 * The little the player asks TMDB for: what's trending this week (Library's banner). The key
 * comes from the TMDB_API_KEY GitHub secret at build time and is never in the code; without it
 * the banner simply doesn't show.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"

    val hasKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    /** This week's trending films ([VodKind.MOVIE]) or series, most popular first. */
    suspend fun trending(kind: VodKind): List<TrendingTitle> = withContext(Dispatchers.IO) {
        val path = if (kind == VodKind.MOVIE) "movie" else "tv"
        val json = JSONObject(get("$API/trending/$path/week?language=en-GB"))
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val item = results.optJSONObject(i) ?: return@mapNotNull null
            val genreIds = item.optJSONArray("genre_ids")
            TrendingTitle(
                tmdbId = item.optLong("id"),
                kind = kind,
                name = item.optString(if (kind == VodKind.MOVIE) "title" else "name"),
                year = item.optString(if (kind == VodKind.MOVIE) "release_date" else "first_air_date").take(4).ifBlank { null },
                overview = item.optString("overview"),
                rating = item.optDouble("vote_average", 0.0),
                genres = if (genreIds == null) emptyList() else (0 until genreIds.length()).mapNotNull { GENRES[genreIds.optInt(it)] }.distinct().take(2),
                backdrop = image(item, "backdrop_path", "w1280"),
                poster = image(item, "poster_path", "w342"),
                rank = i + 1,
            )
        }.filter { it.name.isNotBlank() && it.backdrop != null }
    }

    private fun image(item: JSONObject, field: String, size: String): String? {
        if (item.isNull(field)) return null
        val path = item.optString(field)
        return if (path.isBlank()) null else "$IMAGES/$size$path"
    }

    private fun get(url: String): String {
        val key = URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")
        val connection = (URL("$url&api_key=$key").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WIPTVPlayer/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) error("TMDB answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private val GENRES = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
        53 to "Thriller", 10752 to "War", 37 to "Western", 10759 to "Action", 10762 to "Kids",
        10765 to "Sci-Fi", 10768 to "War", 10764 to "Reality", 10766 to "Soap", 10767 to "Talk", 10763 to "News",
    )
}

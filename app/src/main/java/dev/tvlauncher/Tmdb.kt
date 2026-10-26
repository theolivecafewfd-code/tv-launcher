package dev.tvlauncher

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A film shown in the carousel. */
data class Film(
    val id: Int,
    val title: String,
    val year: String,
    val overview: String,
    /** TMDB's average user score out of 10. */
    val rating: Double,
    val backdropUrl: String?,
    val posterUrl: String?,
)

/** A tab above the carousel. [tmdbGenreId] null means "Explore": popular across all genres. */
data class GenreTab(val name: String, val tmdbGenreId: Int?)

/**
 * Film data from The Movie Database (TMDB). Every list is limited to films you can stream on a
 * subscription service in Ireland, so the carousel only suggests things you can actually watch.
 *
 * This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
object Tmdb {

    private const val TAG = "TvLauncherTmdb"
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"
    private const val REGION = "IE"
    private const val CACHE_MILLIS = 6 * 60 * 60_000L

    val tabs = listOf(
        GenreTab("Explore", null),
        GenreTab("Horror", 27),
        GenreTab("Mystery", 9648),
        GenreTab("Thriller", 53),
        GenreTab("Sci-Fi", 878),
        GenreTab("Comedy", 35),
    )

    /** False when the build has no TMDB key (the GitHub secret is missing). */
    val hasKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    private class Cached<T>(val value: T, val at: Long)

    private val filmCache = ConcurrentHashMap<String, Cached<List<Film>>>()
    private val providerCache = ConcurrentHashMap<Int, List<String>>()

    /** Popular films in a tab, streamable in Ireland. Cached for six hours. */
    suspend fun films(tab: GenreTab): List<Film> = withContext(Dispatchers.IO) {
        val key = tab.tmdbGenreId?.toString() ?: "explore"
        filmCache[key]?.takeIf { SystemClock.elapsedRealtime() - it.at < CACHE_MILLIS }?.let {
            return@withContext it.value
        }
        val params = buildString {
            append("sort_by=popularity.desc")
            append("&watch_region=$REGION")
            append("&with_watch_monetization_types=flatrate")
            append("&vote_count.gte=150")
            append("&include_adult=false")
            append("&language=en-GB")
            tab.tmdbGenreId?.let { append("&with_genres=$it") }
        }
        val json = JSONObject(get("$API/discover/movie?$params"))
        val results = json.getJSONArray("results")
        val films = (0 until results.length())
            .map { results.getJSONObject(it) }
            .filter { !it.isNull("backdrop_path") }
            .take(12)
            .map { item ->
                Film(
                    id = item.getInt("id"),
                    title = item.optString("title"),
                    year = item.optString("release_date").take(4),
                    overview = item.optString("overview"),
                    rating = item.optDouble("vote_average", 0.0),
                    backdropUrl = item.optString("backdrop_path").takeIf { it.isNotBlank() }?.let { "$IMAGES/w780$it" },
                    posterUrl = item.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$IMAGES/w342$it" },
                )
            }
        filmCache[key] = Cached(films, SystemClock.elapsedRealtime())
        films
    }

    /** Subscription services that carry a film in Ireland, e.g. ["Netflix", "Disney Plus"]. */
    suspend fun whereToWatch(filmId: Int): List<String> = withContext(Dispatchers.IO) {
        providerCache[filmId]?.let { return@withContext it }
        val names = try {
            val json = JSONObject(get("$API/movie/$filmId/watch/providers?"))
            val flatrate = json.optJSONObject("results")?.optJSONObject(REGION)?.optJSONArray("flatrate")
            if (flatrate == null) emptyList() else (0 until flatrate.length()).map {
                flatrate.getJSONObject(it).getString("provider_name")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Where-to-watch failed for $filmId", e)
            emptyList()
        }
        providerCache[filmId] = names
        names
    }

    private fun get(url: String): String {
        val key = URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")
        val separator = if (url.endsWith("?")) "" else "&"
        val connection = (URL("$url${separator}api_key=$key").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("TMDB answered $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Finds the installed app for a streaming service TMDB names, e.g. "Amazon Prime Video" →
 * the Prime Video app. Returns null when none of the services is installed.
 */
fun findAppFor(providers: List<String>, apps: List<TvApp>): Pair<String, TvApp>? {
    for (provider in providers) {
        val name = provider.lowercase()
        val keyword = when {
            name.startsWith("amazon") || name.contains("prime video") -> "prime video"
            name.startsWith("now") -> "now"
            name.startsWith("disney") -> "disney"
            name.startsWith("apple tv") -> "apple tv"
            name.startsWith("paramount") -> "paramount"
            else -> name.substringBefore(' ')
        }
        val app = apps.firstOrNull { it.label.lowercase().startsWith(keyword) }
        if (app != null) return provider to app
    }
    return null
}

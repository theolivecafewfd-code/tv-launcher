package dev.tvlauncher

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class MediaType(val path: String, val label: String) {
    TV("tv", "TV Shows"),
    MOVIE("movie", "Movies"),
}

/**
 * Where you can watch something, in your order of preference: the first one listed is what OK
 * opens. Your IPTV player will join at the front once it exists.
 * [providerIds] are TMDB's ids for the service ("|" means any of them).
 */
enum class Source(val label: String, val providerIds: String) {
    NETFLIX("Netflix", "8"),
    PRIME("Prime", "9|119|2100"),
}

/** A show or film in the rows. */
data class Title(
    val id: Int,
    val type: MediaType,
    val name: String,
    val year: String,
    val overview: String,
    /** TMDB's average user score out of 10. */
    val rating: Double,
    val popularity: Double,
    val genreIds: List<Int>,
    val backdropUrl: String?,
    val posterUrl: String?,
    /** Services that have it, in your order of preference. */
    val sources: List<Source>,
    /** Set for an entry from the player's My List: it opens in the player. */
    val iptv: IptvTitle? = null,
) {
    val key: String get() = if (iptv != null) "iptv:${type.path}:${iptv.id}" else "${type.path}:$id"
}

/** One row on the home screen, e.g. "Top 10 this week". */
data class ContentRow(
    val key: String,
    val title: String,
    val note: String,
    /** Top 10 style: a big number beside each poster. */
    val ranked: Boolean,
    val items: List<Title>,
)

/** Everything on a title's details page beyond what the rows already know. */
data class TitleDetails(
    /** e.g. "2h 5m" for a film, "3 seasons" for a show. */
    val length: String?,
    val genres: List<String>,
    /** "Directed by …" for films, "Created by …" for shows. */
    val madeBy: String?,
    val cast: List<String>,
    /** YouTube video id of the official trailer, if there is one. */
    val trailerKey: String?,
    /** Similar titles on your services. */
    val moreLikeThis: List<Title>,
)

/**
 * Shows and films from The Movie Database (TMDB), limited to what's on your services in Ireland.
 * This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
object Catalog {

    private const val TAG = "TvLauncherCatalog"
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"
    private const val REGION = "IE"
    private const val CACHE_MILLIS = 6 * 60 * 60_000L

    private const val COMEDY = 35
    private const val DOCUMENTARY = 99

    /** False when the build has no TMDB key (the GitHub secret is missing). */
    val hasKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    private class Cached<T>(val value: T, val at: Long)

    private val rowCache = ConcurrentHashMap<String, Cached<ContentRow>>()
    private val netflixIds = ConcurrentHashMap<String, String>()

    /** The four home-screen rows for TV shows or films. */
    suspend fun rows(type: MediaType): List<ContentRow> = coroutineScope {
        listOf(
            async { cached("${type.path}:top") { topTen(type) } },
        ).awaitAll().filter { it.items.isNotEmpty() }
    }

    /** Recommended for you, loaded on its own (it takes longer, and changes with what you open). */
    suspend fun recommendedRow(type: MediaType, history: List<HistoryEntry>): ContentRow {
        val ofType = history.filter { it.type == type }
        val signature = ofType.take(10).joinToString(",") { it.id.toString() }
        return cached("${type.path}:recommended:$signature") { recommended(type, ofType) }
    }

    private suspend fun cached(key: String, load: suspend () -> ContentRow): ContentRow {
        rowCache[key]?.takeIf { SystemClock.elapsedRealtime() - it.at < CACHE_MILLIS }?.let { return it.value }
        val row = load()
        if (row.items.isNotEmpty()) rowCache[key] = Cached(row, SystemClock.elapsedRealtime())
        return row
    }

    /**
     * New and popular: the most popular shows that started in the last 18 months, or films
     * released in the last year, on your services. (Plain popularity filled the row with
     * long-running shows from years ago.)
     */
    private suspend fun topTen(type: MediaType): ContentRow {
        val today = java.time.LocalDate.now()
        val recent = when (type) {
            MediaType.TV -> "first_air_date.gte=${today.minusMonths(18)}&first_air_date.lte=$today"
            MediaType.MOVIE -> "primary_release_date.gte=${today.minusMonths(12)}&primary_release_date.lte=$today"
        }
        val items = merged(type, "sort_by=popularity.desc&vote_count.gte=20&$recent", byRating = false)
            .filter { it.backdropUrl != null }
            .take(10)
        return ContentRow("top", "Top 10 this week", "Most popular on your services in Ireland", ranked = true, items = items)
    }

    private suspend fun genreRow(type: MediaType, key: String, title: String, genre: Int): ContentRow {
        val minVotes = if (genre == DOCUMENTARY) 30 else 100
        val items = merged(type, "sort_by=popularity.desc&vote_count.gte=$minVotes&with_genres=$genre", byRating = false)
            .take(15)
        return ContentRow(key, title, "", ranked = false, items = items)
    }

    /**
     * Built from what you've opened in the launcher: the genres you pick most, best rated first.
     * Before there's any history, it's simply the top rated titles on your services.
     */
    private suspend fun recommended(type: MediaType, history: List<HistoryEntry>): ContentRow {
        val seen = history.map { it.id }.toSet()
        val favouriteGenres = history
            .flatMap { it.genreIds }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }

        return if (favouriteGenres.isEmpty()) {
            val items = merged(type, "sort_by=vote_average.desc&vote_count.gte=1000", byRating = true).take(15)
            ContentRow("recommended", "Recommended for you", "Top rated on your services for now. It learns from what you open.", false, items)
        } else {
            val items = merged(
                type,
                "sort_by=vote_average.desc&vote_count.gte=300&with_genres=${favouriteGenres.joinToString("|")}",
                byRating = true,
            ).filter { it.id !in seen }.take(15)
            val because = favouriteGenres.mapNotNull { genreName(it) }.joinToString(" and ")
            ContentRow("recommended", "Recommended for you", "Because you watch $because", false, items)
        }
    }

    /**
     * Runs the same search once per service, so each title knows which services have it,
     * then merges the results.
     */
    private suspend fun merged(type: MediaType, params: String, byRating: Boolean): List<Title> = coroutineScope {
        val perSource = Source.entries.map { source ->
            async { source to runCatching { discover(type, params, source) }.getOrElse { e ->
                Log.w(TAG, "Discover failed for ${source.label}", e)
                emptyList()
            } }
        }.awaitAll()

        val byId = LinkedHashMap<Int, Title>()
        for ((source, titles) in perSource) {
            for (title in titles) {
                val existing = byId[title.id]
                byId[title.id] = if (existing == null) {
                    title.copy(sources = listOf(source))
                } else {
                    existing.copy(sources = (existing.sources + source).distinct().sortedBy { it.ordinal })
                }
            }
        }
        if (perSource.all { it.second.isEmpty() } && Source.entries.isNotEmpty()) {
            error("Couldn't load titles from TMDB")
        }
        val list = byId.values.toList()
        if (byRating) list.sortedByDescending { it.rating } else list.sortedByDescending { it.popularity }
    }

    private suspend fun discover(type: MediaType, params: String, source: Source): List<Title> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append(params)
                append("&watch_region=$REGION")
                append("&with_watch_providers=").append(URLEncoder.encode(source.providerIds, "UTF-8"))
                append("&with_watch_monetization_types=flatrate")
                append("&include_adult=false")
                append("&language=en-GB")
            }
            val json = JSONObject(get("$API/discover/${type.path}?$query"))
            val results = json.getJSONArray("results")
            (0 until results.length()).map { results.getJSONObject(it) }.map { item ->
                val genres = item.optJSONArray("genre_ids")
                Title(
                    id = item.getInt("id"),
                    type = type,
                    name = item.optString(if (type == MediaType.TV) "name" else "title"),
                    year = item.optString(if (type == MediaType.TV) "first_air_date" else "release_date").take(4),
                    overview = item.optString("overview"),
                    rating = item.optDouble("vote_average", 0.0),
                    popularity = item.optDouble("popularity", 0.0),
                    genreIds = if (genres == null) emptyList() else (0 until genres.length()).map { genres.getInt(it) },
                    backdropUrl = image(item, "backdrop_path", "w780"),
                    posterUrl = image(item, "poster_path", "w342"),
                    sources = emptyList(),
                )
            }
        }

    private fun image(item: JSONObject, field: String, size: String): String? {
        if (item.isNull(field)) return null
        val path = item.optString(field)
        return if (path.isBlank()) null else "$IMAGES/$size$path"
    }

    /**
     * Surprise Me: a random well-rated title on your services that you haven't marked as watched.
     * Picks from a random page of the best rated, so it isn't always the same few.
     */
    suspend fun surprise(type: MediaType, watched: Set<String>): Title? {
        val page = (1..5).random()
        val pool = merged(type, "sort_by=vote_average.desc&vote_count.gte=500&page=$page", byRating = true)
            .filter { it.key !in watched && it.backdropUrl != null }
        return pool.randomOrNull()
    }

    private val providerCache = ConcurrentHashMap<String, List<Source>>()

    /**
     * Shows and films matching [query], with which of your services has each one. Titles on your
     * services come first; the rest follow so you can see they exist.
     */
    suspend fun search(query: String): List<Title> = coroutineScope {
        val typed = query.trim()
        val text = URLEncoder.encode(typed, "UTF-8")
        // Shows and films separately, two pages each, so popular titles that only partly match
        // what you've typed (e.g. "break" -> Breaking Bad) aren't crowded out by exact matches.
        val pages = MediaType.entries.flatMap { type ->
            (1..2).map { page ->
                async(Dispatchers.IO) {
                    runCatching {
                        val json = JSONObject(get("$API/search/${type.path}?query=$text&include_adult=false&language=en-GB&page=$page"))
                        val results = json.getJSONArray("results")
                        (0 until results.length()).mapNotNull { i -> searchResult(results.getJSONObject(i), type) }
                    }
                }
            }
        }.awaitAll()
        if (pages.all { it.isFailure }) error("Search failed")
        val found = pages.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.key }

        // Best matches first: a title starting with what you typed, then any word starting with
        // it, then the rest; within each group, the most popular first.
        val lower = typed.lowercase()
        fun matchRank(name: String): Int {
            val n = name.lowercase()
            return when {
                n.startsWith(lower) -> 0
                n.split(' ', '-', ':', '\'').any { it.startsWith(lower) } -> 1
                n.contains(lower) -> 2
                else -> 3
            }
        }
        val ranked = found
            .sortedWith(compareBy<Title> { matchRank(it.name) }.thenByDescending { it.popularity })
            .take(24)

        val withSources = ranked.map { title -> async { title.copy(sources = sourcesFor(title)) } }.awaitAll()
        // Titles on your services first, keeping the ranking within each group.
        withSources.sortedBy { if (it.sources.isEmpty()) 1 else 0 }
    }

    private fun searchResult(item: JSONObject, type: MediaType): Title? {
        if (item.isNull("poster_path")) return null
        val genres = item.optJSONArray("genre_ids")
        return Title(
            id = item.getInt("id"),
            type = type,
            name = item.optString(if (type == MediaType.TV) "name" else "title"),
            year = item.optString(if (type == MediaType.TV) "first_air_date" else "release_date").take(4),
            overview = item.optString("overview"),
            rating = item.optDouble("vote_average", 0.0),
            popularity = item.optDouble("popularity", 0.0),
            genreIds = if (genres == null) emptyList() else (0 until genres.length()).map { genres.getInt(it) },
            backdropUrl = image(item, "backdrop_path", "w780"),
            posterUrl = image(item, "poster_path", "w342"),
            sources = emptyList(),
        )
    }

    /** Which of your services carry [title] in Ireland, in your order of preference. */
    private suspend fun sourcesFor(title: Title): List<Source> = withContext(Dispatchers.IO) {
        providerCache[title.key]?.let { return@withContext it }
        val sources = try {
            val json = JSONObject(get("$API/${title.type.path}/${title.id}/watch/providers?"))
            val flatrate = json.optJSONObject("results")?.optJSONObject(REGION)?.optJSONArray("flatrate")
            val ids = if (flatrate == null) emptySet() else (0 until flatrate.length()).map {
                flatrate.getJSONObject(it).optInt("provider_id").toString()
            }.toSet()
            Source.entries.filter { source -> source.providerIds.split('|').any { it in ids } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not remembered, so it's asked again next time (a failure isn't "on no service").
            Log.w(TAG, "Where-to-watch failed for ${title.name}", e)
            return@withContext emptyList()
        }
        providerCache[title.key] = sources
        sources
    }

    private val detailsCache = ConcurrentHashMap<String, TitleDetails>()

    /** Runtime or seasons, genres, credits, trailer and similar titles for the details page. */
    suspend fun details(title: Title): TitleDetails = coroutineScope {
        detailsCache[title.key]?.let { return@coroutineScope it }
        val main = async(Dispatchers.IO) {
            JSONObject(get("$API/${title.type.path}/${title.id}?append_to_response=credits,videos&language=en-GB"))
        }
        val similar = async {
            val genres = title.genreIds.filter { it != COMEDY || title.genreIds.size == 1 }.take(2)
            val params = buildString {
                append("sort_by=popularity.desc&vote_count.gte=100")
                if (genres.isNotEmpty()) append("&with_genres=").append(genres.joinToString(","))
            }
            val both = runCatching { merged(title.type, params, byRating = false) }.getOrDefault(emptyList())
            // Two genres together can be too narrow: fall back to just the first.
            val found = if (both.size < 4 && genres.size > 1) {
                runCatching { merged(title.type, "sort_by=popularity.desc&vote_count.gte=100&with_genres=${genres[0]}", byRating = false) }
                    .getOrDefault(both)
            } else {
                both
            }
            found.filter { it.id != title.id }.take(12)
        }
        val json = main.await()

        val length = if (title.type == MediaType.MOVIE) {
            json.optInt("runtime", 0).takeIf { it > 0 }?.let { minutes ->
                if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
            }
        } else {
            json.optInt("number_of_seasons", 0).takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" }
        }
        val genres = json.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }
            .orEmpty()
        val credits = json.optJSONObject("credits")
        val cast = credits?.optJSONArray("cast")?.let { a ->
            (0 until minOf(a.length(), 4)).map { a.getJSONObject(it).optString("name") }
        }.orEmpty()
        val madeBy = if (title.type == MediaType.MOVIE) {
            credits?.optJSONArray("crew")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }
                    .filter { it.optString("job") == "Director" }
                    .map { it.optString("name") }
                    .take(2)
            }?.takeIf { it.isNotEmpty() }?.let { "Directed by " + it.joinToString(" and ") }
        } else {
            json.optJSONArray("created_by")?.let { a -> (0 until minOf(a.length(), 2)).map { a.getJSONObject(it).optString("name") } }
                ?.takeIf { it.isNotEmpty() }?.let { "Created by " + it.joinToString(" and ") }
        }
        val trailer = json.optJSONObject("videos")?.optJSONArray("results")?.let { a ->
            val videos = (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("site") == "YouTube" && it.optString("type") == "Trailer" }
            (videos.firstOrNull { it.optBoolean("official") } ?: videos.firstOrNull())?.optString("key")
        }

        val details = TitleDetails(length, genres, madeBy, cast, trailer, similar.await())
        detailsCache[title.key] = details
        details
    }

    /**
     * Netflix's own id for a title, so "Watch on Netflix" can jump straight to it. TMDB gives the
     * title's Wikidata entry, and Wikidata records the Netflix id for many titles. Null when unknown.
     */
    suspend fun netflixId(title: Title): String? = withContext(Dispatchers.IO) {
        netflixIds[title.key]?.let { return@withContext it.ifBlank { null } }
        val id = try {
            val external = JSONObject(get("$API/${title.type.path}/${title.id}/external_ids?"))
            val wikidata = external.optString("wikidata_id").takeIf { it.startsWith("Q") }
            if (wikidata == null) {
                null
            } else {
                val entity = JSONObject(getPlain("https://www.wikidata.org/wiki/Special:EntityData/$wikidata.json"))
                entity.getJSONObject("entities").optJSONObject(wikidata)
                    ?.optJSONObject("claims")
                    ?.optJSONArray("P1874")
                    ?.optJSONObject(0)
                    ?.optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")
                    ?.optString("value")
                    ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not remembered, so it's tried again next time.
            Log.w(TAG, "Netflix id lookup failed for ${title.name}", e)
            return@withContext null
        }
        netflixIds[title.key] = id ?: ""
        id
    }

    private val genreNames = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 80 to "Crime", 18 to "Drama",
        10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 53 to "Thriller", 10752 to "War",
        37 to "Westerns", 10759 to "Action", 10762 to "Kids", 10765 to "Sci-Fi and Fantasy",
        10768 to "War and Politics", 10764 to "Reality", 10766 to "Soaps", 10767 to "Talk", 10763 to "News",
    )

    fun genreName(id: Int): String? = genreNames[id]

    /** TMDB request; the key is added here and never stored in the code. */
    private fun get(url: String): String {
        val key = URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")
        val separator = if (url.endsWith("?")) "" else "&"
        return getPlain("$url${separator}api_key=$key")
    }

    private fun getPlain(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TvLauncher/${BuildConfig.VERSION_NAME} (personal home screen app)")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Answered $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

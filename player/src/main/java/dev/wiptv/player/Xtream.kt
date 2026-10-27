package dev.wiptv.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStreamReader
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException

/** What you type to sign in to an Xtream Codes provider. */
data class Login(val server: String, val username: String, val password: String)

/** The account details the provider reports, plus how big its catalogue is. */
data class Account(
    val status: String,
    /** When the subscription ends, in seconds since 1970; null means it doesn't expire. */
    val expires: Long?,
    val activeConnections: Int,
    val maxConnections: Int,
    val trial: Boolean,
    val timezone: String?,
    val liveGroups: Int?,
    val movieGroups: Int?,
    val seriesGroups: Int?,
    /** Stream formats the provider allows, like "ts" and "m3u8". */
    val formats: List<String> = emptyList(),
) {
    val isActive: Boolean get() = status.equals("Active", ignoreCase = true)

    /** The format to ask for live channels in: MPEG-TS when allowed (it starts fastest), else HLS. */
    val liveFormat: String
        get() = when {
            formats.isEmpty() || "ts" in formats -> "ts"
            "m3u8" in formats -> "m3u8"
            else -> formats.first()
        }
}

/** A Live TV group (category) from the provider. */
data class Group(val id: String, val name: String)

/** One live channel. */
data class Channel(
    val id: Long,
    val number: Int,
    val name: String,
    val logo: String?,
    val groupId: String,
    /** Links the channel to the TV guide (used from phase 3). */
    val guideId: String?,
    /** Whether past programmes can be replayed (used from phase 5). */
    val catchUp: Boolean,
)

/** One programme in the TV guide. Times are in milliseconds since 1970. */
data class Programme(val title: String, val description: String, val start: Long, val end: Long) {
    fun isOn(now: Long) = now in start until end
}

/** Films or series: the two halves of the provider's on-demand catalogue. */
enum class VodKind { MOVIE, SERIES }

/** A film or a series as it appears in a catalogue row. */
data class VodItem(
    val kind: VodKind,
    val id: Long,
    val name: String,
    val poster: String?,
    val categoryId: String,
    /** The provider's score out of 10, when it has one. */
    val rating: Double?,
    /** The file type films play from, like "mp4" or "mkv" (films only). */
    val extension: String?,
    /** Series lists carry these; for films they come with the details. */
    val plot: String? = null,
    val backdrop: String? = null,
    val year: String? = null,
) {
    /** Remembers playback position and favourites under this. */
    val key: String get() = "${kind.name.lowercase()}:$id"
}

/** A film's details page. */
data class MovieDetails(
    val plot: String,
    val genre: String,
    val cast: String,
    val director: String,
    val year: String,
    val durationMinutes: Int?,
    val backdrop: String?,
    val trailer: String?,
    val rating: Double?,
    val extension: String,
)

/** One episode of a series. */
data class Episode(
    val id: Long,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val plot: String,
    val durationMinutes: Int?,
    val image: String?,
)

/** A series' details page: its seasons and their episodes. */
data class SeriesDetails(
    val plot: String,
    val genre: String,
    val cast: String,
    val year: String,
    val backdrop: String?,
    val trailer: String?,
    val rating: Double?,
    val episodes: Map<Int, List<Episode>>,
) {
    val seasons: List<Int> get() = episodes.keys.sorted()
}

/** A sign-in problem, worded for the screen. */
class XtreamException(message: String) : Exception(message)

/** Talks to an Xtream Codes provider through its player_api.php. */
object Xtream {

    /**
     * Tidies a typed server address: adds http:// when it's missing and drops anything after the
     * host and port, so "example.com:8080/" and "http://example.com:8080/player_api.php" both work.
     */
    fun normaliseServer(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return text
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
            text = "http://$text"
        }
        val url = runCatching { URL(text) }.getOrNull() ?: return text.trimEnd('/')
        val port = if (url.port == -1) "" else ":${url.port}"
        return "${url.protocol}://${url.host}$port"
    }

    /**
     * Providers often send an M3U link instead of separate details, like
     * http://example.com:8080/get.php?username=me&password=secret&type=m3u_plus.
     * When one is typed into the server box, this pulls the three details out of it.
     */
    fun fromM3uLink(text: String): Login? {
        val url = runCatching { URL(text.trim()) }.getOrNull() ?: return null
        val query = url.query ?: return null
        val params = query.split('&').mapNotNull { part ->
            val (key, value) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            key.lowercase() to URLDecoder.decode(value, "UTF-8")
        }.toMap()
        val username = params["username"] ?: return null
        val password = params["password"] ?: return null
        return Login(normaliseServer(text), username, password)
    }

    /**
     * Signs in and fetches the account details. Throws [XtreamException] with a readable reason.
     * [withCatalogue] also counts the provider's groups, which takes three more small requests.
     */
    suspend fun signIn(login: Login, withCatalogue: Boolean = true): Account = withContext(Dispatchers.IO) {
        val base = api(login)

        val json = try {
            JSONObject(get(base))
        } catch (e: JSONException) {
            throw XtreamException("That address doesn't look like an Xtream Codes server. Check it with your provider.")
        }
        val user = json.optJSONObject("user_info")
            ?: throw XtreamException("That address doesn't look like an Xtream Codes server. Check it with your provider.")
        if (user.optInt("auth", 0) != 1) {
            throw XtreamException("Wrong username or password.")
        }
        val server = json.optJSONObject("server_info")

        // How big the catalogue is: three small requests, made at the same time.
        val counts = if (!withCatalogue) listOf(null, null, null) else coroutineScope {
            listOf("get_live_categories", "get_vod_categories", "get_series_categories").map { action ->
                async { runCatching { JSONArray(get("$base&action=$action")).length() }.getOrNull() }
            }.map { it.await() }
        }

        Account(
            status = user.optString("status").ifBlank { "Unknown" },
            expires = number(user, "exp_date")?.takeIf { it > 0 },
            activeConnections = number(user, "active_cons")?.toInt() ?: 0,
            maxConnections = number(user, "max_connections")?.toInt() ?: 1,
            trial = number(user, "is_trial") == 1L,
            timezone = server?.optString("timezone")?.takeIf { it.isNotBlank() },
            liveGroups = counts[0],
            movieGroups = counts[1],
            seriesGroups = counts[2],
            formats = user.optJSONArray("allowed_output_formats")?.let { array ->
                (0 until array.length()).map { array.optString(it).lowercase() }.filter { it.isNotBlank() }
            } ?: emptyList(),
        )
    }

    /** The provider's Live TV groups, in the provider's order. */
    suspend fun liveGroups(login: Login): List<Group> = withContext(Dispatchers.IO) {
        val array = parseArray(get("${api(login)}&action=get_live_categories"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("category_id").ifBlank { return@mapNotNull null }
            Group(id, item.optString("category_name").trim().ifBlank { "Group $id" })
        }
    }

    /** The channels in one group, in the provider's order. */
    suspend fun liveChannels(login: Login, groupId: String): List<Channel> = withContext(Dispatchers.IO) {
        val array = parseArray(get("${api(login)}&action=get_live_streams&category_id=${encode(groupId)}"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = number(item, "stream_id") ?: return@mapNotNull null
            Channel(
                id = id,
                number = number(item, "num")?.toInt() ?: (i + 1),
                name = item.optString("name").trim().ifBlank { "Channel $id" },
                logo = item.optString("stream_icon").trim().takeIf { it.startsWith("http") },
                groupId = groupId,
                guideId = item.optString("epg_channel_id").trim().takeIf { it.isNotEmpty() && it != "null" },
                catchUp = number(item, "tv_archive") == 1L,
            )
        }
    }

    /**
     * What's on a channel now and next (the provider's "short EPG"): a handful of programmes from
     * now on. Cheap enough to ask for each channel as it comes on screen.
     */
    suspend fun shortGuide(login: Login, channelId: Long): List<Programme> = withContext(Dispatchers.IO) {
        val text = get("${api(login)}&action=get_short_epg&stream_id=$channelId&limit=8")
        val listings = runCatching { JSONObject(text).optJSONArray("epg_listings") }.getOrNull()
            ?: return@withContext emptyList()
        (0 until listings.length()).mapNotNull { i ->
            val item = listings.optJSONObject(i) ?: return@mapNotNull null
            val start = number(item, "start_timestamp") ?: return@mapNotNull null
            val end = number(item, "stop_timestamp") ?: number(item, "end_timestamp") ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            Programme(
                title = decodeText(item.optString("title")).ifBlank { "No title" },
                description = decodeText(item.optString("description")),
                start = start * 1000,
                end = end * 1000,
            )
        }.sortedBy { it.start }
    }

    /** Guide text usually arrives base64-encoded; some panels send it plain. */
    private fun decodeText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        if (!BASE64.matches(trimmed)) return trimmed
        val bytes = runCatching { android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return trimmed
        val decoded = String(bytes, Charsets.UTF_8)
        val looksWrong = '\uFFFD' in decoded || decoded.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        return if (looksWrong) trimmed else decoded.trim()
    }

    private val BASE64 = Regex("^[A-Za-z0-9+/=\\s]+$")

    // ---- Films and series ----

    /** The provider's film or series categories, in the provider's order. */
    suspend fun vodCategories(login: Login, kind: VodKind): List<Group> = withContext(Dispatchers.IO) {
        val action = if (kind == VodKind.MOVIE) "get_vod_categories" else "get_series_categories"
        val array = parseArray(get("${api(login)}&action=$action"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("category_id").ifBlank { return@mapNotNull null }
            Group(id, item.optString("category_name").trim().ifBlank { "Category $id" })
        }
    }

    /** The films or series in one category, in the provider's order. */
    suspend fun vodItems(login: Login, kind: VodKind, categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val action = if (kind == VodKind.MOVIE) "get_vod_streams" else "get_series"
        val array = parseArray(get("${api(login)}&action=$action&category_id=${encode(categoryId)}"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = number(item, if (kind == VodKind.MOVIE) "stream_id" else "series_id") ?: return@mapNotNull null
            val name = item.optString("name").trim().ifBlank { return@mapNotNull null }
            VodItem(
                kind = kind,
                id = id,
                name = name,
                poster = imageUrl(item.optString(if (kind == VodKind.MOVIE) "stream_icon" else "cover")),
                categoryId = categoryId,
                rating = decimal(item, "rating"),
                extension = item.optString("container_extension").trim().ifBlank { null },
                plot = text(item, "plot"),
                backdrop = firstImage(item.opt("backdrop_path")),
                year = yearOf(text(item, "releaseDate") ?: text(item, "release_date") ?: text(item, "year")),
            )
        }
    }

    /** A film's details. */
    suspend fun movieDetails(login: Login, movie: VodItem): MovieDetails = withContext(Dispatchers.IO) {
        val json = runCatching { JSONObject(get("${api(login)}&action=get_vod_info&vod_id=${movie.id}")) }
            .getOrElse { if (it is XtreamException) throw it else JSONObject() }
        val info = json.optJSONObject("info") ?: JSONObject()
        val data = json.optJSONObject("movie_data") ?: JSONObject()
        MovieDetails(
            plot = text(info, "plot") ?: text(info, "description") ?: "",
            genre = text(info, "genre") ?: "",
            cast = text(info, "cast") ?: text(info, "actors") ?: "",
            director = text(info, "director") ?: "",
            year = yearOf(text(info, "releasedate") ?: text(info, "release_date")) ?: "",
            durationMinutes = number(info, "duration_secs")?.let { (it / 60).toInt() }?.takeIf { it > 0 },
            backdrop = firstImage(info.opt("backdrop_path")) ?: imageUrl(info.optString("movie_image")),
            trailer = text(info, "youtube_trailer"),
            rating = decimal(info, "rating") ?: movie.rating,
            extension = text(data, "container_extension") ?: movie.extension ?: "mp4",
        )
    }

    /** A series' details: its seasons and episodes. */
    suspend fun seriesDetails(login: Login, series: VodItem): SeriesDetails = withContext(Dispatchers.IO) {
        val json = runCatching { JSONObject(get("${api(login)}&action=get_series_info&series_id=${series.id}")) }
            .getOrElse { if (it is XtreamException) throw it else JSONObject() }
        val info = json.optJSONObject("info") ?: JSONObject()
        val episodes = mutableMapOf<Int, List<Episode>>()
        // Episodes come as {"1": [...], "2": [...]}, or on some panels as a plain list.
        val raw = json.opt("episodes")
        val lists: List<JSONArray> = when (raw) {
            is JSONObject -> raw.keys().asSequence().mapNotNull { raw.optJSONArray(it) }.toList()
            is JSONArray -> listOf(raw)
            else -> emptyList()
        }
        for (list in lists) {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val id = number(item, "id") ?: continue
                val details = item.optJSONObject("info") ?: JSONObject()
                val season = number(item, "season")?.toInt() ?: 1
                val episodeNumber = number(item, "episode_num")?.toInt() ?: (i + 1)
                val episode = Episode(
                    id = id,
                    season = season,
                    number = episodeNumber,
                    title = text(item, "title") ?: "Episode $episodeNumber",
                    extension = text(item, "container_extension") ?: "mp4",
                    plot = text(details, "plot") ?: "",
                    durationMinutes = number(details, "duration_secs")?.let { (it / 60).toInt() }?.takeIf { it > 0 },
                    image = imageUrl(details.optString("movie_image")),
                )
                episodes[season] = (episodes[season] ?: emptyList()) + episode
            }
        }
        SeriesDetails(
            plot = text(info, "plot") ?: series.plot ?: "",
            genre = text(info, "genre") ?: "",
            cast = text(info, "cast") ?: "",
            year = yearOf(text(info, "releaseDate") ?: text(info, "release_date")) ?: series.year ?: "",
            backdrop = firstImage(info.opt("backdrop_path")) ?: series.backdrop,
            trailer = text(info, "youtube_trailer"),
            rating = decimal(info, "rating") ?: series.rating,
            episodes = episodes.mapValues { (_, list) -> list.sortedBy { it.number } },
        )
    }

    /** Where a film plays from. */
    fun movieUrl(login: Login, movieId: Long, extension: String): String =
        "${login.server}/movie/${pathPart(login.username)}/${pathPart(login.password)}/$movieId.$extension"

    /** Where an episode plays from. */
    fun episodeUrl(login: Login, episodeId: Long, extension: String): String =
        "${login.server}/series/${pathPart(login.username)}/${pathPart(login.password)}/$episodeId.$extension"

    private fun text(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun decimal(obj: JSONObject, key: String): Double? =
        text(obj, key)?.toDoubleOrNull()?.takeIf { it > 0 }

    private fun imageUrl(text: String?): String? = text?.trim()?.takeIf { it.startsWith("http") }

    private fun firstImage(value: Any?): String? = when (value) {
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { imageUrl(value.optString(it)) }.firstOrNull()
        is String -> imageUrl(value)
        else -> null
    }

    private fun yearOf(date: String?): String? = date?.let { Regex("(19|20)\\d{2}").find(it)?.value }

    /** The address a live channel plays from. */
    fun liveUrl(login: Login, channel: Channel, format: String): String =
        "${login.server}/live/${pathPart(login.username)}/${pathPart(login.password)}/${channel.id}.$format"

    private fun api(login: Login) =
        "${login.server}/player_api.php?username=${encode(login.username)}&password=${encode(login.password)}"

    private fun parseArray(text: String): JSONArray = try {
        JSONArray(text)
    } catch (e: JSONException) {
        // Some panels answer an empty group with {} or nothing at all.
        if (text.isBlank() || text.trim().startsWith("{")) JSONArray() else throw XtreamException("The provider sent something unexpected.")
    }

    private fun pathPart(text: String): String = encode(text).replace("+", "%20")

    /** Providers send numbers as numbers, strings or null, depending on the panel. */
    private fun number(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return when (val value = obj.get(key)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /**
     * Every film or series the provider has, handed over one at a time as it downloads, so even a
     * very large catalogue never sits in memory as a whole. For the player's own title list
     * ([CatalogIndex]), which the launcher asks about. Blocking: call it from a background
     * thread (it stays on the caller's thread, which the index's database transaction needs).
     */
    fun forEachVod(login: Login, kind: VodKind, each: (IndexedTitle) -> Unit) {
        val action = if (kind == VodKind.MOVIE) "get_vod_streams" else "get_series"
        val idKey = if (kind == VodKind.MOVIE) "stream_id" else "series_id"
        val posterKey = if (kind == VodKind.MOVIE) "stream_icon" else "cover"
        // The whole catalogue can take a provider well over 30 seconds to start sending.
        fetch("${api(login)}&action=$action", readTimeoutMs = 120_000) { input ->
            val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
            reader.isLenient = true
            // Some panels answer an empty catalogue with {} or nothing.
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return@fetch
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                val fields = HashMap<String, String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (reader.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> fields[name] = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val id = fields[idKey]?.trim()?.toLongOrNull() ?: continue
                val title = fields["name"]?.trim().orEmpty()
                if (title.isEmpty()) continue
                each(
                    IndexedTitle(
                        kind = kind,
                        id = id,
                        name = title,
                        year = yearOf(fields["releaseDate"] ?: fields["release_date"] ?: fields["year"]),
                        tmdb = (fields["tmdb"] ?: fields["tmdb_id"])?.trim()?.toLongOrNull()?.takeIf { it > 0 },
                        poster = imageUrl(fields[posterKey]),
                        extension = fields["container_extension"]?.trim()?.ifBlank { null },
                        categoryId = fields["category_id"]?.trim().orEmpty(),
                    ),
                )
            }
        }
    }

    private fun get(url: String): String = fetch(url) { it.bufferedReader().readText() }

    /** A GET request, following redirects by hand (including http → https, which Android won't). */
    private fun <T> fetch(url: String, readTimeoutMs: Int = 30_000, read: (java.io.InputStream) -> T): T {
        var current = url
        repeat(5) {
            val connection = try {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = readTimeoutMs
                    setRequestProperty("User-Agent", "WIPTVPlayer/${BuildConfig.VERSION_NAME}")
                }
            } catch (e: Exception) {
                throw XtreamException("That server address isn't valid.")
            }
            try {
                val code = try {
                    connection.responseCode
                } catch (e: UnknownHostException) {
                    throw XtreamException("Couldn't find that server. Check the address, and that the TV is online.")
                } catch (e: SocketTimeoutException) {
                    throw XtreamException("The server didn't answer in time. Try again in a minute.")
                } catch (e: java.io.IOException) {
                    throw XtreamException("Couldn't connect to the server (${e.message ?: e.javaClass.simpleName}).")
                }
                when (code) {
                    in 300..399 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw XtreamException("The server sent a broken redirect.")
                        current = URL(URL(current), location).toString()
                    }
                    in 200..299 -> return connection.inputStream.use(read)
                    401, 403 -> throw XtreamException("The server refused the sign-in. Check your details, or whether your subscription has ended.")
                    else -> throw XtreamException("The server answered with an error ($code).")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw XtreamException("The server redirected too many times.")
    }
}

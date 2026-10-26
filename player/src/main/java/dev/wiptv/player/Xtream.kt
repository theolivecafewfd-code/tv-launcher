package dev.wiptv.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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

    /** A GET request, following redirects by hand (including http → https, which Android won't). */
    private fun get(url: String): String {
        var current = url
        repeat(5) {
            val connection = try {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
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
                    in 200..299 -> return connection.inputStream.bufferedReader().use { it.readText() }
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

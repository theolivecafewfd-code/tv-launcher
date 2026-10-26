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
) {
    val isActive: Boolean get() = status.equals("Active", ignoreCase = true)
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

    /** Signs in and fetches the account details. Throws [XtreamException] with a readable reason. */
    suspend fun signIn(login: Login): Account = withContext(Dispatchers.IO) {
        val base = "${login.server}/player_api.php?username=${encode(login.username)}&password=${encode(login.password)}"

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
        val counts = coroutineScope {
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
        )
    }

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

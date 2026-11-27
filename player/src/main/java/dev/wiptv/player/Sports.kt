package dev.wiptv.player

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One side of a match. */
data class Side(
    val name: String,
    val shortName: String,
    val logo: String?,
    /** The goals so far (once it has kicked off). */
    val score: String?,
    /** This season's wins, draws and losses, "9-2-1", when ESPN has it. */
    val record: String? = null,
)

/** How many days the Sports page shows: today and the next two. */
const val SPORTS_DAYS = 3

/** One Premier League match from ESPN. */
data class Match(
    val id: String,
    /** Kick-off, in milliseconds since 1970. */
    val kickOff: Long,
    val live: Boolean,
    val finished: Boolean,
    /** Where a live match is: "35'", "HT", "45'+2'". */
    val clock: String,
    val home: Side,
    val away: Side,
) {
    /** The day it kicks off, on the TV's clock. */
    val day: LocalDate get() = Instant.ofEpochMilli(kickOff).atZone(ZoneId.systemDefault()).toLocalDate()
}

/**
 * Premier League fixtures and live scores from ESPN's public scoreboard (no key, nothing about
 * the viewer is sent). It isn't an official feed for apps, so if it stops answering the Sports
 * row just doesn't show.
 */
object Espn {
    private const val SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard"

    /** The matches today and the next [days] - 1 days (Irish time), in kick-off order. */
    suspend fun premierLeague(days: Int = SPORTS_DAYS): List<Match> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val last = today.plusDays((days - 1).toLong())
        // One request a day (ESPN's scoreboard doesn't take a range), then trimmed to local days.
        val events = (0 until days).flatMap { offset ->
            val date = today.plusDays(offset.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE)
            val list = JSONObject(get("$SCOREBOARD?dates=$date")).optJSONArray("events")
            if (list == null) emptyList() else (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        }.distinctBy { it.optString("id") }
        events.mapIndexedNotNull { i, event ->
            val game = event.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapIndexedNotNull null
            val kickOff = parseTime(game.optString("date").ifBlank { event.optString("date") }) ?: return@mapIndexedNotNull null
            val day = Instant.ofEpochMilli(kickOff).atZone(zone).toLocalDate()
            if (day.isBefore(today) || day.isAfter(last)) return@mapIndexedNotNull null
            val status = game.optJSONObject("status")
            val type = status?.optJSONObject("type")
            val state = type?.optString("state").orEmpty()
            val sides = game.optJSONArray("competitors") ?: return@mapIndexedNotNull null
            val list = (0 until sides.length()).mapNotNull { sides.optJSONObject(it) }
            val home = list.firstOrNull { it.optString("homeAway") == "home" } ?: list.getOrNull(0) ?: return@mapIndexedNotNull null
            val away = list.firstOrNull { it.optString("homeAway") == "away" } ?: list.getOrNull(1) ?: return@mapIndexedNotNull null
            val started = state == "in" || state == "post"
            Match(
                id = event.optString("id").ifBlank { "$i" },
                kickOff = kickOff,
                live = state == "in",
                finished = state == "post" || type?.optBoolean("completed") == true,
                clock = type?.optString("shortDetail").orEmpty().ifBlank { status?.optString("displayClock").orEmpty() },
                home = side(home, started),
                away = side(away, started),
            )
        }.sortedBy { it.kickOff }
    }

    private fun side(item: JSONObject, started: Boolean): Side {
        val team = item.optJSONObject("team") ?: JSONObject()
        val name = team.optString("displayName").ifBlank { team.optString("name") }.ifBlank { "?" }
        return Side(
            name = name,
            shortName = team.optString("shortDisplayName").ifBlank { name },
            logo = team.optString("logo").takeIf { it.startsWith("https://") },
            score = if (started) item.optString("score").takeIf { it.isNotBlank() && it != "null" } else null,
            record = item.optJSONArray("records")?.optJSONObject(0)?.optString("summary")
                ?.takeIf { it.matches(RECORD) },
        )
    }

    private val RECORD = Regex("\\d+-\\d+-\\d+")

    private fun parseTime(text: String): Long? =
        runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")).toInstant().toEpochMilli() }.getOrNull()

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WIPTVPlayer/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) error("ESPN answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}

/** The Live TV group with your sports channels, chosen once on Home. */
class SportsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sports", Context.MODE_PRIVATE)

    fun group(): Group? {
        val id = prefs.getString("group", null) ?: return null
        return Group(id, prefs.getString("groupName", null) ?: "Sports")
    }

    fun setGroup(group: Group) {
        prefs.edit().putString("group", group.id).putString("groupName", group.name).apply()
    }
}

/** A sports group's channels and what's on them, kept while the player runs. */
class SportsGuide(val groupId: String, val loadedAt: Long, val channels: List<Channel>, val programmes: Map<Long, List<Programme>>)

/** Kept between visits to Home, so coming back doesn't ask ESPN or the provider again at once. */
object SportsCache {
    var matches: List<Match>? = null
    var matchesAt = 0L
    var guide: SportsGuide? = null

    /** How long the sports channels' guides are kept before they're read again. */
    const val GUIDE_KEEP_MS = 30 * 60_000L

    /** The most channels whose guides are read (a few at a time), to spare the Fire Stick. */
    private const val MAX_CHANNELS = 150

    /** Reads the guide (the next day or so) of every channel in [group]. */
    suspend fun loadGuide(login: Login, group: Group): SportsGuide {
        val channels = Xtream.liveChannels(login, group.id).take(MAX_CHANNELS)
        val programmes = mutableMapOf<Long, List<Programme>>()
        for (batch in channels.chunked(4)) {
            coroutineScope {
                batch.map { channel ->
                    async {
                        channel.id to try {
                            Xtream.shortGuide(login, channel.id, limit = 30)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll()
            }.forEach { (id, list) -> programmes[id] = list }
        }
        return SportsGuide(group.id, System.currentTimeMillis(), channels, programmes).also { guide = it }
    }
}

/**
 * Finds the channel showing a match: a programme on one of your sports channels that names both
 * teams and is on around kick-off. Names in guides vary ("Man Utd", "Manchester United"), so each
 * team has a few versions; a programme naming both in its title beats one naming them only in its
 * description, "Live" helps, and highlights or replays don't count.
 */
object SportsMatcher {
    fun channelFor(match: Match, guide: SportsGuide): Channel? {
        val home = aliases(match.home)
        val away = aliases(match.away)
        var best: Channel? = null
        var bestScore = 0
        for (channel in guide.channels) {
            val programmes = guide.programmes[channel.id] ?: continue
            for (p in programmes) {
                // On around kick-off: starts before it's well under way and runs past the start.
                if (p.start > match.kickOff + 45 * 60_000L || p.end < match.kickOff + 10 * 60_000L) continue
                val title = normalise(p.title)
                val all = title + normalise(p.description)
                if (home.none { it in all } || away.none { it in all }) continue
                var score = if (home.any { it in title } && away.any { it in title }) 3 else 1
                if (" live " in title) score += 1
                if (NOT_LIVE.any { it in title }) score -= 3
                if (score > bestScore) {
                    bestScore = score
                    best = channel
                }
            }
        }
        return best
    }

    private val NOT_LIVE = listOf(" highlights ", " replay ", " reaction ", " classic ", " review ", " rewind ")

    /** Words that start more than one club's name, so they can't stand for a team on their own. */
    private val SHARED_FIRST_WORDS = setOf("manchester", "west", "aston", "crystal", "nottingham", "sheffield", "afc", "real", "the")

    private val EXTRA = mapOf(
        "manchester united" to listOf("man utd", "man united"),
        "manchester city" to listOf("man city"),
        "tottenham hotspur" to listOf("spurs", "tottenham"),
        "wolverhampton wanderers" to listOf("wolves"),
        "brighton and hove albion" to listOf("brighton"),
        "nottingham forest" to listOf("forest", "nottm forest", "nott m forest"),
        "west ham united" to listOf("west ham"),
        "aston villa" to listOf("villa"),
        "crystal palace" to listOf("palace"),
        "afc bournemouth" to listOf("bournemouth"),
        "sheffield united" to listOf("sheff utd", "sheffield utd"),
    )

    private fun aliases(side: Side): List<String> {
        val names = listOf(side.name, side.shortName).map { normalise(it).trim() }.filter { it.isNotEmpty() }
        val extra = names.flatMap { EXTRA[it].orEmpty() }
        val trimmed = names.map { it.removePrefix("afc ").removeSuffix(" fc").removeSuffix(" afc") }
        val firstWords = names.mapNotNull { it.substringBefore(' ').takeIf { w -> w.length >= 4 && w !in SHARED_FIRST_WORDS } }
        return (names + extra + trimmed + firstWords).distinct().map { " $it " }
    }

    /** Lower case, no accents, "&" as "and", anything else between words as one space, padded. */
    fun normalise(text: String): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase().replace("&", " and ")
        return " " + plain.replace(NON_WORD, " ").trim() + " "
    }

    private val ACCENTS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^a-z0-9]+")
}

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

    /** Not started and more than an hour from kick-off (most games have an hour's build-up): too soon to watch. */
    fun notOnYet(now: Long = System.currentTimeMillis()): Boolean = !live && !finished && kickOff - now > NOT_ON_YET_MS
}

/** How long before kick-off a match's channels can be chosen. */
const val NOT_ON_YET_MS = 60 * 60_000L

/** A game on UK TV, from the listings: the teams as written there and the channels showing it. */
class TvListing(val day: LocalDate, val time: String, val teams: String, val channels: List<String>)

/**
 * Which UK channels show each Premier League game, from live-footballontv.com's listings page (a
 * public web page; nothing about the viewer is sent). Saturday 3pm games aren't on UK TV, so they
 * aren't listed. If the page changes or doesn't answer, there's just no listing.
 */
object TvListings {
    private const val PAGE = "https://www.live-footballontv.com/live-premier-league-football-on-tv.html"

    /** The day anchors ("2026Nov28"), each game's time, teams and competition, and its channels, in page order. */
    private val TOKEN = Regex(
        "<a id=\"(\\d{4})([A-Z][a-z]{2})(\\d{2})\"|fixture__time\">([^<]*)<|fixture__teams\">([^<]*)<|" +
            "fixture__competition\">([^<]*)<|channel-pill\"[^>]*>([^<]*)<",
    )
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    suspend fun premierLeague(): List<TvListing> = withContext(Dispatchers.IO) {
        val connection = (URL(PAGE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "WIPTVPlayer/${BuildConfig.VERSION_NAME}")
        }
        val html = try {
            if (connection.responseCode !in 200..299) error("Listings answered ${connection.responseCode}")
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
        parse(html)
    }

    fun parse(html: String): List<TvListing> {
        val found = mutableListOf<TvListing>()
        var day: LocalDate? = null
        var time = ""
        var teams = ""
        var competition = ""
        var channels = mutableListOf<String>()
        fun finish() {
            val d = day
            if (d != null && teams.isNotBlank() && competition.trim().equals("Premier League", ignoreCase = true) && channels.isNotEmpty()) {
                found += TvListing(d, time, teams, channels.distinct())
            }
            time = ""
            teams = ""
            competition = ""
            channels = mutableListOf()
        }
        for (m in TOKEN.findAll(html)) {
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> {
                    finish()
                    val month = MONTHS.indexOf(g[2]) + 1
                    day = if (month > 0) runCatching { LocalDate.of(g[1].toInt(), month, g[3].toInt()) }.getOrNull() else null
                }
                g[4].isNotEmpty() -> {
                    finish()
                    time = g[4].trim()
                }
                g[5].isNotEmpty() -> teams = unescape(g[5]).trim()
                g[6].isNotEmpty() -> competition = unescape(g[6])
                g[7].isNotEmpty() -> unescape(g[7]).trim().takeIf { it.isNotEmpty() }?.let { channels += it }
            }
        }
        finish()
        return found
    }

    private fun unescape(text: String) =
        text.replace("&amp;", "&").replace("&#039;", "'").replace("&#39;", "'").replace("&quot;", "\"").replace("&nbsp;", " ")
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

/** The channel you chose for each match, so OK on its card goes straight there next time. */
class SportsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sports", Context.MODE_PRIVATE)

    fun pick(matchId: String): Channel? {
        val text = prefs.getString("pick.$matchId", null) ?: return null
        return runCatching {
            val o = JSONObject(text)
            Channel(
                id = o.getLong("id"),
                number = 0,
                name = o.getString("name"),
                logo = o.optString("logo").takeIf { it.startsWith("http") },
                groupId = o.optString("group"),
                guideId = null,
                catchUp = false,
            )
        }.getOrNull()
    }

    /** Saves [channel] for [matchId], and forgets picks for matches no longer in [keep]. */
    fun setPick(matchId: String, channel: Channel, keep: Set<String>) {
        val o = JSONObject().put("id", channel.id).put("name", channel.name).put("logo", channel.logo ?: "").put("group", channel.groupId)
        val edit = prefs.edit().putString("pick.$matchId", o.toString())
        prefs.all.keys.filter { it.startsWith("pick.") && it.removePrefix("pick.") !in keep && it != "pick.$matchId" }.forEach { edit.remove(it) }
        edit.apply()
    }
}

/** Your provider's sports channels and what's on them, kept while the player runs. */
class SportsGuide(
    val loadedAt: Long,
    val channels: List<Channel>,
    val programmes: Map<Long, List<Programme>>,
    /** The groups named for the Premier League ("EPL", "Premier League"), where channels are for one game or team. */
    val leagueGroups: Set<String> = emptySet(),
    /** False while only the channels are known and their guides are still being read. */
    val complete: Boolean = true,
)

/** A channel that might show a match, for the list you choose from. */
class ChannelOption(
    val channel: Channel,
    /** What the guide has on it at kick-off. */
    val programme: Programme?,
    /** "Showing this game", "Football on" or "Sports channel". */
    val tag: String,
    /** The guide names both teams on it around kick-off. */
    val showing: Boolean,
)

/** Kept between visits to Home, so coming back doesn't ask ESPN or the provider again at once. */
object SportsCache {
    var matches: List<Match>? = null
    var matchesAt = 0L
    var guide: SportsGuide? = null
    var listings: List<TvListing>? = null
    var listingsAt = 0L

    /** How long the TV listings are kept before they're read again. */
    const val LISTINGS_KEEP_MS = 60 * 60_000L

    /** How long the sports channels' guides are kept before they're read again. */
    const val GUIDE_KEEP_MS = 30 * 60_000L

    /** The most channels whose guides are read (a few at a time), to spare the Fire Stick. */
    private const val MAX_CHANNELS = 150

    /** The most groups looked through for sports channels. */
    private const val MAX_GROUPS = 20

    /**
     * Finds your provider's sports channels without you choosing a group: the Premier League groups
     * first (every channel in them), then the groups named for sport (UK and Irish ones before
     * others) and the channels in them that look like sport (Sky Sports, TNT, Premier Sports,
     * Amazon, DAZN, match and event channels…); then reads the next day or so of each one's guide.
     */
    suspend fun loadGuide(login: Login, onChannels: (SportsGuide) -> Unit = {}): SportsGuide {
        val groups = Xtream.liveGroups(login)
            .map { it to SportsChannels.groupScore(it.name) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_GROUPS)
        val found = mutableListOf<Pair<Channel, Int>>()
        val leagueGroups = mutableSetOf<String>()
        for ((group, groupScore) in groups) {
            val inGroup = try {
                Xtream.liveChannels(login, group.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val sportsGroup = SportsChannels.isSportsGroup(group.name)
            val league = SportsChannels.isLeagueGroup(group.name)
            if (league) leagueGroups += group.id
            inGroup.forEach { channel ->
                val score = SportsChannels.channelScore(channel.name)
                // In a sports group every channel counts; elsewhere only ones named for sport.
                // The Premier League groups' channels always make the cut.
                if (score > 0 || sportsGroup) found += channel to (score + groupScore + if (league) 100 else 0)
            }
        }
        val channels = found.distinctBy { it.first.id }.sortedByDescending { it.second }.map { it.first }.take(MAX_CHANNELS)
        // The channels first, so ones named for a game or team show while the guides are read.
        onChannels(SportsGuide(System.currentTimeMillis(), channels, emptyMap(), leagueGroups, complete = false))
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
        return SportsGuide(System.currentTimeMillis(), channels, programmes, leagueGroups).also { guide = it }
    }
}

/** How sporty a group or channel name looks. */
object SportsChannels {
    private val HOME = Regex("(^|[^a-z])(uk|ie|irl|ireland|gb|eng|england)([^a-z]|$)")
    private val BROADCASTERS = listOf(
        "sky sports", "tnt sport", "bt sport", "premier sports", "amazon", "prime video", "dazn",
        "viaplay", "premier league", "mutv", "lfc tv", "lfctv", "virgin media",
    )
    private val SPORTY = listOf("sport", "football", "soccer", "match", "event", "bein", "espn", "setanta", "fubo")

    /** Short names that must stand alone ("epl", not the middle of "replay"). */
    private val WORDS = Regex("(^|[^a-z])(epl|ppv|rte)([^a-z]|$)")

    fun isSportsGroup(name: String) = name.lowercase().let { n ->
        "sport" in n || "football" in n || "event" in n || isLeagueGroup(n) || WORDS.containsMatchIn(n)
    }

    /** A group for the Premier League itself ("EPL", "Premier League", "PL Hub"). */
    fun isLeagueGroup(name: String) = name.lowercase().let { n ->
        "premier league" in n || LEAGUE.containsMatchIn(n)
    }
    private val LEAGUE = Regex("(^|[^a-z])(epl|pl)([^a-z]|$)")

    /** A group's score: Premier League groups, then sports groups, Irish and UK ones before others; 0 to skip it. */
    fun groupScore(name: String): Int {
        val n = name.lowercase()
        val home = HOME.containsMatchIn(n)
        return when {
            isLeagueGroup(n) -> 8
            isSportsGroup(n) && home -> 4
            isSportsGroup(n) -> 2
            home -> 1
            else -> 0
        }
    }

    /** A channel's score: big UK and Irish broadcasters first, then anything sporty; 0 if not sport. */
    fun channelScore(name: String): Int {
        val n = name.lowercase()
        val home = if (HOME.containsMatchIn(n)) 1 else 0
        return when {
            BROADCASTERS.any { it in n } -> 4 + home
            SPORTY.any { it in n } || WORDS.containsMatchIn(n) -> 2 + home
            else -> 0
        }
    }
}

/**
 * Finds the channel showing a match: a programme on one of your sports channels that names both
 * teams and is on around kick-off. Names in guides vary ("Man Utd", "Manchester United"), so each
 * team has a few versions; a programme naming both in its title beats one naming them only in its
 * description, "Live" helps, and highlights or replays don't count.
 */
object SportsMatcher {
    /** The channel most likely to be showing [match], or null. */
    fun channelFor(match: Match, guide: SportsGuide, listing: TvListing? = null): Channel? =
        options(match, guide, listing).firstOrNull { it.showing }?.channel

    /** The UK TV listing for [match]: same day, both teams named. */
    fun listingFor(match: Match, listings: List<TvListing>?): TvListing? {
        val home = aliases(match.home)
        val away = aliases(match.away)
        return listings.orEmpty().firstOrNull { l ->
            val teams = normalise(l.teams)
            l.day == match.day && home.any { it in teams } && away.any { it in teams }
        }
    }

    /** "Sky Sports Main Event" as it might be named by a provider: "sky sport main event", "sky sport pl"… */
    private fun channelKeys(name: String): List<String> {
        val n = normalise(name).replace(" sports ", " sport ")
        return listOf(n, n.replace(" premier league ", " pl ")).distinct()
    }

    /**
     * Whether a provider's channel ([provider], normalised) is the listed one ([listed]): the listed
     * name, then nothing but quality and country tags, so "Sky Sports+" isn't every Sky Sports
     * channel and "TNT Sports 1" isn't "TNT Sports 10".
     */
    private fun sameChannel(listed: String, provider: String): Boolean {
        val at = provider.indexOf(listed)
        if (at < 0) return false
        val rest = provider.substring(at + listed.length).trim()
        return rest.isEmpty() || rest.split(' ').all { it in TAGS }
    }

    private val TAGS = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "hdr", "hevc", "h265", "h264", "raw", "backup", "alt", "vip", "plus",
        "uk", "ie", "irl", "ireland", "gb", "1080p", "720p", "50fps", "60fps", "fps",
    )

    /**
     * Channels to choose from for [match], best first:
     * - "Showing this game": its guide names both teams around kick-off, or its own name does
     *   (a provider's channel for the one game);
     * - "On TV for this game": the UK listings name it for this game;
     * - "Team channel": a Premier League group's channel named for one of the teams;
     * - "Football on": football is on it at kick-off;
     * - "Sports channel": a few of the big UK and Irish sports channels, in case.
     */
    fun options(match: Match, guide: SportsGuide, listing: TvListing? = null, others: Int = 8): List<ChannelOption> {
        val home = aliases(match.home)
        val away = aliases(match.away)
        val listed = listing?.channels.orEmpty().flatMap { channelKeys(it) }
        val showing = mutableListOf<Pair<ChannelOption, Int>>()
        val onTv = mutableListOf<ChannelOption>()
        val team = mutableListOf<ChannelOption>()
        val football = mutableListOf<ChannelOption>()
        val rest = mutableListOf<ChannelOption>()
        for (channel in guide.channels) {
            val programmes = guide.programmes[channel.id].orEmpty()
            // On around kick-off: starts before it's well under way and runs past the start.
            val around = programmes.filter { p -> p.start <= match.kickOff + 45 * 60_000L && p.end >= match.kickOff + 10 * 60_000L }
            var best: Programme? = null
            var bestScore = 0
            for (p in around) {
                val title = normalise(p.title)
                val all = title + normalise(p.description)
                if (home.none { it in all } || away.none { it in all }) continue
                var score = if (home.any { it in title } && away.any { it in title }) 3 else 1
                if (" live " in title) score += 1
                if (NOT_LIVE.any { it in title }) score -= 3
                if (score > bestScore) {
                    bestScore = score
                    best = p
                }
            }
            val atKickOff = around.firstOrNull { match.kickOff in it.start until it.end } ?: around.firstOrNull()
            val name = normalise(channel.name)
            val inLeagueGroup = channel.groupId in guide.leagueGroups
            val keys = channelKeys(channel.name)
            when {
                best != null -> showing += ChannelOption(channel, best, "Showing this game", showing = true) to bestScore
                home.any { it in name } && away.any { it in name } ->
                    showing += ChannelOption(channel, atKickOff, "Showing this game", showing = true) to 5
                listed.any { l -> keys.any { k -> sameChannel(l, k) } } -> onTv += ChannelOption(channel, atKickOff, "On TV for this game", showing = true)
                inLeagueGroup && (home.any { it in name } || away.any { it in name }) ->
                    team += ChannelOption(channel, atKickOff, "Team channel", showing = false)
                atKickOff != null && FOOTBALL.any { it in normalise(atKickOff.title + " " + atKickOff.description) } ->
                    football += ChannelOption(channel, atKickOff, "Football on", showing = false)
                SportsChannels.channelScore(channel.name) >= 4 -> rest += ChannelOption(channel, atKickOff, "Sports channel", showing = false)
            }
        }
        return showing.sortedByDescending { it.second }.map { it.first } + onTv + team + football + rest.take(others)
    }

    private val FOOTBALL = listOf(" premier league ", " football ", " soccer ", " epl ")

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

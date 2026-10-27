package dev.tvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A film or series in the IPTV provider's catalogue, as WIPTV Player reports it. */
data class IptvTitle(val type: MediaType, val id: Long, val name: String, val year: String?, val posterUrl: String?)

/**
 * Asks WIPTV Player about your IPTV provider's films and series. The player keeps the list
 * and the sign-in; the launcher only gets names, years, posters and ids, through a channel only
 * our own apps can use. Everything here quietly gives nothing when the player isn't installed,
 * isn't signed in, or hasn't fetched its list yet.
 */
object Iptv {
    private const val TAG = "Iptv"
    private const val PLAYER = "dev.wiptv.player"
    private val base = Uri.parse("content://dev.wiptv.player.catalog")

    /** The provider's entry for [title], matched by name and year. */
    suspend fun match(context: Context, title: Title): IptvTitle? = withContext(Dispatchers.IO) {
        val uri = base.buildUpon().appendPath("match")
            .appendQueryParameter("kind", kindOf(title.type))
            .appendQueryParameter("title", title.name)
            .appendQueryParameter("year", title.year)
            .build()
        query(context, uri).firstOrNull()
    }

    /** Films and series whose name contains [text]. */
    suspend fun search(context: Context, text: String): List<IptvTitle> = withContext(Dispatchers.IO) {
        query(context, base.buildUpon().appendPath("search").appendQueryParameter("q", text).build())
    }

    /**
     * A line saying how the player's list stands, for the search screen: how many films and
     * series it has, or why there's nothing (not installed, not signed in, still fetching, an
     * error).
     */
    suspend fun status(context: Context): String = withContext(Dispatchers.IO) {
        try {
            context.packageManager.getPackageInfo(PLAYER, 0)
        } catch (e: Exception) {
            return@withContext "IPTV: WIPTV Player isn't installed"
        }
        try {
            context.contentResolver.query(base.buildUpon().appendPath("status").build(), null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@withContext "IPTV: no answer from WIPTV Player"
                val signedIn = c.getInt(0) == 1
                val films = c.getInt(1)
                val series = c.getInt(2)
                val refreshed = c.getLong(3)
                val refreshing = c.getInt(4) == 1
                val error = c.getString(5)
                when {
                    !signedIn -> "IPTV: sign in to WIPTV Player first"
                    films + series > 0 -> "IPTV: $films films and $series series" +
                        (if (refreshed > 0) " · updated " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.UK).format(java.util.Date(refreshed)) else "") +
                        (if (refreshing) " · updating" else "")
                    refreshing -> "IPTV: WIPTV Player is fetching your provider's list, try again in a minute"
                    error != null -> "IPTV: the list couldn't be fetched ($error)"
                    else -> "IPTV: no list yet, open WIPTV Player and wait a minute"
                }
            } ?: "IPTV: WIPTV Player needs updating"
        } catch (e: SecurityException) {
            "IPTV: not allowed to ask WIPTV Player (reinstall both apps)"
        } catch (e: Exception) {
            "IPTV: couldn't ask WIPTV Player (${e.javaClass.simpleName})"
        }
    }

    /** Opens it in WIPTV Player: a film plays (or resumes), a series opens its page. */
    fun open(context: Context, title: IptvTitle): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .setClassName(PLAYER, "$PLAYER.MainActivity")
                .putExtra("open_kind", kindOf(title.type))
                .putExtra("open_id", title.id)
                // A fresh player each time, so it opens on this title and closes back to here.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't open the player", e)
        false
    }

    private fun kindOf(type: MediaType) = if (type == MediaType.MOVIE) "MOVIE" else "SERIES"

    private fun query(context: Context, uri: Uri): List<IptvTitle> = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val out = mutableListOf<IptvTitle>()
            while (c.moveToNext()) {
                out += IptvTitle(
                    type = if (c.getString(0) == "MOVIE") MediaType.MOVIE else MediaType.TV,
                    id = c.getLong(1),
                    name = c.getString(2),
                    year = c.getString(3),
                    posterUrl = c.getString(4),
                )
            }
            out
        }.orEmpty()
    } catch (e: Exception) {
        // No player, or no permission (the player isn't one of ours): just no IPTV results.
        Log.w(TAG, "Couldn't ask the player", e)
        emptyList()
    }
}

/**
 * Which titles your IPTV provider has, for the IPTV badge on posters. Each poster asks once (one
 * at a time, from the player's list on the TV); answers are kept while the launcher runs, and
 * the "no" answers are forgotten each time you come back home, in case the list has filled in.
 */
object IptvBadges {
    private val known = mutableStateMapOf<String, Boolean>()
    private val lock = Mutex()

    fun forgetMisses() {
        known.keys.filter { known[it] == false }.forEach { known.remove(it) }
    }

    @Composable
    fun has(title: Title): Boolean {
        val context = LocalContext.current
        val answer = known[title.key]
        if (answer == null) {
            LaunchedEffect(title.key) {
                lock.withLock {
                    if (title.key !in known) known[title.key] = Iptv.match(context, title) != null
                }
            }
        }
        return answer == true
    }
}

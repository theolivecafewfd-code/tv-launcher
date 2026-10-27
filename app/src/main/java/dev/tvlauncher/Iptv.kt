package dev.tvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
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

package dev.wiptv.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Lets the launcher ask the player about the provider's films and series, without ever seeing
 * the sign-in or a stream address. Only apps signed with our key can use it (the
 * `dev.wiptv.permission.CATALOG` permission). Answers come from [CatalogIndex], which this
 * also refreshes when it's a day old.
 *
 * - `content://dev.wiptv.player.catalog/match?kind=MOVIE&title=…&year=…&tmdb=…`: the provider's
 *   entry for one title, or no rows.
 * - `content://dev.wiptv.player.catalog/search?q=…`: films and series whose name contains q.
 *
 * - `content://dev.wiptv.player.catalog/continue`: what you've started, newest first: key, kind,
 *   title, subtitle, poster, image, progress (0–1), left ("38 min left"). Never the stream address.
 * - `content://dev.wiptv.player.catalog/status`: one row: signed_in, films, series, refreshed,
 *   refreshing, error.
 * - `content://dev.wiptv.player.catalog/mylist`: My List, newest first: kind, id, name, year,
 *   poster, backdrop. The launcher shows it as its own My List, so there's one list for both.
 *   Insert (values kind, id) adds a title from the provider's list; delete with `?kind=…&id=…`
 *   takes one off.
 * - `content://dev.wiptv.player.catalog/channels`: your favourite live channels, in your order:
 *   id, name (without its quality tag), logo. Never a stream address.
 * - `content://dev.wiptv.player.catalog/onnow?id=…`: what's on a live channel now and next, from
 *   the provider's guide: title, description, start, end (milliseconds). Up to two rows.
 *
 * Rows: kind, id, name, year, poster.
 */
class CatalogProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        // Unlocking the saved sign-in takes a moment, so only for a status check or a stale list
        // (each poster's IPTV badge asks here).
        if (uri.lastPathSegment == "onnow") return onNow(context, uri.getQueryParameter("id")?.toLongOrNull() ?: return null)
        val status = uri.lastPathSegment == "status"
        val login = if (status || CatalogIndex.isStale(context)) LoginStore.load(context) else null
        login?.let { CatalogIndex.refreshIfStale(context, it) }
        if (uri.lastPathSegment == "channels") {
            val cursor = MatrixCursor(arrayOf("id", "name", "logo"))
            FavouritePrefs(context).load().forEach { c ->
                cursor.addRow(arrayOf<Any?>(c.id, ChannelLabels.of(c.name).name, c.logo))
            }
            return cursor
        }
        if (uri.lastPathSegment == "mylist") {
            val cursor = MatrixCursor(arrayOf("kind", "id", "name", "year", "poster", "backdrop"))
            VodStore(context).favourites().forEach { f ->
                cursor.addRow(arrayOf<Any?>(f.kind.name, f.id, f.name, f.year, f.poster, f.backdrop))
            }
            return cursor
        }
        if (uri.lastPathSegment == "continue") {
            val cursor = MatrixCursor(arrayOf("key", "kind", "title", "subtitle", "poster", "image", "progress", "left", "resume_from"))
            VodStore(context).resumePoints().take(20).forEach { p ->
                cursor.addRow(
                    arrayOf<Any?>(
                        p.key,
                        if (p.key.startsWith("movie:")) "MOVIE" else "SERIES",
                        p.title,
                        p.subtitle,
                        p.poster,
                        p.image,
                        p.progress.toDouble(),
                        timeLeft(p),
                        if (p.position > 0) clockOf(p.position) else null,
                    ),
                )
            }
            return cursor
        }
        if (status) {
            val s = CatalogIndex.status(context)
            return MatrixCursor(arrayOf("signed_in", "films", "series", "refreshed", "refreshing", "error")).apply {
                addRow(arrayOf<Any?>(if (login != null) 1 else 0, s.films, s.series, s.refreshed, if (s.refreshing) 1 else 0, s.error))
            }
        }
        val rows = when (uri.lastPathSegment) {
            "match" -> {
                val kind = uri.getQueryParameter("kind")?.let { k -> VodKind.entries.firstOrNull { it.name == k } } ?: return null
                val title = uri.getQueryParameter("title") ?: return null
                listOfNotNull(
                    CatalogIndex.match(
                        context,
                        kind,
                        title,
                        uri.getQueryParameter("year")?.ifBlank { null },
                        uri.getQueryParameter("tmdb")?.toLongOrNull(),
                    ),
                )
            }
            "search" -> CatalogIndex.search(context, uri.getQueryParameter("q").orEmpty())
            else -> return null
        }
        val cursor = MatrixCursor(arrayOf("kind", "id", "name", "year", "poster"))
        rows.forEach { cursor.addRow(arrayOf<Any?>(it.kind.name, it.id, it.name, it.year, it.poster)) }
        return cursor
    }

    /** Now and next on one channel. The guide is asked for there and then, so it's bounded. */
    private fun onNow(context: android.content.Context, channelId: Long): Cursor {
        val cursor = MatrixCursor(arrayOf("title", "description", "start", "end"))
        val login = LoginStore.load(context) ?: return cursor
        val now = System.currentTimeMillis()
        val programmes = try {
            runBlocking { withTimeout(8_000) { Xtream.shortGuide(login, channelId) } }
        } catch (e: Exception) {
            // Includes the timeout: the launcher then just shows the channel's name.
            emptyList()
        }
        withoutOverlaps(programmes).filter { it.end > now }.take(2).forEach {
            cursor.addRow(arrayOf<Any?>(it.title, it.description, it.start, it.end))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        if (uri.lastPathSegment != "mylist" || values == null) return null
        val kind = VodKind.entries.firstOrNull { it.name == values.getAsString("kind") } ?: return null
        val id = values.getAsLong("id") ?: return null
        val title = CatalogIndex.find(context, kind, id) ?: return null
        VodStore(context).addFavourite(
            VodItem(
                kind = kind,
                id = id,
                name = title.name,
                poster = title.poster,
                categoryId = title.categoryId,
                rating = null,
                extension = title.extension,
                year = title.year,
            ),
        )
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = context ?: return 0
        if (uri.lastPathSegment != "mylist") return 0
        val kind = VodKind.entries.firstOrNull { it.name == uri.getQueryParameter("kind") } ?: return 0
        val id = uri.getQueryParameter("id")?.toLongOrNull() ?: return 0
        return if (VodStore(context).removeFavourite("${kind.name.lowercase()}:$id")) 1 else 0
    }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

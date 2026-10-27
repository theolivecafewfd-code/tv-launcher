package dev.wiptv.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

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
 * - `content://dev.wiptv.player.catalog/status`: one row: signed_in, films, series, refreshed,
 *   refreshing, error.
 *
 * Rows: kind, id, name, year, poster.
 */
class CatalogProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        // Unlocking the saved sign-in takes a moment, so only for a status check or a stale list
        // (each poster's IPTV badge asks here).
        val status = uri.lastPathSegment == "status"
        val login = if (status || CatalogIndex.isStale(context)) LoginStore.load(context) else null
        login?.let { CatalogIndex.refreshIfStale(context, it) }
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

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

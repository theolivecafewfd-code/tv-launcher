package dev.wiptv.player

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/** One film or series in the player's own title list. Never holds a stream address. */
data class IndexedTitle(
    val kind: VodKind,
    val id: Long,
    val name: String,
    val year: String?,
    val tmdb: Long?,
    val poster: String?,
    val extension: String?,
    val categoryId: String,
)

/**
 * The provider's films and series, kept on the TV in a small database and refreshed about once
 * a day, so the launcher can ask "do you have this title?" and search them without the player
 * downloading anything at that moment (see [CatalogProvider]). Names are stored with a matching
 * form ([normalise]) that drops language tags, years and quality labels.
 */
object CatalogIndex {
    private const val TAG = "CatalogIndex"
    private const val STALE_MS = 24 * 60 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var refreshing = false

    @Volatile
    private var helper: Helper? = null

    private fun db(context: Context): SQLiteDatabase =
        (helper ?: synchronized(this) { helper ?: Helper(context.applicationContext).also { helper = it } }).writableDatabase

    /** Whether the list is missing or a day old (cheap: no sign-in needed to ask). */
    fun isStale(context: Context): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences("catalog-index", Context.MODE_PRIVATE).getLong("refreshed", 0) >= STALE_MS

    /** Starts a refresh in the background when the list is missing or a day old. */
    fun refreshIfStale(context: Context, login: Login) {
        if (refreshing) return
        val prefs = context.getSharedPreferences("catalog-index", Context.MODE_PRIVATE)
        if (!isStale(context)) return
        refreshing = true
        scope.launch {
            try {
                for (kind in VodKind.entries) refresh(context, login, kind)
                prefs.edit().putLong("refreshed", System.currentTimeMillis()).remove("error").apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Keep the last good list; the next open of the player or the launcher tries again.
                // The reason is kept for the launcher's status line (never a server address).
                Log.w(TAG, "Refresh failed", e)
                val reason = if (e is XtreamException) e.message else e.javaClass.simpleName
                prefs.edit().putString("error", reason ?: "unknown error").apply()
            } finally {
                refreshing = false
            }
        }
    }

    /** How the list stands, for the launcher's IPTV status line. */
    data class Status(val films: Int, val series: Int, val refreshed: Long, val refreshing: Boolean, val error: String?)

    fun status(context: Context): Status {
        val prefs = context.getSharedPreferences("catalog-index", Context.MODE_PRIVATE)
        fun count(kind: VodKind): Int =
            db(context).rawQuery("SELECT COUNT(*) FROM titles WHERE kind = ?", arrayOf(kind.name)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        return Status(
            films = count(VodKind.MOVIE),
            series = count(VodKind.SERIES),
            refreshed = prefs.getLong("refreshed", 0),
            refreshing = refreshing,
            error = prefs.getString("error", null),
        )
    }

    /** Replaces one kind's titles. If the download fails part-way, the old ones stay. */
    private fun refresh(context: Context, login: Login, kind: VodKind) {
        val db = db(context)
        db.beginTransaction()
        try {
            db.delete("titles", "kind = ?", arrayOf(kind.name))
            val insert = db.compileStatement(
                "INSERT OR REPLACE INTO titles (kind, id, name, norm, year, tmdb, poster, ext, category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            )
            Xtream.forEachVod(login, kind) { t ->
                insert.clearBindings()
                insert.bindString(1, t.kind.name)
                insert.bindLong(2, t.id)
                insert.bindString(3, t.name)
                insert.bindString(4, normalise(t.name))
                (t.year ?: yearIn(t.name))?.let { insert.bindString(5, it) }
                t.tmdb?.let { insert.bindLong(6, it) }
                t.poster?.let { insert.bindString(7, it) }
                t.extension?.let { insert.bindString(8, it) }
                insert.bindString(9, t.categoryId)
                insert.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * The provider's entry for a title the launcher shows: by TMDB id when the provider gives
     * one, otherwise by matching name and a year no more than one apart. With no year on either
     * side, only an unambiguous name counts.
     */
    fun match(context: Context, kind: VodKind, title: String, year: String?, tmdb: Long?): IndexedTitle? {
        val db = db(context)
        if (tmdb != null) {
            db.query("titles", COLUMNS, "kind = ? AND tmdb = ?", arrayOf(kind.name, tmdb.toString()), null, null, null, "1")
                .use { c -> if (c.moveToFirst()) return read(c) }
        }
        val norm = normalise(title)
        if (norm.isEmpty()) return null
        val candidates = db.query("titles", COLUMNS, "kind = ? AND norm = ?", arrayOf(kind.name, norm), null, null, null, "20")
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }
        val wanted = year?.toIntOrNull()
        if (wanted != null) {
            candidates.firstOrNull { it.year?.toIntOrNull() == wanted }?.let { return it }
            candidates.firstOrNull { it.year?.toIntOrNull()?.let { y -> kotlin.math.abs(y - wanted) <= 1 } == true }?.let { return it }
        }
        val undated = candidates.filter { it.year == null }
        return if (undated.size == 1 && candidates.size == 1) undated.first() else null
    }

    /** Films and series whose name contains [query], names starting with it first. */
    fun search(context: Context, query: String, limit: Int = 24): List<IndexedTitle> {
        val norm = normalise(query)
        if (norm.length < 2) return emptyList()
        val like = norm.replace("%", "").replace("_", "")
        val found = db(context).rawQuery(
            "SELECT ${COLUMNS.joinToString()} FROM titles WHERE norm LIKE ? " +
                "ORDER BY (norm LIKE ?) DESC, length(norm), name LIMIT ?",
            arrayOf("%$like%", "$like%", (limit * 3).toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }
        // Providers often list the same title several times (HD, 4K, other categories).
        return found.distinctBy { Triple(it.kind, normalise(it.name), it.year) }.take(limit)
    }

    /** A random film or series with a poster, for the header's Surprise me; null if the list is empty. */
    fun random(context: Context): IndexedTitle? =
        db(context).rawQuery("SELECT ${COLUMNS.joinToString()} FROM titles WHERE poster IS NOT NULL ORDER BY RANDOM() LIMIT 1", null)
            .use { c -> if (c.moveToFirst()) read(c) else null }

    /**
     * "More like this" for a details page: other titles with posters from the same provider
     * category, in a random order, without repeats of the same name (HD, 4K copies).
     */
    fun similar(context: Context, kind: VodKind, id: Long, categoryId: String?, name: String, limit: Int = 12): List<IndexedTitle> {
        val category = categoryId?.ifBlank { null } ?: find(context, kind, id)?.categoryId?.ifBlank { null } ?: return emptyList()
        val own = normalise(name)
        return db(context).rawQuery(
            "SELECT ${COLUMNS.joinToString()} FROM titles WHERE kind = ? AND category = ? AND id != ? AND poster IS NOT NULL " +
                "ORDER BY RANDOM() LIMIT ?",
            arrayOf(kind.name, category, id.toString(), (limit * 3).toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }
            .filter { normalise(it.name) != own }
            .distinctBy { normalise(it.name) }
            .take(limit)
    }

    /** One title by its provider id, for opening it from the launcher. */
    fun find(context: Context, kind: VodKind, id: Long): IndexedTitle? =
        db(context).query("titles", COLUMNS, "kind = ? AND id = ?", arrayOf(kind.name, id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) read(c) else null }

    /**
     * A name in the form used for matching: no language tag ("EN - ", "|UK|"), year, quality
     * label or punctuation, lower case, accents removed, and no leading "the".
     */
    fun normalise(name: String): String {
        var s = name
        s = s.replace(Regex("""[(\[]\s*(19|20)\d{2}\s*[)\]]"""), " ")
        s = s.replace(Regex("""\s[-–|]\s*(19|20)\d{2}\s*$"""), " ")
        s = s.replace(Regex("""^\s*(\|[^|]*\||\[[^\]]*])\s*"""), "")
        s = s.replace(Regex("""^\s*[A-Z]{2,3}\s*[-:|]\s+"""), "")
        s = s.lowercase(Locale.ROOT)
        s = s.replace(Regex("""\b(4k|uhd|fhd|hd|sd|multi[- ]?sub|multi[- ]?audio)\b"""), " ")
        s = s.replace("&", " and ")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        s = s.replace(Regex("""[^a-z0-9]+"""), " ").trim()
        return s.removePrefix("the ")
    }

    /** A year at the end of a name, as in "Predator (1987)" or "Predator - 1987". */
    private fun yearIn(name: String): String? =
        Regex("""(?:[(\[]\s*|\s[-–|]\s*)((?:19|20)\d{2})\s*[)\]]?\s*$""").find(name)?.groupValues?.get(1)

    private val COLUMNS = arrayOf("kind", "id", "name", "year", "tmdb", "poster", "ext", "category")

    private fun read(c: Cursor) = IndexedTitle(
        kind = VodKind.valueOf(c.getString(0)),
        id = c.getLong(1),
        name = c.getString(2),
        year = c.getString(3),
        tmdb = if (c.isNull(4)) null else c.getLong(4),
        poster = c.getString(5),
        extension = c.getString(6),
        categoryId = c.getString(7) ?: "",
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "catalogue.db", null, 1) {
        init {
            // Lets the launcher's lookups read while a refresh is writing.
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE titles (kind TEXT NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL, norm TEXT NOT NULL, " +
                    "year TEXT, tmdb INTEGER, poster TEXT, ext TEXT, category TEXT, PRIMARY KEY (kind, id))",
            )
            db.execSQL("CREATE INDEX titles_norm ON titles (norm)")
            db.execSQL("CREATE INDEX titles_tmdb ON titles (kind, tmdb)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS titles")
            onCreate(db)
        }
    }
}

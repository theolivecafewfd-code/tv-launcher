package dev.wiptv.player

import androidx.compose.runtime.mutableStateMapOf

/**
 * What the player has already loaded from the provider, kept while it runs so switching between
 * Live TV, Movies and TV Shows doesn't download it all again: the categories, each category's
 * titles and films' details, Live TV's groups and their channels. One copy per account (so another
 * sign-in starts afresh), cleared after an hour so new titles turn up. Playback is never kept:
 * leaving Live TV still stops its stream.
 */
class SessionCache private constructor() {
    var categories: List<Group>? = null
    val items = mutableStateMapOf<String, List<VodItem>>()
    val details = mutableStateMapOf<Long, MovieDetails>()
    var liveGroups: List<Group>? = null
    val channels = mutableStateMapOf<String, List<Channel>>()
    private var started = System.currentTimeMillis()

    companion object {
        private const val KEEP_MS = 60 * 60_000L
        private val caches = mutableMapOf<String, SessionCache>()

        /** The copy for [login] and [section] ("MOVIE", "SERIES" or "LIVE"), fresh if it was stale. */
        fun of(login: Login, section: String): SessionCache {
            val key = "${login.server}|${login.username}|$section"
            val existing = caches[key]
            if (existing != null && System.currentTimeMillis() - existing.started < KEEP_MS) return existing
            return SessionCache().also { caches[key] = it }
        }
    }
}

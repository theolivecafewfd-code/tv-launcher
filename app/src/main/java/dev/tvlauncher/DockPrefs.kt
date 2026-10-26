package dev.tvlauncher

import android.content.Context

/**
 * Remembers which apps you've hidden from "Your apps" and the order you've put them in.
 * Stored on the device, so it survives restarts and app updates.
 */
class DockPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    fun setHidden(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, packages.toSet()).apply()
    }

    fun order(): List<String> =
        prefs.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun setOrder(packages: List<String>) {
        prefs.edit().putString(KEY_ORDER, packages.joinToString(",")).apply()
    }

    companion object {
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_ORDER = "order"

        /**
         * The apps to show in the dock: everything not hidden, in your saved order. Apps you
         * haven't placed yet (like newly installed ones) follow, in the default order.
         */
        fun arrange(all: List<TvApp>, hidden: Set<String>, order: List<String>): List<TvApp> {
            val defaultRank = all.withIndex().associate { (index, app) -> app.packageName to index }
            return all
                .filter { it.packageName !in hidden }
                .sortedBy { app ->
                    val saved = order.indexOf(app.packageName)
                    if (saved >= 0) saved else order.size + (defaultRank[app.packageName] ?: 0)
                }
        }
    }
}

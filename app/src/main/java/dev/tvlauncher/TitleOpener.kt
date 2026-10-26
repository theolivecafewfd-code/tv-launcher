package dev.tvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Opens a show or film in the first service you have that carries it. */
object TitleOpener {

    private const val TAG = "TvLauncherOpen"

    /**
     * Opens [title]. Returns a short message to show on screen, or null when it opened straight
     * to the title.
     */
    suspend fun open(context: Context, title: Title, apps: List<TvApp>, only: Source? = null): String? {
        History(context).add(title)
        for (source in if (only != null) listOf(only) else title.sources) {
            when (source) {
                Source.NETFLIX -> {
                    val app = apps.firstOrNull { it.label.lowercase().startsWith("netflix") } ?: continue
                    val id = Catalog.netflixId(title)
                    if (id != null && openNetflixTitle(context, app, id)) return null
                    start(context, app.intent)
                    return "Opening Netflix. Search for ${title.name}"
                }
                Source.PRIME -> {
                    val app = apps.firstOrNull { it.label.lowercase().startsWith("prime video") } ?: continue
                    start(context, app.intent)
                    return "Opening Prime Video. Search for ${title.name}"
                }
            }
        }
        return "${title.name} is on ${title.sources.joinToString { it.label }}, which isn't installed"
    }

    /** Plays a YouTube trailer in the YouTube app. Returns a message when it couldn't. */
    fun openTrailer(context: Context, youtubeKey: String, apps: List<TvApp>): String? {
        val app = apps.firstOrNull { it.label.lowercase().startsWith("youtube") }
            ?: return "Install YouTube to watch trailers"
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeKey")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey")),
        )
        for (intent in attempts) {
            try {
                start(context, intent.setPackage(app.packageName))
                return null
            } catch (e: Exception) {
                Log.w(TAG, "Trailer link ${intent.data} didn't open", e)
            }
        }
        return "Couldn't open the trailer in YouTube"
    }

    /** Netflix on Fire TV and Android TV opens a title from a netflix://title/<id> link. */
    private fun openNetflixTitle(context: Context, app: TvApp, id: String): Boolean {
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("netflix://title/$id"))
                .putExtra("amzn_deeplink_data", id),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/title/$id")),
        )
        for (intent in attempts) {
            try {
                start(context, intent.setPackage(app.packageName))
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Netflix link ${intent.data} didn't open", e)
            }
        }
        return false
    }

    private fun start(context: Context, intent: Intent) {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

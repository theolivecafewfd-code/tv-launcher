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
    suspend fun open(context: Context, title: Title, apps: List<TvApp>): String? {
        History(context).add(title)
        for (source in title.sources) {
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

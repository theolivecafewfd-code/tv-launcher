package dev.tvlauncher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One tile in the app dock. */
data class TvApp(
    val label: String,
    val packageName: String,
    val intent: Intent,
    /** The app's 16:9 TV banner when it has one, otherwise its square icon. */
    val image: ImageBitmap?,
    val isBanner: Boolean,
)

object AppRepository {

    /** Apps that always sit at the front of the dock, in this order, matched by name. */
    private val pinned = listOf("netflix", "youtube", "disney", "prime video", "now", "tivimate")

    /** Loads every launchable app on the TV. Call off the main thread: decoding banners is slow. */
    fun load(context: Context): List<TvApp> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, TvApp>()

        // TV apps first (they have proper banners), then phone-style apps not already found.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val activities = pm.queryIntentActivities(query, 0)
            for (info in activities) {
                val activity = info.activityInfo
                val pkg = activity.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) continue

                val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
                val image = runCatching {
                    if (banner != null) banner.toBitmap(320, 180) else info.loadIcon(pm).toBitmap(128, 128)
                }.getOrNull()?.asImageBitmap()

                found[pkg] = TvApp(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(pkg, activity.name),
                    image = image,
                    isBanner = banner != null,
                )
            }
        }

        val sorted = found.values.sortedWith(compareBy({ pinRank(it.label) }, { it.label.lowercase() }))

        // The TV's Settings app isn't listed as a normal app, so it gets its own tile at the end.
        val settings = TvApp(
            label = "Settings",
            packageName = "android.settings",
            intent = Intent(Settings.ACTION_SETTINGS),
            image = null,
            isBanner = false,
        )
        return sorted + settings
    }

    private fun pinRank(label: String): Int {
        val name = label.lowercase()
        val index = pinned.indexOfFirst { name.startsWith(it) }
        return if (index >= 0) index else pinned.size
    }
}

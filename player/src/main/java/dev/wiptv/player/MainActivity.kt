package dev.wiptv.player

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = PlayerApp.crashFile(application)
        if (crash.exists()) {
            showCrashReport(crash.readText()) {
                crash.delete()
                recreate()
            }
            return
        }

        // Another app (or the screenshot run) can open straight to Movies or Series.
        val section = intent.getStringExtra(EXTRA_SECTION)?.let { name -> Section.entries.firstOrNull { it.name == name } }
        // The launcher's "Watch on IPTV" opens one title: a film plays, a series opens its page.
        // When you're done with it, the player closes and you're back in the launcher.
        val resumeKey = intent.getStringExtra(EXTRA_OPEN_RESUME)
        val open = when {
            // The launcher's Continue watching: play that film or episode from where you stopped.
            resumeKey != null -> OpenTitle(if (resumeKey.startsWith("movie:")) VodKind.MOVIE else VodKind.SERIES, -1, resumeKey)
            else -> intent.getStringExtra(EXTRA_OPEN_KIND)?.let { k -> VodKind.entries.firstOrNull { it.name == k } }
                ?.let { kind -> intent.getLongExtra(EXTRA_OPEN_ID, -1).takeIf { it >= 0 }?.let { OpenTitle(kind, it) } }
        }
        // The launcher's channel row: that channel full screen; Back goes to its group in Live TV.
        val channel = intent.getLongExtra(EXTRA_OPEN_CHANNEL, -1).takeIf { it >= 0 }
        setContent {
            PlayerRoot(
                initialSection = when (open?.kind) {
                    VodKind.MOVIE -> Section.MOVIES
                    VodKind.SERIES -> Section.SERIES
                    null -> section ?: Section.LIVE
                },
                open = open,
                openChannel = channel.takeIf { open == null },
                onOpenFinished = { finish() },
            )
        }
    }

    companion object {
        /** "LIVE", "MOVIES" or "SERIES": the section to open on. */
        const val EXTRA_SECTION = "section"

        /** "MOVIE" or "SERIES", with [EXTRA_OPEN_ID]: a title to open straight away. */
        const val EXTRA_OPEN_KIND = "open_kind"
        const val EXTRA_OPEN_ID = "open_id"

        /** A Continue watching key ("movie:…" or "episode:…") to resume straight away. */
        const val EXTRA_OPEN_RESUME = "open_resume"

        /** A favourite channel's id: Live TV opens with it full screen. */
        const val EXTRA_OPEN_CHANNEL = "open_channel"
    }

    /** A plain (non-Compose) screen showing the last crash, so it still works if Compose crashed. */
    private fun showCrashReport(report: String, onRetry: () -> Unit) {
        val padding = (32 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply {
            text = "WIPTV Player crashed last time. Take a photo of this screen and send it to Claude."
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        val retry = Button(this).apply {
            text = "Clear this report and try again"
            setOnClickListener { onRetry() }
        }
        val details = TextView(this).apply {
            text = report
            setTextColor(Color.rgb(0xF2, 0xF4, 0xF8))
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(0x0B, 0x11, 0x1B))
            addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(retry, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = padding / 2 })
            addView(details, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = padding / 2 })
        }
        setContentView(ScrollView(this).apply { addView(column) })
        retry.requestFocus()
    }
}

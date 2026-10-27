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
        setContent { PlayerRoot(initialSection = section ?: Section.LIVE) }
    }

    companion object {
        /** "LIVE", "MOVIES" or "SERIES": the section to open on. */
        const val EXTRA_SECTION = "section"
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
            setTextColor(Color.rgb(0xE6, 0xE1, 0xEE))
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(0x16, 0x11, 0x1E))
            addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(retry, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = padding / 2 })
            addView(details, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = padding / 2 })
        }
        setContentView(ScrollView(this).apply { addView(column) })
        retry.requestFocus()
    }
}

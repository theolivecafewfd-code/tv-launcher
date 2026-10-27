package dev.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    /** Bumped every time the home screen comes back into view, so newly installed apps show up. */
    private val refreshKey = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = LauncherApp.crashFile(application)
        if (crash.exists()) {
            showCrashReport(crash.readText()) {
                crash.delete()
                recreate()
            }
            return
        }

        setContent {
            HomeScreen(
                refreshKey = refreshKey.intValue,
                onLaunch = ::launch,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey.intValue++
    }

    private fun launch(app: TvApp) {
        try {
            startActivity(Intent(app.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * A plain (non-Compose) screen showing the last crash, so it still works if Compose is
     * what crashed. Photograph it and send it over; the button clears it and tries again.
     */
    private fun showCrashReport(report: String, onRetry: () -> Unit) {
        val padding = (32 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "WIPTV crashed last time. Take a photo of this screen and send it to Claude."
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
            setPadding(0, padding / 2, 0, 0)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(padding, padding, padding, padding)
            addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(retry, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = padding / 2 })
            addView(details, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.rgb(0x0B, 0x11, 0x1B))
                addView(column)
            },
        )
        retry.requestFocus()
    }
}

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
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /** Bumped every time the home screen comes back into view, so newly installed apps show up. */
    private val refreshKey = mutableIntStateOf(0)

    /** WIPTV Player's header asking for the Apps tray or search ("apps", "search"); see [take]. */
    private val request = mutableStateOf<String?>(null)
    private val requestTick = mutableIntStateOf(0)

    /**
     * The player section that asked (LIVE, MOVIES or SERIES): backing out of the tray or search
     * goes back there. Forgotten as soon as the launcher leaves the screen (an app or title was
     * opened instead).
     */
    private val returnTo = mutableStateOf<String?>(null)

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

        take(intent)
        setContent {
            HomeScreen(
                refreshKey = refreshKey.intValue,
                onLaunch = ::launch,
                request = request.value,
                requestTick = requestTick.intValue,
                returnTo = returnTo.value,
                onReturn = ::backToPlayer,
            )
        }
    }

    // singleTask: the player's Apps and Search come back here as a new intent.
    /**
     * A known Compose bug: when the focused card's row goes away mid-scroll (Down pressed quickly
     * past a row that loaded empty), the focus can point at nothing, and the next key press throws
     * "ActiveParent must have a focusedChild". That press is dropped and the focus is reset (the
     * next press finds a card again) instead of the app crashing.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = try {
        super.dispatchKeyEvent(event)
    } catch (e: IllegalStateException) {
        if (e.message?.contains("focusedChild") != true) throw e
        android.util.Log.w("WIPTV", "Recovered from a lost focus", e)
        currentFocus?.let { view ->
            view.clearFocus()
            view.requestFocus()
        }
        true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        val wanted = intent?.getStringExtra(EXTRA_OPEN) ?: return
        intent.removeExtra(EXTRA_OPEN)
        returnTo.value = intent.getStringExtra(EXTRA_RETURN)
        intent.removeExtra(EXTRA_RETURN)
        request.value = wanted
        requestTick.intValue++
    }

    override fun onStop() {
        super.onStop()
        returnTo.value = null
    }

    /** Back to the player, as it was, on the section that asked. */
    private fun backToPlayer() {
        val section = returnTo.value ?: return
        returnTo.value = null
        if (!Iptv.backToPlayer(this, section)) Toast.makeText(this, "Couldn't open WIPTV Player", Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** "apps" or "search": what WIPTV Player's header asked to open. */
        const val EXTRA_OPEN = "wiptv_open"

        /** The player section to go back to when the tray or search is closed. */
        const val EXTRA_RETURN = "wiptv_return"
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

package dev.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    /** Bumped every time the home screen comes back into view, so newly installed apps show up. */
    private val refreshKey = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

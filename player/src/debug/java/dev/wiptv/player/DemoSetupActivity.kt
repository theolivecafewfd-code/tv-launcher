package dev.wiptv.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.net.URL

/**
 * Debug builds only. Signs in to the pretend provider used for screenshots, writes its demo
 * Continue watching and My List, then opens the player on the section asked for:
 *
 *   adb shell am start -n dev.wiptv.player/.DemoSetupActivity \
 *       --es server http://10.0.2.2:8000 --es scenario many --es section MOVIES
 */
class DemoSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val server = intent.getStringExtra("server") ?: "http://10.0.2.2:8000"
        val scenario = intent.getStringExtra("scenario") ?: "many"
        val section = intent.getStringExtra("section") ?: "LIVE"
        Thread {
            val seed = JSONObject(URL("$server/demo/seed.json?scenario=$scenario").readText())
            LoginStore.save(this, Login(seed.getString("server"), seed.getString("username"), seed.getString("password")))
            val store = VodStore(this)
            val resume = seed.getJSONArray("resume")
            // Oldest first, so the newest ends up at the front.
            for (i in resume.length() - 1 downTo 0) {
                val o = resume.getJSONObject(i)
                store.savePosition(
                    ResumePoint(
                        key = o.getString("key"),
                        title = o.getString("title"),
                        subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                        poster = o.optString("poster").takeIf { it.isNotBlank() },
                        url = o.getString("url"),
                        position = o.getLong("position"),
                        duration = o.getLong("duration"),
                        updated = o.getLong("updated"),
                        seriesId = o.optLong("seriesId", -1).takeIf { it >= 0 },
                        image = o.optString("image").takeIf { it.isNotBlank() },
                    ),
                )
            }
            val favourites = seed.getJSONArray("favourites")
            for (i in favourites.length() - 1 downTo 0) {
                val o = favourites.getJSONObject(i)
                store.toggleFavourite(
                    VodItem(
                        kind = VodKind.valueOf(o.getString("kind")),
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        poster = o.optString("poster").takeIf { it.isNotBlank() },
                        categoryId = o.optString("categoryId"),
                        rating = o.optDouble("rating").takeIf { !it.isNaN() },
                        extension = o.optString("extension").takeIf { it.isNotBlank() },
                    ),
                )
            }
            runOnUiThread {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SECTION, section)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                finish()
            }
        }.start()
    }
}

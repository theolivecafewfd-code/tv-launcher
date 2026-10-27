package dev.wiptv.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Where the weather is for. Town level only: nothing more precise is needed or stored. */
private object WeatherPlace {
    const val NAME = "Waterford"
    const val LATITUDE = 52.26
    const val LONGITUDE = -7.11
}

/** The kind of weather, which picks the icon. */
enum class Sky { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

data class WeatherNow(
    val temperature: Int,
    val sky: Sky,
    val description: String,
    /** A short line about tomorrow, e.g. "Showers likely tomorrow". */
    val outlook: String,
)

/** Current weather from Open-Meteo: free, and needs no key or account. */
object Weather {

    private const val TAG = "WiptvWeather"

    /** The last answer, kept while the app runs, so a new screen's header shows it at once. */
    @Volatile
    var last: WeatherNow? = null
        private set

    @Volatile
    private var lastAt = 0L

    /** The weather now; asked of Open-Meteo at most every half hour, however many headers ask. */
    suspend fun fetch(): WeatherNow? = withContext(Dispatchers.IO) {
        last?.takeIf { System.currentTimeMillis() - lastAt < 30 * 60_000L }?.let { return@withContext it }
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${WeatherPlace.LATITUDE}&longitude=${WeatherPlace.LONGITUDE}" +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,precipitation_probability_max,temperature_2m_max" +
                "&timezone=Europe%2FDublin&forecast_days=2"
            val json = JSONObject(get(url))
            val current = json.getJSONObject("current")
            val code = current.getInt("weather_code")
            val daily = json.getJSONObject("daily")
            val tomorrowCode = daily.getJSONArray("weather_code").getInt(1)
            val tomorrowRain = daily.getJSONArray("precipitation_probability_max").optInt(1, 0)
            val tomorrowHigh = daily.getJSONArray("temperature_2m_max").getDouble(1).roundToInt()
            WeatherNow(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                sky = skyFor(code),
                description = describe(code),
                outlook = outlook(tomorrowCode, tomorrowRain, tomorrowHigh),
            ).also {
                last = it
                lastAt = System.currentTimeMillis()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            null
        }
    }

    val placeName: String get() = WeatherPlace.NAME

    /** WMO weather codes, as used by Open-Meteo. */
    private fun skyFor(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2 -> Sky.PARTLY_CLOUDY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.THUNDER
        else -> Sky.CLOUDY
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorms"
        else -> "Cloudy"
    }

    private fun outlook(code: Int, rainChance: Int, high: Int): String {
        val sky = skyFor(code)
        return when {
            sky == Sky.THUNDER -> "Thunderstorms likely tomorrow"
            sky == Sky.SNOW -> "Snow likely tomorrow"
            rainChance >= 60 -> "Rain likely tomorrow, high of $high°"
            rainChance >= 30 -> "Chance of showers tomorrow, high of $high°"
            sky == Sky.CLEAR -> "Dry and bright tomorrow, high of $high°"
            else -> "Dry tomorrow, high of $high°"
        }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        try {
            if (connection.responseCode !in 200..299) error("Weather service answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** A small line-drawn weather icon, so it matches the app instead of using emoji. */
@Composable
fun WeatherIcon(sky: Sky, color: Color, size: Dp = 20.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)

        fun sun(cx: Float, cy: Float, r: Float) {
            drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)
            for (i in 0 until 8) {
                val angle = Math.toRadians(i * 45.0)
                val inner = r * 1.45f
                val outer = r * 1.85f
                drawLine(
                    color,
                    Offset(cx + inner * kotlin.math.cos(angle).toFloat(), cy + inner * kotlin.math.sin(angle).toFloat()),
                    Offset(cx + outer * kotlin.math.cos(angle).toFloat(), cy + outer * kotlin.math.sin(angle).toFloat()),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }

        fun cloud(top: Float) {
            // Two bumps on a rounded base.
            drawArc(color, 180f, 180f, false, Offset(w * 0.18f, top + w * 0.06f), Size(w * 0.34f, w * 0.34f), style = stroke)
            drawArc(color, 200f, 160f, false, Offset(w * 0.40f, top), Size(w * 0.44f, w * 0.44f), style = stroke)
            drawRoundRect(
                color,
                topLeft = Offset(w * 0.10f, top + w * 0.22f),
                size = Size(w * 0.80f, w * 0.26f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.13f),
                style = stroke,
            )
        }

        fun drops(top: Float, dots: Boolean) {
            for (i in 0 until 3) {
                val x = w * (0.30f + i * 0.20f)
                if (dots) {
                    drawCircle(color, radius = w * 0.045f, center = Offset(x, top + w * 0.08f))
                } else {
                    drawLine(color, Offset(x, top), Offset(x - w * 0.06f, top + w * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
        }

        when (sky) {
            Sky.CLEAR -> sun(w / 2, w / 2, w * 0.22f)
            Sky.PARTLY_CLOUDY -> {
                sun(w * 0.34f, w * 0.30f, w * 0.13f)
                cloud(w * 0.30f)
            }
            Sky.CLOUDY -> cloud(w * 0.22f)
            Sky.FOG -> {
                cloud(w * 0.08f)
                drawLine(color, Offset(w * 0.15f, w * 0.80f), Offset(w * 0.85f, w * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.25f, w * 0.94f), Offset(w * 0.75f, w * 0.94f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            Sky.DRIZZLE -> {
                cloud(w * 0.08f)
                drops(w * 0.72f, dots = true)
            }
            Sky.RAIN -> {
                cloud(w * 0.08f)
                drops(w * 0.70f, dots = false)
            }
            Sky.SNOW -> {
                cloud(w * 0.08f)
                drops(w * 0.72f, dots = true)
            }
            Sky.THUNDER -> {
                cloud(w * 0.08f)
                drawLine(color, Offset(w * 0.55f, w * 0.62f), Offset(w * 0.44f, w * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, w * 0.80f), Offset(w * 0.56f, w * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, w * 0.80f), Offset(w * 0.45f, w * 0.98f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

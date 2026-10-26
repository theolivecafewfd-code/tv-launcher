package dev.tvlauncher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What the update button in the header shows. It's always visible; pressing it acts on the state. */
sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class CheckFailed(val reason: String) : UpdateState
    data class Available(val build: Int, val apkUrl: String) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String, val retry: Available) : UpdateState
}

/**
 * Checks GitHub for a newer release of this app, downloads it and hands it to Android's installer.
 * Releases are tagged `build-N`, where N is the GitHub Actions run number, which is also this
 * app's versionCode, so a higher N means a newer app.
 */
object Updater {

    private const val TAG = "TvLauncherUpdate"
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    /** Asks GitHub for the newest release: [UpdateState.Available], [UpdateState.UpToDate] or [UpdateState.CheckFailed]. */
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(httpGetText(LATEST_RELEASE))
            val tag = json.getString("tag_name")
            val build = tag.removePrefix("build-").toIntOrNull()
                ?: return@withContext UpdateState.CheckFailed("unexpected release name $tag")
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == "tv-launcher.apk") {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            when {
                build <= BuildConfig.VERSION_CODE -> UpdateState.UpToDate
                apkUrl == null -> UpdateState.CheckFailed("build $build has no app file yet")
                else -> UpdateState.Available(build, apkUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            UpdateState.CheckFailed(describe(e))
        }
    }

    /** A short, readable reason for the header pill. */
    private fun describe(e: Exception): String {
        val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return when {
            e is java.net.UnknownHostException -> "no internet connection"
            e is java.net.SocketTimeoutException -> "GitHub didn't answer in time"
            message.contains("HTTP 403") -> "GitHub is limiting checks, try later"
            else -> message.substringBefore(" from ").take(60)
        }
    }

    /** True when the user has allowed this app to install updates. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where the user allows this app to install updates. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Toast.makeText(context, "Turn on WIPTV, then press Back and try the update again", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Fire OS hides this screen; the setting lives in Developer options instead.
            Toast.makeText(
                context,
                "Allow WIPTV under Settings → My Fire TV → Developer options → Install unknown apps",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Downloads the new build, reporting progress, then starts the install. */
    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateState.Available,
        onProgress: (Int) -> Unit,
    ) {
        val apk = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "tv-launcher-${update.build}.apk")
            download(update.apkUrl, file, onProgress)
            file
        }
        withContext(Dispatchers.IO) { install(context, apk) }
    }

    private fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = open(url)
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = (copied * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Installs with Android's PackageInstaller. Android then shows its own "Install?" confirmation,
     * which the user has to accept: apps outside an app store can't skip that step.
     */
    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("tv-launcher.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                flags,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun httpGetText(url: String): String {
        val connection = open(url, accept = "application/vnd.github+json")
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens a connection, following redirects by hand (GitHub sends downloads to another host).
     * Headers must be set here, before the connection is made: Android refuses them afterwards.
     */
    private fun open(url: String, accept: String? = null): HttpURLConnection {
        var current = url
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "TvLauncher/${BuildConfig.VERSION_NAME}")
                if (accept != null) setRequestProperty("Accept", accept)
            }
            when (val code = connection.responseCode) {
                in 300..399 -> {
                    current = connection.getHeaderField("Location") ?: error("Redirect without a location")
                    connection.disconnect()
                }
                in 200..299 -> return connection
                else -> {
                    connection.disconnect()
                    error("HTTP $code from $current")
                }
            }
        }
        error("Too many redirects")
    }
}

/** Receives the installer's progress: shows the confirmation screen, or reports a failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The app restarts on the new version.
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error $status"
                Toast.makeText(context, "Update didn't install: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}

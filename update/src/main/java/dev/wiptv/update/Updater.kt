package dev.wiptv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The two WIPTV apps, and where each one's newest build is published in the repo. */
enum class WiptvApp(
    val packageName: String,
    val label: String,
    val apkName: String,
    /** The GitHub API path of the app's release. */
    val releasePath: String,
    /** A small file in the release holding just its build number, e.g. "112". */
    val buildFileName: String,
) {
    /** The launcher: a new `build-N` release per merge, always the repo's "latest". */
    LAUNCHER("dev.tvlauncher", "WIPTV", "tv-launcher.apk", "releases/latest", "tv-launcher.build.txt"),

    /** The player: one `player` release, replaced per merge, named "WIPTV Player build N". */
    PLAYER("dev.wiptv.player", "WIPTV Player", "wiptv-player.apk", "releases/tags/player", "wiptv-player.build.txt");

    /**
     * The build file's download link. Release downloads aren't counted against GitHub's API
     * limit (60 checks an hour per home connection), so this is what the pill reads first.
     */
    fun buildFileUrl(repo: String): String = when (this) {
        LAUNCHER -> "https://github.com/$repo/releases/latest/download/$buildFileName"
        PLAYER -> "https://github.com/$repo/releases/download/player/$buildFileName"
    }

    /** The APK of [build], as a plain download link. */
    fun apkUrl(repo: String, build: Int): String = when (this) {
        LAUNCHER -> "https://github.com/$repo/releases/download/build-$build/$apkName"
        PLAYER -> "https://github.com/$repo/releases/download/player/$apkName"
    }

    /** The build number a release stands for (the GitHub Actions run number = versionCode). */
    fun buildOf(release: JSONObject): Int? = when (this) {
        LAUNCHER -> release.optString("tag_name").removePrefix("build-").toIntOrNull()
        PLAYER -> Regex("(\\d+)\\s*$").find(release.optString("name"))?.groupValues?.get(1)?.toIntOrNull()
    }
}

/** A newer build of one app, ready to download. */
data class AppUpdate(val app: WiptvApp, val build: Int, val apkUrl: String)

/** What the update pill shows. It's always visible; pressing it acts on the state. */
sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class CheckFailed(val reason: String) : UpdateState

    /** One or both apps have a newer build. */
    data class Available(val updates: List<AppUpdate>) : UpdateState
    data class Downloading(val percent: Int, val app: String = "") : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String, val retry: Available) : UpdateState
}

/** The pill's words. [installedBuild] is this app's own build, shown when all is up to date. */
fun UpdateState.label(installedBuild: Int): String = when (this) {
    UpdateState.Checking -> "Checking for updates…"
    UpdateState.UpToDate -> "Up to date · build $installedBuild"
    is UpdateState.CheckFailed -> "Couldn't check: $reason"
    is UpdateState.Available -> updates.singleOrNull()?.let { "Update available · ${it.app.label} build ${it.build}" }
        ?: "${updates.size} updates available · WIPTV and the player"
    is UpdateState.Downloading -> "Downloading ${app.ifBlank { "update" }}… $percent%"
    UpdateState.Installing -> "Installing…"
    is UpdateState.Failed -> "Update failed · press to retry"
}

/** True when the pill needs you (something to install, or a failure to retry). */
val UpdateState.loud: Boolean get() = this is UpdateState.Available || this is UpdateState.Failed

/**
 * Checks GitHub for newer builds of both WIPTV apps, downloads them and hands them to Android's
 * installer: the other app first, then this one, because updating this app restarts it. Only
 * apps already on the TV are updated; nothing new is installed.
 */
object Updater {

    private const val TAG = "WiptvUpdate"

    /** Checks both apps against [repo] ("owner/name"). */
    suspend fun check(context: Context, repo: String): UpdateState = withContext(Dispatchers.IO) {
        val updates = mutableListOf<AppUpdate>()
        var failure: Exception? = null
        var checked = 0
        for (app in WiptvApp.entries) {
            val installed = installedBuild(context, app.packageName) ?: continue
            try {
                // The build file first; the API only if it's missing (releases from before it existed).
                val newest = try {
                    fromBuildFile(context, repo, app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.i(TAG, "No build file for ${app.label}, asking the API", e)
                    fromApi(context, repo, app)
                }
                checked++
                if (newest == null || newest.build <= installed) continue
                updates += newest
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't check ${app.label}", e)
                if (failure == null) failure = e
            }
        }
        when {
            updates.isNotEmpty() -> UpdateState.Available(inInstallOrder(context, updates))
            checked == 0 && failure != null -> UpdateState.CheckFailed(describe(failure!!))
            else -> UpdateState.UpToDate
        }
    }

    /** The newest build from the release's build file. */
    private fun fromBuildFile(context: Context, repo: String, app: WiptvApp): AppUpdate {
        val text = httpGetText(context, app.buildFileUrl(repo))
        val build = text.trim().toIntOrNull() ?: error("Unreadable build file")
        return AppUpdate(app, build, app.apkUrl(repo, build))
    }

    /** The newest build from GitHub's API, or null while a build is still being published. */
    private fun fromApi(context: Context, repo: String, app: WiptvApp): AppUpdate? {
        val release = JSONObject(
            httpGetText(context, "https://api.github.com/repos/$repo/${app.releasePath}", accept = "application/vnd.github+json"),
        )
        val build = app.buildOf(release) ?: return null
        val assets = release.getJSONArray("assets")
        val url = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == app.apkName }
            ?.optString("browser_download_url")
            ?: return null // a build still being published: next check will see it
        return AppUpdate(app, build, url)
    }

    /** True when the user has allowed this app to install updates. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where the user allows this app to install updates. */
    fun openInstallPermissionSettings(context: Context) {
        val name = context.applicationInfo.loadLabel(context.packageManager)
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Toast.makeText(context, "Turn on $name, then press Back and try the update again", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Fire OS hides this screen; the setting lives in Developer options instead.
            Toast.makeText(
                context,
                "Allow $name under Settings → My Fire TV → Developer options → Install unknown apps",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Downloads every update, then installs them one after another (this app last). */
    suspend fun downloadAndInstall(
        context: Context,
        target: UpdateState.Available,
        onProgress: (app: String, percent: Int) -> Unit,
    ) {
        val files = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            target.updates.map { update ->
                val file = File(dir, "${update.app.name.lowercase()}-${update.build}.apk")
                download(context, update.apkUrl, file) { onProgress(update.app.label, it) }
                update.app.packageName to file
            }
        }
        withContext(Dispatchers.IO) { installChain(context, files) }
    }

    /** Installs the first file; the receiver installs the rest as each one succeeds. */
    internal fun installChain(context: Context, files: List<Pair<String, File>>) {
        val (packageName, apk) = files.firstOrNull() ?: return
        val rest = ArrayList(files.drop(1).map { (pkg, file) -> "$pkg|${file.absolutePath}" })
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite(apk.name, 0, apk.length()).use { output ->
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
                Intent(context, InstallResultReceiver::class.java).putStringArrayListExtra(EXTRA_NEXT, rest),
                flags,
            )
            session.commit(callback.intentSender)
        }
    }

    /** The other app first, this app last (installing this one restarts it). */
    private fun inInstallOrder(context: Context, updates: List<AppUpdate>): List<AppUpdate> =
        updates.sortedBy { if (it.app.packageName == context.packageName) 1 else 0 }

    /** The installed build of a package, or null when it isn't on the TV. */
    private fun installedBuild(context: Context, packageName: String): Long? = try {
        context.packageManager.getPackageInfo(packageName, 0).longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** A short, readable reason for the pill. */
    private fun describe(e: Exception): String {
        val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return when {
            e is java.net.UnknownHostException -> "no internet connection"
            e is java.net.SocketTimeoutException -> "GitHub didn't answer in time"
            message.contains("HTTP 403") -> "GitHub is limiting checks, try later"
            else -> message.substringBefore(" from ").take(60)
        }
    }

    private fun download(context: Context, url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = open(context, url)
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

    private fun httpGetText(context: Context, url: String, accept: String? = null): String {
        val connection = open(context, url, accept = accept)
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
    private fun open(context: Context, url: String, accept: String? = null): HttpURLConnection {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        var current = url
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "WIPTV/${version ?: "dev"}")
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

    internal const val EXTRA_NEXT = "dev.wiptv.update.NEXT"
}

/**
 * Receives the installer's progress: shows the confirmation screen, starts the next app's install
 * when one finishes, or reports a failure.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // On to the next app, if there is one (this app always comes last).
                val next = intent.getStringArrayListExtra(Updater.EXTRA_NEXT).orEmpty().mapNotNull { entry ->
                    val (pkg, path) = entry.split('|', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                    pkg to File(path)
                }
                if (next.isEmpty()) return
                val pending = goAsync()
                Thread {
                    try {
                        Updater.installChain(context.applicationContext, next)
                    } catch (e: Exception) {
                        Log.w("WiptvUpdate", "Next install failed", e)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error $status"
                Toast.makeText(context, "Update didn't install: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}

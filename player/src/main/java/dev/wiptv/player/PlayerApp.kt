package dev.wiptv.player

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Saves the details of any crash to a file, so the next launch can show them on screen
 * (the same approach as the launcher: there's no easy way to read the TV's error log).
 */
class PlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                crashFile(this).writeText(
                    buildString {
                        appendLine("App version: ${BuildConfig.VERSION_NAME}")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(Log.getStackTraceString(error))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(app: Application): File = File(app.filesDir, "last-crash.txt")
    }
}

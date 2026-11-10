package com.chatnook.app.data.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.chatnook.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on disk so the person can send it after the fact.
 *
 * No paid crash service and no network call: the trace is written to internal storage,
 * read once on the next launch, and deleted as soon as it is shown.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(app.filesDir, FILE).writeText(
                    buildString {
                        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        appendLine("${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}")
                        appendLine("ChatNook ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine()
                        append(Log.getStackTraceString(error))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last trace, or null. Always deletes, so a report is offered exactly once. */
    fun take(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text
    }
}

package com.grateful.deadly.feature.settings.screens.bugreport

import android.content.Context
import android.os.Build
import com.grateful.deadly.feature.settings.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pulls the recent logcat buffer for *this* process and returns it as plain text
 * suitable for sharing in a bug report — the Android analog of iOS's `LogExport`.
 *
 * On Android 4.1+ a third-party app's `logcat` invocation only ever sees its own
 * process's log entries (no READ_LOGS permission and none needed), so the dump is
 * already scoped to us — no PID/package filtering required.
 *
 * Only logs that were actually written (`Log.i`/`w`/`e`/`d`) are present; the
 * window is bounded with `-t <time>` so we ship roughly the last hour, mirroring
 * the iOS export window.
 */
object BugReportExporter {

    /** Default lookback window, in seconds, matching the iOS export. */
    const val DEFAULT_WINDOW_SECONDS = 3600L

    /**
     * Read the last [windowSeconds] of this process's logcat output, prefixed with
     * a self-describing header. If [filterContains] is non-null, only lines
     * containing that substring are kept.
     */
    fun exportRecentLogs(
        windowSeconds: Long = DEFAULT_WINDOW_SECONDS,
        filterContains: String? = null,
    ): String {
        val header = header(windowSeconds)

        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            .format(Date(System.currentTimeMillis() - windowSeconds * 1000))

        val body = try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "threadtime", "-t", since
            ).redirectErrorStream(true).start()

            process.inputStream.bufferedReader().useLines { lines ->
                val kept = lines
                    .filter { filterContains == null || it.contains(filterContains) }
                    .toList()
                process.waitFor()
                if (kept.isEmpty()) "(no log entries in the selected window)"
                else kept.joinToString("\n")
            }
        } catch (e: Exception) {
            "Couldn't read logs: ${e.message}"
        }

        val count = if (body.startsWith("(") || body.startsWith("Couldn't")) 0
        else body.count { it == '\n' } + 1

        return buildString {
            append(header)
            append('\n')
            append(body)
            append("\n---\nCaptured ")
            append(count)
            append(" entries.")
        }
    }

    /** Write [text] to a shareable file in the cache and return it. */
    fun writeToCache(context: Context, text: String): File {
        val dir = File(context.cacheDir, "bugreports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "deadly-logs-$stamp.txt")
        file.writeText(text)
        return file
    }

    private fun header(windowSeconds: Long): String {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
        return buildString {
            append("=== Deadly Bug Report ===\n")
            append("Generated: ").append(now).append('\n')
            append("App: deadly ").append(BuildConfig.VERSION_NAME).append('\n')
            append("OS: Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Window: last ").append(windowSeconds).append("s\n")
            append("---")
        }
    }
}

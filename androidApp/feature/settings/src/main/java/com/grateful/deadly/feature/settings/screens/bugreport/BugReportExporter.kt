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

    /** Max lines shown in the on-screen preview. The full log is still sent/shared. */
    const val PREVIEW_MAX_LINES = 500

    /** Max characters shown in the on-screen preview (~100KB). */
    const val PREVIEW_MAX_CHARS = 100_000

    /**
     * Max characters put on the clipboard. A clip crosses Binder as UTF-16, and a
     * transaction over ~1MB throws TransactionTooLargeException, so stay well under.
     */
    const val CLIPBOARD_MAX_CHARS = 250_000

    /**
     * A bounded tail of the log for on-screen display. Rendering the full log
     * (several MB) in one Compose `Text` exceeds the max layout height and ANRs.
     */
    data class LogPreview(
        val text: String,
        val shownLines: Int,
        val totalLines: Int,
        val isTruncated: Boolean,
    )

    /**
     * Return the last [maxLines] lines of [text], or fewer if they exceed
     * [maxChars] characters (whichever is smaller). A single line longer than
     * [maxChars] keeps only its last [maxChars] characters. Call off the main thread.
     */
    fun previewTail(
        text: String,
        maxLines: Int = PREVIEW_MAX_LINES,
        maxChars: Int = PREVIEW_MAX_CHARS,
    ): LogPreview {
        if (text.isEmpty()) return LogPreview("", 0, 0, isTruncated = false)
        val totalLines = text.count { it == '\n' } + 1

        // Walk back from the end, one line at a time, until a limit is hit.
        var tailStart = text.length
        var shown = 0
        var searchFrom = text.length - 1
        while (shown < maxLines) {
            val newline = if (searchFrom >= 0) text.lastIndexOf('\n', searchFrom) else -1
            val lineStart = newline + 1
            if (text.length - lineStart > maxChars) {
                if (shown == 0) {
                    // One very long last line: keep only its tail.
                    return LogPreview(text.substring(text.length - maxChars), 1, totalLines, isTruncated = true)
                }
                break
            }
            tailStart = lineStart
            shown++
            if (newline < 0) break
            searchFrom = newline - 1
        }
        return LogPreview(text.substring(tailStart), shown, totalLines, isTruncated = tailStart > 0)
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

package com.grateful.deadly.core.network.genius.service

import android.content.Context
import android.util.Log
import com.grateful.deadly.core.network.genius.api.GeniusApi
import com.grateful.deadly.core.network.genius.di.Genius
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class GeniusServiceImpl @Inject constructor(
    @Named("geniusAccessToken") private val apiKey: String,
    @ApplicationContext private val context: Context,
    private val geniusApi: GeniusApi,
    @Genius private val okHttpClient: OkHttpClient
) : GeniusService {

    companion object {
        private const val TAG = "GeniusService"
        private const val CACHE_DIR_NAME = "genius"
        private const val CACHE_EXPIRY_DAYS = 30
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
    }

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    override suspend fun getLyrics(songTitle: String, artist: String): String? {
        if (apiKey.isBlank() || songTitle.isBlank()) return null

        val cacheKey = "${songTitle}_$artist".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.txt")

        // Check cache
        if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
            val cached = cacheFile.readText()
            return cached.ifBlank { null }
        }

        val lyrics = fetchLyrics(songTitle, artist)

        // Cache result (empty string for negative cache)
        cacheFile.writeText(lyrics ?: "")
        return lyrics
    }

    private suspend fun fetchLyrics(songTitle: String, artist: String): String? {
        return try {
            // Search Genius for the song
            val query = "$songTitle $artist"
            val response = geniusApi.search(query, "Bearer $apiKey")
            if (!response.isSuccessful || response.body() == null) {
                Log.w(TAG, "Genius search failed: ${response.code()}")
                return null
            }

            val hits = response.body()!!.response.hits
            if (hits.isEmpty()) {
                Log.d(TAG, "No Genius results for: $query")
                return null
            }

            val songUrl = hits[0].result?.url
            if (songUrl.isNullOrBlank()) {
                Log.d(TAG, "No URL in Genius result for: $query")
                return null
            }

            Log.d(TAG, "Fetching lyrics from: $songUrl")
            scrapeLyrics(songUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics for: $songTitle", e)
            null
        }
    }

    private suspend fun scrapeLyrics(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch lyrics page: ${response.code}")
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null

            // Strip data-exclude-from-selection blocks (contributors header, etc.)
            val cleaned = stripExcludeBlocks(html)

            // Extract text from lyrics container divs
            val containerRegex = Regex(
                """<div[^>]*data-lyrics-container="true"[^>]*>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val lyricsBuilder = StringBuilder()

            for (match in containerRegex.findAll(cleaned)) {
                val contentStart = match.range.last + 1
                val content = extractDivContent(cleaned, contentStart)
                val text = content
                    .replace(Regex("<br\\s*/?>"), "\n")
                    .replace(HTML_TAG_REGEX, "")
                    .trim()
                if (text.isNotBlank()) {
                    if (lyricsBuilder.isNotEmpty()) lyricsBuilder.append("\n\n")
                    lyricsBuilder.append(text)
                }
            }

            val lyrics = lyricsBuilder.toString()
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .trim()

            if (lyrics.isNotBlank()) {
                Log.d(TAG, "Scraped ${lyrics.length} chars of lyrics")
                lyrics
            } else {
                Log.d(TAG, "No lyrics content found in page")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping lyrics", e)
            null
        }
    }

    /**
     * Remove all data-exclude-from-selection blocks (e.g. contributors header)
     * by tracking div nesting depth so we don't break on inner divs.
     */
    private fun stripExcludeBlocks(html: String): String {
        val marker = "data-exclude-from-selection=\"true\""
        val result = StringBuilder(html.length)
        var i = 0
        while (i < html.length) {
            val excludeStart = html.indexOf(marker, i)
            if (excludeStart == -1) {
                result.append(html, i, html.length)
                break
            }
            // Walk back to find the opening <div
            val divStart = html.lastIndexOf("<div", excludeStart)
            result.append(html, i, divStart)
            // Find the end of this opening tag
            val tagEnd = html.indexOf('>', excludeStart)
            if (tagEnd == -1) break
            // Track depth to find matching </div>
            var depth = 1
            var pos = tagEnd + 1
            while (pos < html.length && depth > 0) {
                val nextOpen = html.indexOf("<div", pos)
                val nextClose = html.indexOf("</div>", pos)
                if (nextClose == -1) break
                if (nextOpen != -1 && nextOpen < nextClose) {
                    depth++
                    pos = nextOpen + 4
                } else {
                    depth--
                    pos = nextClose + 6
                }
            }
            i = pos
        }
        return result.toString()
    }

    /**
     * Extract content of a div starting after the opening tag,
     * tracking nesting depth to find the matching closing tag.
     */
    private fun extractDivContent(html: String, startIndex: Int): String {
        var depth = 1
        var pos = startIndex
        while (pos < html.length && depth > 0) {
            val nextOpen = html.indexOf("<div", pos)
            val nextClose = html.indexOf("</div>", pos)
            if (nextClose == -1) break
            if (nextOpen != -1 && nextOpen < nextClose) {
                depth++
                pos = nextOpen + 4
            } else {
                depth--
                if (depth == 0) return html.substring(startIndex, nextClose)
                pos = nextClose + 6
            }
        }
        return html.substring(startIndex)
    }

    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryMs = CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() > timestamp + expiryMs
    }
}

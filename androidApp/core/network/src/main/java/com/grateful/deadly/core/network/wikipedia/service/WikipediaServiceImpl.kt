package com.grateful.deadly.core.network.wikipedia.service

import android.content.Context
import android.util.Log
import com.grateful.deadly.core.network.wikipedia.api.WikipediaApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikipediaServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wikipediaApi: WikipediaApi
) : WikipediaService {

    companion object {
        private const val TAG = "WikipediaService"
        private const val CACHE_DIR_NAME = "wikipedia"
        private const val CACHE_EXPIRY_DAYS = 7
    }

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    override suspend fun getVenueSummary(venueName: String, city: String?): String? {
        if (venueName.isBlank()) return null

        val cacheKey = "${venueName}_${city.orEmpty()}".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.txt")

        // Check cache
        if (cacheFile.exists() && !isCacheExpired(cacheFile.lastModified())) {
            val cached = cacheFile.readText()
            // Empty file means we previously found no result — don't retry
            return cached.ifBlank { null }
        }

        val extract = tryDirectLookup(venueName)
            ?: trySearchLookup(venueName, city)

        // Cache result (empty string for negative cache)
        cacheFile.writeText(extract ?: "")
        return extract
    }

    private suspend fun tryDirectLookup(venueName: String): String? {
        return try {
            val encoded = URLEncoder.encode(venueName.replace(" ", "_"), "UTF-8")
            val response = wikipediaApi.getPageSummary(encoded)
            if (response.isSuccessful) {
                val body = response.body()
                val extract = body?.extract?.takeIf { it.isNotBlank() }
                if (extract != null) {
                    Log.d(TAG, "Direct lookup hit for: $venueName")
                }
                extract
            } else {
                Log.d(TAG, "Direct lookup miss for: $venueName (${response.code()})")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct lookup failed for: $venueName", e)
            null
        }
    }

    private suspend fun trySearchLookup(venueName: String, city: String?): String? {
        return try {
            val query = listOfNotNull(venueName, city).joinToString(" ")
            val response = wikipediaApi.search(query = query)
            if (!response.isSuccessful || response.body() == null) return null

            // opensearch returns: ["query", ["Title1", ...], ["Desc1", ...], ["Url1", ...]]
            val jsonArray = response.body() as? JsonArray ?: return null
            if (jsonArray.size < 2) return null

            val titles = jsonArray[1].jsonArray
            if (titles.isEmpty()) {
                Log.d(TAG, "Search returned no results for: $query")
                return null
            }

            val firstTitle = titles[0].jsonPrimitive.content
            Log.d(TAG, "Search found: $firstTitle for query: $query")

            // Fetch the summary for the found article
            val encoded = URLEncoder.encode(firstTitle.replace(" ", "_"), "UTF-8")
            val summaryResponse = wikipediaApi.getPageSummary(encoded)
            if (summaryResponse.isSuccessful) {
                summaryResponse.body()?.extract?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search lookup failed for: $venueName", e)
            null
        }
    }

    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryMs = CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() > timestamp + expiryMs
    }
}

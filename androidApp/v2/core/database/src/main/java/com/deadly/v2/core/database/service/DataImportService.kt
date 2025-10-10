package com.deadly.v2.core.database.service

import android.util.Log
import com.deadly.v2.core.model.V2Database
import com.deadly.v2.core.database.dao.DataVersionDao
import com.deadly.v2.core.database.dao.ShowDao
import com.deadly.v2.core.database.dao.ShowSearchDao
import com.deadly.v2.core.database.dao.RecordingDao
import com.deadly.v2.core.database.entities.DataVersionEntity
import com.deadly.v2.core.database.entities.ShowEntity
import com.deadly.v2.core.database.entities.ShowSearchEntity
import com.deadly.v2.core.database.entities.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ShowImportData(
    @SerialName("show_id") val showId: String,
    val band: String,
    val venue: String,
    @SerialName("location_raw") val locationRaw: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = "USA",
    val date: String,
    val url: String? = null,
    @SerialName("setlist_status") val setlistStatus: String? = null,
    val setlist: JsonElement? = null, // Raw JSON (can be null, string, or complex object/array)
    @SerialName("lineup_status") val lineupStatus: String? = null,
    val lineup: List<LineupMember>? = null,
    @SerialName("supporting_acts_status") val supportingActsStatus: String? = null,
    @SerialName("supporting_acts") val supportingActs: JsonElement? = null, // Raw JSON (can be null, string, or array)
    @SerialName("collection_timestamp") val collectionTimestamp: String? = null,
    @SerialName("show_time") val showTime: String? = null,
    val recordings: List<String> = emptyList(), // List of recording IDs
    @SerialName("best_recording") val bestRecording: String? = null,
    @SerialName("avg_rating") val avgRating: Double = 0.0,
    @SerialName("recording_count") val recordingCount: Int = 0,
    val confidence: Double = 0.0,
    @SerialName("source_types") val sourceTypes: Map<String, Int> = emptyMap(),
    @SerialName("matching_method") val matchingMethod: String? = null,
    @SerialName("filtering_applied") val filteringApplied: List<String> = emptyList(),
    val collections: List<String> = emptyList()
)

@Serializable
data class LineupMember(
    val name: String,
    val instruments: String,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class RecordingImportData(
    val rating: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("source_type") val sourceType: String? = null,
    val confidence: Double = 0.0,
    val date: String,
    val venue: String,
    val location: String,
    @SerialName("raw_rating") val rawRating: Double = 0.0,
    @SerialName("high_ratings") val highRatings: Int = 0,
    @SerialName("low_ratings") val lowRatings: Int = 0,
    val tracks: List<TrackData> = emptyList(),
    val taper: String? = null,
    val source: String? = null,
    val lineage: String? = null
)

@Serializable
data class TrackData(
    val track: String,
    val title: String,
    val duration: Double = 0.0,
    val formats: List<TrackFormatData> = emptyList()
)

@Serializable
data class TrackFormatData(
    val format: String,
    val filename: String,
    val bitrate: String? = null
)


@Singleton
class DataImportService @Inject constructor(
    @V2Database private val showDao: ShowDao,
    @V2Database private val showSearchDao: ShowSearchDao,
    @V2Database private val recordingDao: RecordingDao,
    @V2Database private val dataVersionDao: DataVersionDao,
    private val collectionsImportService: CollectionsImportService
) {
    
    companion object {
        private const val TAG = "DataImportService"
    }
    
    /**
     * Import data from extracted V2 directories with progress tracking
     */
    suspend fun importFromExtractedFiles(
        showsDirectory: File,
        recordingsDirectory: File,
        progressCallback: ((ImportProgress) -> Unit)? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting V2 data import from directories")
            
            progressCallback?.invoke(ImportProgress("READING_DIRECTORIES", 0, 0, "Scanning directories..."))
            
            // Get all show and recording files
            val showFiles = showsDirectory.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()
            val recordingFiles = recordingsDirectory.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()
            
            Log.d(TAG, "Found ${showFiles.size} show files and ${recordingFiles.size} recording files")
            
            progressCallback?.invoke(ImportProgress("READING_FILES", 0, showFiles.size + recordingFiles.size, "Preparing data files..."))
            
            // Clear existing data
            progressCallback?.invoke(ImportProgress("CLEARING", 0, 0, "Clearing existing data..."))
            showSearchDao.clearAllSearchData()
            recordingDao.deleteAllRecordings()
            showDao.deleteAll()
            dataVersionDao.deleteAll()
            
            var importedShows = 0
            var importedRecordings = 0
            
            // Read and parse all show files
            val showsMap = mutableMapOf<String, ShowImportData>()
            var showParseSuccessCount = 0
            var showParseFailureCount = 0
            
            showFiles.forEachIndexed { index, file ->
                progressCallback?.invoke(
                    ImportProgress(
                        "READING_SHOWS", 
                        index + 1, 
                        showFiles.size, 
                        "Processing show data..."
                    )
                )
                
                Log.d(TAG, "Processing show file ${index + 1}/${showFiles.size}: ${file.name}")
                
                try {
                    val showJson = file.readText()
                    val json = Json { ignoreUnknownKeys = true }
                    val showData = json.decodeFromString<ShowImportData>(showJson)
                    showsMap[showData.showId] = showData
                    showParseSuccessCount++
                    Log.d(TAG, "✅ Successfully parsed show: ${showData.showId}")
                } catch (e: Exception) {
                    showParseFailureCount++
                    Log.e(TAG, "❌ Failed to parse show file: ${file.name}", e)
                }
            }
            
            Log.i(TAG, "Show parsing summary: $showParseSuccessCount successful, $showParseFailureCount failed out of ${showFiles.size} total files")
            
            // Parse recording files
            val recordingsMap = mutableMapOf<String, RecordingImportData>()
            var recordingParseSuccessCount = 0
            var recordingParseFailureCount = 0
            
            recordingFiles.forEachIndexed { index, file ->
                progressCallback?.invoke(
                    ImportProgress(
                        "READING_RECORDINGS", 
                        index + 1, 
                        recordingFiles.size, 
                        "Processing recording data..."
                    )
                )
                
                Log.d(TAG, "Processing recording file ${index + 1}/${recordingFiles.size}: ${file.name}")
                
                try {
                    val recordingJson = file.readText()
                    val json = Json { ignoreUnknownKeys = true }
                    val recordingData = json.decodeFromString<RecordingImportData>(recordingJson)
                    val recordingId = file.nameWithoutExtension // Use filename as recording ID
                    recordingsMap[recordingId] = recordingData
                    recordingParseSuccessCount++
                    Log.d(TAG, "✅ Successfully parsed recording: $recordingId")
                } catch (e: Exception) {
                    recordingParseFailureCount++
                    Log.e(TAG, "❌ Failed to parse recording file: ${file.name}", e)
                }
            }
            
            Log.i(TAG, "Recording parsing summary: $recordingParseSuccessCount successful, $recordingParseFailureCount failed out of ${recordingFiles.size} total files")
            
            Log.d(TAG, "Parsed ${showsMap.size} shows and ${recordingsMap.size} recordings")
            
            // Import shows
            var showDbSuccessCount = 0
            var showDbFailureCount = 0
            
            showsMap.values.forEachIndexed { index, showData ->
                progressCallback?.invoke(
                    ImportProgress(
                        "IMPORTING_SHOWS", 
                        index + 1, 
                        showsMap.size, 
                        "Creating show entries..."
                    )
                )
                
                Log.d(TAG, "Inserting show to database ${index + 1}/${showsMap.size}: ${showData.showId}")
                
                try {
                    // Create ShowEntity from V2 data
                    val showEntity = createShowEntity(showData, recordingsMap)
                    showDao.insert(showEntity)
                    importedShows++
                    showDbSuccessCount++
                    
                    // Create search index entry
                    val searchEntity = createSearchEntity(showData)
                    showSearchDao.insertOrUpdate(searchEntity)
                    
                    Log.d(TAG, "✅ Successfully inserted show: ${showData.showId}")
                    
                } catch (e: Exception) {
                    showDbFailureCount++
                    Log.e(TAG, "❌ Failed to insert show to database: ${showData.showId}", e)
                }
            }
            
            Log.i(TAG, "Show database insertion summary: $showDbSuccessCount successful, $showDbFailureCount failed out of ${showsMap.size} parsed shows")
            
            // Import recordings (only those referenced by shows)
            var recordingDbSuccessCount = 0
            var recordingDbFailureCount = 0
            var recordingsNotInShows = 0
            
            recordingsMap.entries.forEachIndexed { index, (recordingId, recordingData) ->
                progressCallback?.invoke(
                    ImportProgress(
                        "IMPORTING_RECORDINGS", 
                        index + 1, 
                        recordingsMap.size, 
                        "Creating recording entries..."
                    )
                )
                
                Log.d(TAG, "Processing recording ${index + 1}/${recordingsMap.size}: $recordingId")
                
                // Find which show(s) reference this recording
                val referencingShows = showsMap.values.filter { show ->
                    show.recordings.contains(recordingId)
                }
                
                if (referencingShows.isNotEmpty()) {
                    // Recording is referenced by at least one show, import it
                    referencingShows.forEach { show ->
                        try {
                            val recordingEntity = createRecordingEntity(recordingId, recordingData, show.showId)
                            recordingDao.insertRecording(recordingEntity)
                            importedRecordings++
                            recordingDbSuccessCount++
                            Log.d(TAG, "✅ Successfully inserted recording: $recordingId for show: ${show.showId}")
                        } catch (e: Exception) {
                            recordingDbFailureCount++
                            Log.e(TAG, "❌ Failed to insert recording: $recordingId for show: ${show.showId}", e)
                        }
                    }
                } else {
                    // Recording not referenced by any show
                    recordingsNotInShows++
                    Log.d(TAG, "⚠️ Recording $recordingId not referenced by any show, skipping")
                }
            }
            
            Log.i(TAG, "Recording database insertion summary: $recordingDbSuccessCount successful, $recordingDbFailureCount failed, $recordingsNotInShows not referenced by shows")
            
            if (recordingsNotInShows > 0) {
                Log.w(TAG, "⚠️ Found $recordingsNotInShows recordings not referenced by any show - this may indicate data inconsistency")
            }
            
            // Import collections if available
            var collectionsImported = 0
            try {
                progressCallback?.invoke(ImportProgress("IMPORTING_COLLECTIONS", 0, 1, "Importing collections..."))
                
                val collectionsResult = collectionsImportService.importCollectionsFromFile(File(showsDirectory.parent ?: showsDirectory.absolutePath))
                when (collectionsResult) {
                    is CollectionsImportResult.Success -> {
                        collectionsImported = collectionsResult.importedCount
                        Log.i(TAG, "Collections import successful: ${collectionsResult.message}")
                    }
                    is CollectionsImportResult.Error -> {
                        Log.w(TAG, "Collections import failed: ${collectionsResult.error}")
                        // Don't fail the entire import for collections failure
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Collections import exception (continuing anyway)", e)
            }
            
            progressCallback?.invoke(ImportProgress("FINALIZING", importedShows + importedRecordings, importedShows + importedRecordings, "Finalizing database..."))
            
            // Update data version
            val currentTime = System.currentTimeMillis()
            dataVersionDao.insertOrUpdate(
                DataVersionEntity(
                    id = 1,
                    dataVersion = "2.0.0",
                    packageName = "Deadly Metadata",
                    versionType = "release", 
                    description = "V2 database import from extracted files with collections",
                    importedAt = currentTime,
                    gitCommit = null,
                    gitTag = null,
                    buildTimestamp = null,
                    totalShows = importedShows,
                    totalVenues = 0,
                    totalFiles = importedRecordings,
                    totalSizeBytes = 0L
                )
            )
            
            progressCallback?.invoke(ImportProgress("COMPLETED", importedShows + importedRecordings, importedShows + importedRecordings, "Import completed successfully"))
            
            Log.d(TAG, "Data import completed successfully: $importedShows shows, $importedRecordings recordings, $collectionsImported collections")
            
            ImportResult(
                success = true,
                importedShows = importedShows,
                importedRecordings = importedRecordings,
                message = "Successfully imported $importedShows shows, $importedRecordings recordings, and $collectionsImported collections"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Data import failed", e)
            ImportResult(
                success = false,
                importedShows = 0,
                importedRecordings = 0,
                message = "Import failed: ${e.message}"
            )
        }
    }
    
    
    private fun createShowEntity(showData: ShowImportData, recordingsMap: Map<String, RecordingImportData>): ShowEntity {
        // Parse date components
        val dateParts = showData.date.split("-")
        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt()
        val yearMonth = "${year}-${dateParts[1]}"
        
        // Extract song list from setlist JSON for FTS
        val songList = extractSongListFromSetlist(showData.setlist)
        
        // Extract member list from lineup for FTS
        val memberList = extractMemberListFromLineup(showData.lineup)
        
        // Use the pre-built recording data from V2 show data
        val recordingCount = showData.recordingCount
        val averageRating = showData.avgRating.toFloat()
        val bestRecordingId = showData.bestRecording
        
        // Calculate total reviews from source types (approximation)
        val totalReviews = showData.sourceTypes.values.sum()
        
        val currentTime = System.currentTimeMillis()
        
        return ShowEntity(
            showId = showData.showId,
            date = showData.date,
            year = year,
            month = month,
            yearMonth = yearMonth,
            band = showData.band,
            url = showData.url,
            venueName = showData.venue,
            city = showData.city,
            state = showData.state,
            country = showData.country ?: "USA",
            locationRaw = showData.locationRaw,
            setlistStatus = showData.setlistStatus,
            setlistRaw = showData.setlist?.toString(), // Convert JsonElement to raw JSON string
            songList = songList, // Extracted flat song list for FTS
            lineupStatus = showData.lineupStatus,
            lineupRaw = showData.lineup?.let { Json.encodeToString(it) }, // Convert lineup to JSON string
            memberList = memberList, // Extracted flat member list for FTS
            showSequence = 1,
            recordingsRaw = if (showData.recordings.isNotEmpty()) Json.encodeToString(showData.recordings) else null, // JSON array of recording IDs
            recordingCount = recordingCount, // From V2 data
            bestRecordingId = bestRecordingId, // From V2 data
            averageRating = averageRating, // From V2 data
            totalReviews = totalReviews, // Approximated from source types
            isInLibrary = false,
            libraryAddedAt = null,
            createdAt = currentTime,
            updatedAt = currentTime
        )
    }
    
    /**
     * Extract flat member list from lineup for FTS
     */
    private fun extractMemberListFromLineup(lineup: List<LineupMember>?): String? {
        return lineup?.mapNotNull { member ->
            member.name.takeIf { it.isNotBlank() }
        }?.joinToString(",")
    }
    
    /**
     * Extract flat song list from setlist JsonElement for FTS
     */
    private fun extractSongListFromSetlist(setlistElement: JsonElement?): String? {
        return try {
            when {
                setlistElement == null -> null
                setlistElement.toString() == "null" -> null
                else -> {
                    // Try to parse as JSON array (V2 format)
                    val setlistArray = setlistElement.jsonArray
                    val songs = mutableListOf<String>()
                    
                    for (setElement in setlistArray) {
                        val setObject = setElement.jsonObject
                        val songsArray = setObject["songs"]?.jsonArray
                        
                        songsArray?.forEach { songElement ->
                            val songObject = songElement.jsonObject
                            val songName = songObject["name"]?.jsonPrimitive?.content
                            if (!songName.isNullOrBlank()) {
                                songs.add(songName)
                            }
                        }
                    }
                    
                    if (songs.isNotEmpty()) songs.joinToString(",") else null
                }
            }
        } catch (e: Exception) {
            // If parsing fails, setlist might be a string or malformed - just return null
            Log.d("DataImportService", "Could not parse setlist for song extraction: ${e.message}")
            null
        }
    }
    
    private fun createSearchEntity(showData: ShowImportData): ShowSearchEntity {
        // Combine all searchable text for FTS
        val searchableText = buildList {
            // Enhanced date indexing for comprehensive search support
            add(showData.date) // Original: "1977-05-08"
            
            // Parse date components for alternative formats
            val dateParts = showData.date.split("-")
            val year = dateParts[0]      // "1977"
            val month = dateParts[1]     // "05"
            val day = dateParts[2]       // "08"
            
            // Core date components
            add(year)                    // "1977"
            add(year.takeLast(2))        // "77"

            // Original delimiters you were using: -, /, now adding .
            val delimiters = listOf("-", "/", ".")

            // Day / month / year formats
            delimiters.forEach { delim ->
                add("${month.toInt()}$delim${day.toInt()}$delim${year.takeLast(2)}")  // 5-8-77, 5/8/77, 5.8.77
                add("${month}$delim${day}$delim${year}")                               // 05-08-1977, 05/08/1977, 05.08.1977
                add("${year}$delim${month.toInt()}$delim${day.toInt()}")               // 1977-5-8, 1977/5/8, 1977.5.8
            }

            // Month / year formats
            delimiters.forEach { delim ->
                add("${month.toInt()}$delim${year.takeLast(2)}")  // 5-77, 5/77, 5.77
                add("${year}$delim${month}")                      // 1977-05, 1977/05, 1977.05
                add("${year}$delim${month.toInt()}")             // 1977-5, 1977/5, 1977.5
                add("${year.takeLast(2)}$delim${month.toInt()}") // 77-5, 77/5, 77.5
            }

            // Century prefix for decade searches
            add(year.take(3))            // "197" (enables 1970s searches)
            
            // Venue information
            add(showData.venue)
            
            // Consolidated location (avoid redundancy)
            showData.locationRaw?.let { add(it) } // Single location field: "Ithaca, NY"
            
            // Band member names (no instruments - reduces noise)
            val memberList = extractMemberListFromLineup(showData.lineup)
            if (!memberList.isNullOrBlank()) {
                add(memberList.replace(",", " ")) // "Jerry Garcia Bob Weir Phil Lesh"
            }
            
            // Song list for setlist searches
            val songList = extractSongListFromSetlist(showData.setlist)
            if (!songList.isNullOrBlank()) {
                add(songList.replace(",", " ")) // "Scarlet Begonias Fire On The Mountain"
            }
        }.joinToString(" ")
        
        return ShowSearchEntity(rowid = 0, showId = showData.showId, searchText = searchableText)
    }
    
    private fun createRecordingEntity(recordingId: String, recordingData: RecordingImportData, showId: String): RecordingEntity {
        return RecordingEntity(
            identifier = recordingId,
            showId = showId,
            sourceType = recordingData.sourceType,
            rating = recordingData.rating,
            rawRating = recordingData.rawRating,
            reviewCount = recordingData.reviewCount,
            confidence = recordingData.confidence,
            highRatings = recordingData.highRatings,
            lowRatings = recordingData.lowRatings,
            taper = recordingData.taper,
            source = recordingData.source,
            lineage = recordingData.lineage,
            sourceTypeString = recordingData.sourceType, // Use sourceType as sourceTypeString
            collectionTimestamp = System.currentTimeMillis()
        )
    }
    
}

data class ImportResult(
    val success: Boolean,
    val importedShows: Int,
    val importedRecordings: Int,
    val message: String
)

/**
 * Progress information for data import
 */
data class ImportProgress(
    val phase: String,
    val processedItems: Int,
    val totalItems: Int,
    val currentItem: String
) {
    val progressPercentage: Float
        get() = if (totalItems > 0) (processedItems.toFloat() / totalItems.toFloat()) * 100f else 0f
}
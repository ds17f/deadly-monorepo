package com.deadly.v2.core.network.archive.mapper

import com.deadly.v2.core.model.Review
import com.deadly.v2.core.model.RecordingMetadata  
import com.deadly.v2.core.model.Track
import com.deadly.v2.core.network.archive.model.ArchiveMetadataResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting Archive.org API responses to V2 domain models
 */
@Singleton
class ArchiveMapper @Inject constructor() {
    
    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "wma")
    }
    
    /**
     * Convert Archive API response to RecordingMetadata domain model
     */
    fun mapToRecordingMetadata(response: ArchiveMetadataResponse): RecordingMetadata {
        val metadata = response.metadata
        val audioTracks = response.files.filter { isAudioFile(it.name) }
        
        return RecordingMetadata(
            identifier = metadata?.identifier ?: "",
            title = metadata?.title ?: "",
            date = metadata?.date,
            venue = metadata?.venue,
            description = metadata?.description,
            setlist = metadata?.setlist,
            source = metadata?.source,
            taper = metadata?.taper,
            transferer = metadata?.transferer,
            lineage = metadata?.lineage,
            totalTracks = audioTracks.size,
            totalReviews = response.reviews?.size ?: 0
        )
    }
    
    /**
     * Convert Archive API response to list of Track domain models
     */
    fun mapToTracks(response: ArchiveMetadataResponse): List<Track> {
        return response.files
            .filter { isAudioFile(it.name) }
            .mapIndexed { index, file ->
                Track(
                    name = file.name,
                    title = file.title ?: extractTitleFromFilename(file.name),
                    trackNumber = file.track?.toIntOrNull() ?: (index + 1),
                    duration = file.length,
                    format = file.format,
                    size = file.size,
                    bitrate = file.bitrate,
                    sampleRate = file.sampleRate,
                    isAudio = true
                )
            }
            .sortedBy { it.trackNumber }
    }
    
    /**
     * Convert Archive API response to list of Review domain models
     */
    fun mapToReviews(response: ArchiveMetadataResponse): List<Review> {
        return response.reviews?.map { review ->
            Review(
                reviewer = review.reviewer,
                title = review.title,
                body = review.body,
                rating = review.stars,
                reviewDate = review.reviewDate
            )
        } ?: emptyList()
    }
    
    /**
     * Check if a file is an audio file based on extension
     */
    private fun isAudioFile(filename: String): Boolean {
        val extension = filename.lowercase().substringAfterLast(".", "")
        return extension in AUDIO_EXTENSIONS
    }
    
    /**
     * Extract song title from filename for tracks without title metadata
     */
    private fun extractTitleFromFilename(filename: String): String {
        // Remove common prefixes and extensions
        return filename
            .substringBeforeLast(".")
            .removePrefix("gd")
            .removePrefix("grateful_dead")
            .replace(Regex("^\\d{4}-\\d{2}-\\d{2}"), "") // Remove date prefix
            .replace(Regex("^d\\dt\\d+\\."), "") // Remove disc/track prefix
            .replace("_", " ")
            .trim()
            .takeIf { it.isNotBlank() } ?: filename
    }
}
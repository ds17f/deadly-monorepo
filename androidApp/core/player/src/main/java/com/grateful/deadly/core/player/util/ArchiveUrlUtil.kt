package com.grateful.deadly.core.player.util

import com.grateful.deadly.core.model.Recording
import com.grateful.deadly.core.model.Track

/**
 * Archive URL Utility for generating Archive.org URLs for recordings and tracks
 */
object ArchiveUrlUtil {
    
    private const val ARCHIVE_BASE_URL = "https://archive.org/details/"
    
    /**
     * Generate Archive.org URL for a recording
     * @param recording The recording to generate URL for
     * @return URL string for the recording on Archive.org
     */
    fun getRecordingUrl(recording: Recording): String {
        return "$ARCHIVE_BASE_URL${recording.identifier}"
    }
    
    /**
     * Generate Archive.org URL for a specific track within a recording
     * @param recording The recording containing the track
     * @param track The specific track to link to
     * @return URL string for the track on Archive.org with track parameter
     */
    fun getTrackUrl(recording: Recording, track: Track): String {
        val baseUrl = getRecordingUrl(recording)
        val filename = getArchiveFilename(recording, track.name)
        return if (filename.isNotBlank()) {
            "$baseUrl/$filename"
        } else {
            baseUrl
        }
    }
    
    /**
     * Generate Archive.org URL for a track with time offset
     * @param recording The recording containing the track
     * @param track The specific track to link to
     * @param timeOffsetSeconds Optional time offset within the track
     * @return URL string for the track with time parameter
     */
    fun getTrackUrlWithTime(recording: Recording, track: Track, timeOffsetSeconds: Long? = null): String {
        val baseUrl = getRecordingUrl(recording)
        val filename = getArchiveFilename(recording, track.name)
        
        return when {
            timeOffsetSeconds != null && timeOffsetSeconds > 0 && filename.isNotBlank() -> {
                // Archive.org accepts time parameters with the file and #start/seconds format
                "$baseUrl/$filename#start/$timeOffsetSeconds"
            }
            filename.isNotBlank() -> {
                "$baseUrl/$filename"
            }
            else -> baseUrl
        }
    }
    
    /**
     * Convert filename to match Archive.org's actual file format based on recording type
     */
    private fun getArchiveFilename(recording: Recording, filename: String): String {
        if (filename.isBlank()) return filename
        
        // Determine the actual file format from the recording identifier
        val actualExtension = when {
            recording.identifier.contains(".shnf") -> "shn"
            recording.identifier.contains(".flac") -> "flac"
            recording.identifier.contains(".mp3") -> "mp3"
            else -> {
                // Default logic: if soundboard/audience mentioned, likely SHN
                when (recording.sourceType.displayName.lowercase()) {
                    "sbd", "aud" -> "shn"
                    else -> "mp3"
                }
            }
        }
        
        // Replace the extension in the filename
        val nameWithoutExtension = filename.substringBeforeLast('.')
        return "$nameWithoutExtension.$actualExtension"
    }
}
package com.deadly.v2.core.media.exception

/**
 * Exception thrown when requested audio format is not available for a recording
 * 
 * Provides detailed information for debugging and user feedback when
 * MediaControllerRepository cannot find tracks in the requested format.
 */
class FormatNotAvailableException(
    message: String,
    val recordingId: String,
    val requestedFormat: String,
    val availableFormats: List<String>
) : Exception("$message. Available formats: $availableFormats") {
    
    /**
     * Convenience constructor with formatted message
     */
    constructor(
        recordingId: String,
        requestedFormat: String, 
        availableFormats: List<String>
    ) : this(
        message = "Format '$requestedFormat' not available for recording $recordingId",
        recordingId = recordingId,
        requestedFormat = requestedFormat,
        availableFormats = availableFormats
    )
}
package com.grateful.deadly.core.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

object ShowArtworkService {
    private val sourceTypes = ConcurrentHashMap<String, RecordingSourceType>()

    var badgeStyle: SourceBadgeStyle by mutableStateOf(SourceBadgeStyle.LONG)

    fun sourceType(recordingId: String): RecordingSourceType? = sourceTypes[recordingId]

    fun populate(entries: Map<String, RecordingSourceType>) {
        sourceTypes.putAll(entries)
    }

    fun clear() {
        sourceTypes.clear()
    }
}

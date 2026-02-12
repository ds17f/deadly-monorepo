package com.deadly.v2.core.design.component.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data models for organizing debug information in a structured way.
 * These models provide a consistent format for displaying debug data across the app.
 */

/**
 * Represents a section of related debug information
 */
data class DebugSection(
    val title: String,
    val items: List<DebugItem>
) {
    /**
     * Formats this section as text for copying
     */
    fun toFormattedText(): String {
        val builder = StringBuilder()
        builder.appendLine("=== $title ===")
        items.forEach { item ->
            builder.appendLine(item.toFormattedText())
        }
        builder.appendLine()
        return builder.toString()
    }
}

/**
 * Sealed class representing different types of debug information
 */
sealed class DebugItem {
    abstract fun toFormattedText(): String
    
    /**
     * Simple key-value pair
     */
    data class KeyValue(val key: String, val value: String) : DebugItem() {
        override fun toFormattedText(): String = "$key: $value"
    }
    
    /**
     * Multi-line text data
     */
    data class Multiline(val key: String, val value: String) : DebugItem() {
        override fun toFormattedText(): String {
            return "$key:\n${value.lines().joinToString("\n") { "  $it" }}"
        }
    }
    
    /**
     * Error information with optional stack trace
     */
    data class Error(val message: String, val stackTrace: String? = null) : DebugItem() {
        override fun toFormattedText(): String {
            return if (stackTrace != null) {
                "ERROR: $message\nStack Trace:\n${stackTrace.lines().joinToString("\n") { "  $it" }}"
            } else {
                "ERROR: $message"
            }
        }
    }
    
    /**
     * Timestamp with label
     */
    data class Timestamp(val label: String, val time: Long) : DebugItem() {
        private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        
        override fun toFormattedText(): String {
            return "$label: ${formatter.format(Date(time))}"
        }
        
        fun getFormattedTime(): String = formatter.format(Date(time))
    }
    
    /**
     * JSON or structured data
     */
    data class JsonData(val key: String, val json: String) : DebugItem() {
        override fun toFormattedText(): String {
            return "$key:\n${json.lines().joinToString("\n") { "  $it" }}"
        }
    }
    
    /**
     * Boolean value with visual indicator
     */
    data class BooleanValue(val key: String, val value: Boolean) : DebugItem() {
        override fun toFormattedText(): String = "$key: $value"
        
        fun getStatusEmoji(): String = if (value) "✅" else "❌"
    }
    
    /**
     * Numeric value with optional unit
     */
    data class NumericValue(val key: String, val value: Number, val unit: String = "") : DebugItem() {
        override fun toFormattedText(): String = "$key: $value$unit"
    }
}

/**
 * Container for all debug data from a screen
 */
data class DebugData(
    val screenName: String,
    val sections: List<DebugSection>,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Formats all debug data as text for copying
     */
    fun toFormattedText(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val builder = StringBuilder()
        
        builder.appendLine("DEBUG DATA - $screenName")
        builder.appendLine("Generated: ${formatter.format(Date(timestamp))}")
        builder.appendLine("=" * 50)
        builder.appendLine()
        
        sections.forEach { section ->
            builder.append(section.toFormattedText())
        }
        
        return builder.toString()
    }
    
    /**
     * Gets total number of debug items across all sections
     */
    fun getTotalItemCount(): Int = sections.sumOf { it.items.size }
    
    /**
     * Gets sections that contain errors
     */
    fun getErrorSections(): List<DebugSection> {
        return sections.filter { section ->
            section.items.any { it is DebugItem.Error }
        }
    }
}

/**
 * Helper functions for creating common debug items
 */
object DebugItemFactory {
    
    fun createStateItem(key: String, value: Any?): DebugItem {
        return when (value) {
            null -> DebugItem.KeyValue(key, "null")
            is Boolean -> DebugItem.BooleanValue(key, value)
            is Number -> DebugItem.NumericValue(key, value)
            is String -> if (value.contains('\n')) {
                DebugItem.Multiline(key, value)
            } else {
                DebugItem.KeyValue(key, value)
            }
            else -> DebugItem.KeyValue(key, value.toString())
        }
    }
    
    fun createTimestamp(label: String): DebugItem.Timestamp {
        return DebugItem.Timestamp(label, System.currentTimeMillis())
    }
    
    fun createErrorItem(exception: Throwable): DebugItem.Error {
        return DebugItem.Error(
            message = exception.message ?: "Unknown error",
            stackTrace = exception.stackTraceToString()
        )
    }
    
    fun createErrorItem(message: String, throwable: Throwable? = null): DebugItem.Error {
        return DebugItem.Error(
            message = message,
            stackTrace = throwable?.stackTraceToString()
        )
    }
}

/**
 * Extension function for String repetition (used in formatting)
 */
private operator fun String.times(count: Int): String {
    return this.repeat(count)
}
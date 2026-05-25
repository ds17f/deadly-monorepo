package com.grateful.deadly.core.model

/**
 * Which transport controls are exposed on the notification, lock screen, and Android Auto.
 */
enum class PlayerControlsStyle(val label: String) {
    SKIP_TRACK("Tracks"),
    SKIP_SECONDS("10s"),
    BOTH("Both");

    companion object {
        fun fromString(value: String?): PlayerControlsStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SKIP_TRACK
    }
}

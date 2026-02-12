package com.grateful.deadly.feature.splash.model

/**
 * Database initialization phases for progress tracking
 */
enum class Phase {
    IDLE,
    CHECKING,
    USING_LOCAL,
    DOWNLOADING,
    EXTRACTING,
    IMPORTING_SHOWS,
    COMPUTING_VENUES,
    IMPORTING_RECORDINGS,
    COMPLETED,
    ERROR
}
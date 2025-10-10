package com.deadly.v2.feature.splash.model

/**
 * V2 database initialization phases for progress tracking
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
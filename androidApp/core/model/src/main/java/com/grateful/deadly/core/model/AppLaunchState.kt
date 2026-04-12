package com.grateful.deadly.core.model

/**
 * Process-lifetime launch state. Shared between app and feature modules
 * without introducing circular dependencies.
 */
object AppLaunchState {
    /** True only during the first launch of the process. Cleared after the tooltip is consumed. */
    var isColdLaunch = true
}

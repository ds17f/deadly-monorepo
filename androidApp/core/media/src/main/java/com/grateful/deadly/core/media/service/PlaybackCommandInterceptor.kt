package com.grateful.deadly.core.media.service

/**
 * Intercepts playback commands before they reach ExoPlayer.
 * Return true to consume the command (prevent ExoPlayer from handling it),
 * false to let ExoPlayer handle it normally.
 */
interface PlaybackCommandInterceptor {
    fun onPlay(): Boolean
    fun onPause(): Boolean
    fun onSeekTo(positionMs: Long): Boolean
    fun onSeekToNext(): Boolean
    fun onSeekToPrevious(): Boolean
}

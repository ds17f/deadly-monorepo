package com.grateful.deadly.core.media

import androidx.media3.common.MediaMetadata

internal const val GRATEFUL_DEAD_ARTIST = "Grateful Dead"

/**
 * Android System UI and Android Auto render title + artist, while scrobblers also
 * interpret artist semantically. The user preference chooses which audience wins.
 */
internal fun MediaMetadata.Builder.setPlaybackMetadata(
    title: CharSequence?,
    showLabel: CharSequence,
    scrobblingMetadata: Boolean,
): MediaMetadata.Builder =
    setTitle(title)
        .setArtist(if (scrobblingMetadata) GRATEFUL_DEAD_ARTIST else showLabel)
        .setAlbumTitle(showLabel)
        .setDisplayTitle(title)
        .setSubtitle(showLabel)

package com.grateful.deadly.core.media

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMetadataTest {
    @Test
    fun `sets canonical metadata for scrobblers`() {
        val metadata = MediaMetadata.Builder()
            .setPlaybackMetadata(
                "Scarlet Begonias",
                "May 8, 1977 - Barton Hall",
                scrobblingMetadata = true,
            )
            .build()

        assertEquals("Scarlet Begonias", metadata.title)
        assertEquals("Grateful Dead", metadata.artist)
        assertEquals("May 8, 1977 - Barton Hall", metadata.albumTitle)
    }

    @Test
    fun `preserves show context in display metadata`() {
        val metadata = MediaMetadata.Builder()
            .setPlaybackMetadata(
                "Scarlet Begonias",
                "May 8, 1977 - Barton Hall",
                scrobblingMetadata = false,
            )
            .build()

        assertEquals("Scarlet Begonias", metadata.displayTitle)
        assertEquals("May 8, 1977 - Barton Hall", metadata.subtitle)
    }
    @Test
    fun `show details style publishes the show label as artist`() {
        val metadata = MediaMetadata.Builder()
            .setPlaybackMetadata(
                "Scarlet Begonias",
                "May 8, 1977 - Barton Hall",
                scrobblingMetadata = false,
            )
            .build()

        assertEquals("May 8, 1977 - Barton Hall", metadata.artist)
        assertEquals("May 8, 1977 - Barton Hall", metadata.albumTitle)
    }
}

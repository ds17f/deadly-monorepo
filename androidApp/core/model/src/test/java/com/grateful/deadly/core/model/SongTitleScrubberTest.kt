package com.grateful.deadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTitleScrubberTest {

    // --- scrub() tests ---

    @Test
    fun `strip leading track number with space`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("02 Scarlet Begonias"))
    }

    @Test
    fun `strip leading track number with dot`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("02. Scarlet Begonias"))
    }

    @Test
    fun `strip leading track number with dash`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("02-Scarlet Begonias"))
    }

    @Test
    fun `strip trailing segue marker`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("Scarlet Begonias >"))
    }

    @Test
    fun `strip trailing double segue marker`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("Scarlet Begonias >>"))
    }

    @Test
    fun `strip leading segue marker`() {
        assertEquals("Fire on the Mountain", SongTitleScrubber.scrub("> Fire on the Mountain"))
    }

    @Test
    fun `strip bracketed suffix`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("Scarlet Begonias [10:23]"))
    }

    @Test
    fun `strip parenthesized duration`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("Scarlet Begonias (10:23)"))
    }

    @Test
    fun `inline segue takes first segment`() {
        assertEquals("Scarlet", SongTitleScrubber.scrub("Scarlet > Fire"))
    }

    @Test
    fun `inline segue with track number`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("05 Scarlet Begonias > Fire on the Mountain"))
    }

    @Test
    fun `no change for clean title`() {
        assertEquals("Playing in the Band", SongTitleScrubber.scrub("Playing in the Band"))
    }

    @Test
    fun `combined track number and trailing segue`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("02 Scarlet Begonias >"))
    }

    @Test
    fun `blank input returns empty`() {
        assertEquals("", SongTitleScrubber.scrub(""))
        assertEquals("", SongTitleScrubber.scrub("   "))
    }

    @Test
    fun `normalizes extra whitespace`() {
        assertEquals("Scarlet Begonias", SongTitleScrubber.scrub("  Scarlet   Begonias  "))
    }

    @Test
    fun `preserves parenthesized non-duration`() {
        assertEquals("The Other One (jam)", SongTitleScrubber.scrub("The Other One (jam)"))
    }

    // --- matchesSetlistSong() tests ---

    @Test
    fun `exact match case insensitive`() {
        assertTrue(SongTitleScrubber.matchesSetlistSong("scarlet begonias", "Scarlet Begonias"))
    }

    @Test
    fun `partial match - scrubbed title contained in setlist name`() {
        assertTrue(SongTitleScrubber.matchesSetlistSong("Scarlet", "Scarlet Begonias"))
    }

    @Test
    fun `no match`() {
        assertFalse(SongTitleScrubber.matchesSetlistSong("Dark Star", "Scarlet Begonias"))
    }

    @Test
    fun `blank inputs return false`() {
        assertFalse(SongTitleScrubber.matchesSetlistSong("", "Scarlet Begonias"))
        assertFalse(SongTitleScrubber.matchesSetlistSong("Scarlet", ""))
    }
}

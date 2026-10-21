package com.grateful.deadly.feature.settings.screens.bugreport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportPreviewTest {

    private fun lines(count: Int, width: Int = 10) =
        (1..count).joinToString("\n") { "L$it".padEnd(width, 'x') }

    @Test
    fun `short log is shown in full`() {
        val text = lines(5)
        val preview = BugReportExporter.previewTail(text, maxLines = 500, maxChars = 100_000)

        assertEquals(text, preview.text)
        assertEquals(5, preview.shownLines)
        assertEquals(5, preview.totalLines)
        assertFalse(preview.isTruncated)
    }

    @Test
    fun `line limit keeps the last lines`() {
        val preview = BugReportExporter.previewTail(lines(1_000), maxLines = 500, maxChars = 100_000)

        assertEquals(500, preview.shownLines)
        assertEquals(1_000, preview.totalLines)
        assertTrue(preview.isTruncated)
        assertTrue(preview.text.startsWith("L501"))
        assertTrue(preview.text.endsWith(lines(1_000).takeLast(10)))
    }

    @Test
    fun `char limit wins when smaller than line limit`() {
        // 1000 lines of 100 chars (+1 newline) = ~101KB; cap at 10_000 chars.
        val preview = BugReportExporter.previewTail(lines(1_000, width = 100), maxLines = 500, maxChars = 10_000)

        assertTrue(preview.text.length <= 10_000)
        assertEquals(99, preview.shownLines)
        assertTrue(preview.isTruncated)
        assertTrue(preview.text.startsWith("L902"))
    }

    @Test
    fun `single huge line keeps only its tail`() {
        val text = "a".repeat(50_000) + "END"
        val preview = BugReportExporter.previewTail(text, maxLines = 500, maxChars = 1_000)

        assertEquals(1_000, preview.text.length)
        assertTrue(preview.text.endsWith("END"))
        assertTrue(preview.isTruncated)
    }

    @Test
    fun `multi-megabyte log is bounded`() {
        val text = lines(40_000, width = 100) // ~4MB, like the field report
        val preview = BugReportExporter.previewTail(text)

        assertTrue(preview.text.length <= BugReportExporter.PREVIEW_MAX_CHARS)
        assertTrue(preview.shownLines <= BugReportExporter.PREVIEW_MAX_LINES)
        assertTrue(preview.isTruncated)
    }

    @Test
    fun `empty log`() {
        val preview = BugReportExporter.previewTail("")
        assertEquals("", preview.text)
        assertFalse(preview.isTruncated)
    }
}

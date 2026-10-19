package com.aiplacementos.app.ui.history

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timestamp parsing, and the crash it exists to prevent.
 *
 * The history endpoints serialise from SQLAlchemy `DateTime` columns declared **without** a
 * timezone, so a real response carries `2026-10-17T21:30:00` -- no offset and no `Z`. Every other
 * screen in this app parses timestamps with `OffsetDateTime.parse`, which throws on exactly that
 * shape. A history screen built the usual way would therefore crash on a perfectly valid
 * response, which is why [parseConversationTimestamp] exists and why these tests do.
 */
class ConversationFormattingTest {

    @Test
    fun `a naive timestamp is read as UTC rather than throwing`() {
        // The shape the server actually sends today.
        val parsed = parseConversationTimestamp("2026-10-17T21:30:00")

        assertNotNull("a naive timestamp must parse, not crash the screen", parsed)
        assertEquals(ZoneOffset.UTC, parsed!!.offset)
        assertEquals(2026, parsed.year)
        assertEquals(21, parsed.hour)
    }

    @Test
    fun `an offset timestamp still parses`() {
        // The shape the server would send if those columns ever become timezone-aware.
        val parsed = parseConversationTimestamp("2026-10-17T21:30:00Z")

        assertNotNull(parsed)
        assertEquals(ZoneOffset.UTC, parsed!!.offset)
    }

    @Test
    fun `a non-UTC offset is preserved`() {
        val parsed = parseConversationTimestamp("2026-10-17T21:30:00+05:30")

        assertNotNull(parsed)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), parsed!!.offset)
    }

    @Test
    fun `an unusable value gives null rather than a guessed date`() {
        // A wrong date on a student's own record is worse than no date.
        for (bad in listOf("", "   ", "not a date", "17/10/2026", "2026-13-45T99:99:99")) {
            assertNull("should not parse: " + bad, parseConversationTimestamp(bad))
            assertNull(formatConversationTimestamp(bad))
        }
        assertNull(parseConversationTimestamp(null))
        assertNull(formatConversationTimestamp(null))
    }

    @Test
    fun `a formatted timestamp is human readable`() {
        val formatted = formatConversationTimestamp("2026-10-17T21:30:00")

        assertNotNull(formatted)
        // Rendered in the reader's own zone, so the clock time is not asserted -- only that the
        // date survived and no ISO artefacts leaked into the UI string.
        assertTrue("expected a readable date, was " + formatted, formatted!!.contains("2026"))
        assertTrue(!formatted.contains("T"))
    }

    // ------------------------------------------------------------------ vocabularies

    @Test
    fun `the kinds have product names`() {
        assertEquals("Voice Career Coach", conversationKindLabel("career_coach"))
        assertEquals("Mock Interview", conversationKindLabel("mock_interview"))
    }

    @Test
    fun `an unknown kind is passed through rather than guessed at`() {
        // A new server kind must not be silently relabelled as one of the two we know.
        assertEquals("something_new", conversationKindLabel("something_new"))
    }

    @Test
    fun `an interrupted session is not described as finished`() {
        val label = conversationStatusLabel("interrupted")

        assertEquals("Interrupted", label)
        // The honesty rule: a dropped connection is not a completed conversation.
        assertTrue(!label.contains("Complete"))
    }

    @Test
    fun `the statuses have product names`() {
        assertEquals("Still open", conversationStatusLabel("active"))
        assertEquals("Completed", conversationStatusLabel("completed"))
        assertEquals("whatever", conversationStatusLabel("whatever"))
    }

    @Test
    fun `turn roles are named from the student's point of view`() {
        assertEquals("You", turnRoleLabel("user"))
        assertEquals("Coach", turnRoleLabel("assistant"))
    }

    // ------------------------------------------------------------------ titles

    @Test
    fun `a server title is used when there is one`() {
        assertEquals(
            "System design practice",
            conversationDisplayTitle("career_coach", "System design practice", null),
        )
    }

    @Test
    fun `a blank title falls back to values the server did send`() {
        // Composed from the kind and the date -- never a summary of a transcript the list
        // endpoint never fetched.
        val title = conversationDisplayTitle("mock_interview", "   ", "2026-10-17T21:30:00")

        assertTrue(title.startsWith("Mock Interview"))
        assertTrue(title.contains("2026"))
    }

    @Test
    fun `with neither a title nor a date the kind alone is used`() {
        assertEquals("Voice Career Coach", conversationDisplayTitle("career_coach", null, null))
    }
}

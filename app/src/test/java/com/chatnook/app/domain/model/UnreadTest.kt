package com.chatnook.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadTest {

    @Test
    fun `mention needs a word boundary on both sides`() {
        assertTrue(mentionsUser("hey @alice look", "alice"))
        assertTrue(mentionsUser("@alice", "alice"))
        assertTrue(mentionsUser("ping @alice.", "alice"))
        assertFalse(mentionsUser("hey @alice2 look", "alice"))
        assertFalse(mentionsUser("hey @notalice look", "alice"))
        assertFalse(mentionsUser("mail me at alice@example.com", "example"))
    }

    @Test
    fun `mention is case insensitive and handles dotted names`() {
        assertTrue(mentionsUser("@Alice hi", "alice"))
        assertTrue(mentionsUser("yo @john.doe!", "john.doe"))
        // '.' must not act as a regex wildcard.
        assertFalse(mentionsUser("yo @johnxdoe!", "john.doe"))
        assertTrue(mentionsUser("@a-b_c", "a-b_c"))
    }

    @Test
    fun `no username means no mentions`() {
        assertFalse(mentionsUser("@alice hi", null))
        assertFalse(mentionsUser("@alice hi", " "))
    }

    @Test
    fun `the open channel's badge is hidden`() {
        val counts = mapOf(
            "a" to UnreadState(3, 0),
            "b" to UnreadState(1, 2),
        )
        val shown = unreadBadges(counts, openChannelId = "a")
        assertFalse(shown.getValue("a").visible)
        assertEquals(UnreadState(1, 2), shown["b"])
    }

    @Test
    fun `an already read open channel is left alone`() {
        val counts = mapOf("a" to UnreadState.None, "b" to UnreadState(4, 0))
        assertEquals(counts, unreadBadges(counts, openChannelId = "a"))
        assertEquals(counts, unreadBadges(counts, openChannelId = null))
    }

    @Test
    fun `badge labels cap at 99`() {
        assertEquals("7", UnreadState(7, 0).label)
        assertEquals("99+", UnreadState(140, 0).label)
        assertFalse(UnreadState.None.visible)
        assertTrue(UnreadState(0, 1).visible)
    }
}

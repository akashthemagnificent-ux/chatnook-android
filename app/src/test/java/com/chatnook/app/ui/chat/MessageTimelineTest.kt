package com.chatnook.app.ui.chat

import com.chatnook.app.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun message(
    id: String,
    author: String,
    at: Long,
    content: String = "hi",
): ChatMessage = ChatMessage(
    messageId = id,
    channelId = "c1",
    senderId = author,
    content = content,
    messageType = ChatMessage.MessageType.text,
    createdAt = at,
)

/** Fixed anchor; tests only use offsets from it, so the absolute date is irrelevant. */
private const val DAY_ONE = 1_762_336_800_000L
private const val MINUTE = 60_000L

class MessageTimelineTest {

    @Test
    fun `groups consecutive messages from one author inside the window`() {
        val items = buildTimeline(
            listOf(
                message("a", "u1", DAY_ONE),
                message("b", "u1", DAY_ONE + MINUTE),
                message("c", "u1", DAY_ONE + 2 * MINUTE),
            ),
            UTC,
        )

        val messages = items.filterIsInstance<TimelineItem.Msg>()
        assertEquals(3, messages.size)
        assertFalse(messages[0].grouped)
        assertTrue(messages[1].grouped)
        assertTrue(messages[2].grouped)
    }

    @Test
    fun `breaks the group on author change and on a long gap`() {
        val items = buildTimeline(
            listOf(
                message("a", "u1", DAY_ONE),
                message("b", "u2", DAY_ONE + MINUTE),
                message("c", "u2", DAY_ONE + 30 * MINUTE),
            ),
            UTC,
        ).filterIsInstance<TimelineItem.Msg>()

        assertFalse(items[1].grouped)
        assertFalse(items[2].grouped)
    }

    @Test
    fun `starts a new day separator and resets grouping`() {
        // DAY_ONE is 10:00 UTC: +13h59m is 23:59, +14h1m is 00:01 the next day.
        val items = buildTimeline(
            listOf(
                message("a", "u1", DAY_ONE + 14 * 60 * MINUTE - MINUTE),
                message("b", "u1", DAY_ONE + 14 * 60 * MINUTE + MINUTE),
            ),
            UTC,
        )

        // One separator per distinct day, and grouping does not cross midnight.
        assertEquals(2, items.filterIsInstance<TimelineItem.Day>().size)
        assertEquals(4, items.size)
        val second = items[3] as TimelineItem.Msg
        assertFalse(second.grouped)
    }

    @Test
    fun `a deleted message does not join a group`() {
        val items = buildTimeline(
            listOf(
                message("a", "u1", DAY_ONE),
                message("b", "u1", DAY_ONE + MINUTE, content = ""),
            ),
            UTC,
        ).filterIsInstance<TimelineItem.Msg>()

        assertFalse(items[1].grouped)
    }

    @Test
    fun `day labels distinguish today, yesterday and older`() {
        val now = DAY_ONE + 5 * 60 * MINUTE
        assertEquals(DayLabel.Today, dayLabel(DAY_ONE + MINUTE, now, UTC))
        assertEquals(DayLabel.Yesterday, dayLabel(DAY_ONE - 24 * 60 * MINUTE, now, UTC))
        assertEquals(DayLabel.Date, dayLabel(DAY_ONE - 5 * 24 * 60 * MINUTE, now, UTC))
    }

    @Test
    fun `row keys are unique so the lazy list can key on them`() {
        val items = buildTimeline(
            listOf(
                message("a", "u1", DAY_ONE),
                message("b", "u2", DAY_ONE + MINUTE),
                message("c", "u2", DAY_ONE + 24 * 60 * MINUTE),
            ),
            UTC,
        )

        assertEquals(items.size, items.map { it.key }.distinct().size)
    }
}

package com.chatnook.app.ui.chat

import com.chatnook.app.domain.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Consecutive messages from one author inside this window collapse into one header. */
const val GROUP_WINDOW_MS = 5 * 60_000L

sealed interface TimelineItem {
    val key: String

    data class Day(val dayKey: String) : TimelineItem {
        override val key: String get() = "day-$dayKey"
    }

    data class Msg(val message: ChatMessage, val grouped: Boolean) : TimelineItem {
        override val key: String get() = message.messageId
    }
}

enum class DayLabel { Today, Yesterday, Date }

/**
 * Flattens a sorted message list into rows plus day separators.
 *
 * Pure and timezone-injected so the grouping rules are unit-testable; the screen only
 * turns [DayLabel] into localized text.
 */
fun buildTimeline(messages: List<ChatMessage>, zone: TimeZone): List<TimelineItem> {
    val out = ArrayList<TimelineItem>(messages.size + 4)
    var currentDay: String? = null
    var previous: ChatMessage? = null

    for (message in messages) {
        val day = dayKey(message.createdAt, zone)
        if (day != currentDay) {
            out += TimelineItem.Day(day)
            currentDay = day
            previous = null
        }

        val grouped = previous != null &&
            previous.senderId == message.senderId &&
            previous.messageType == ChatMessage.MessageType.text &&
            message.messageType == ChatMessage.MessageType.text &&
            !previous.isDeleted && !message.isDeleted &&
            message.createdAt - previous.createdAt < GROUP_WINDOW_MS

        out += TimelineItem.Msg(message, grouped)
        previous = message
    }
    return out
}

/** Local calendar day as `yyyy-MM-dd`; keys are compared, never shown. */
fun dayKey(epochMillis: Long, zone: TimeZone): String {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = epochMillis }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

fun dayLabel(epochMillis: Long, nowMillis: Long, zone: TimeZone): DayLabel {
    val day = dayKey(epochMillis, zone)
    if (day == dayKey(nowMillis, zone)) return DayLabel.Today
    val yesterday = Calendar.getInstance(zone).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis
    return if (day == dayKey(yesterday, zone)) DayLabel.Yesterday else DayLabel.Date
}

/** `Nov 5, 2026` in the device locale. */
fun formatDay(epochMillis: Long, zone: TimeZone, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("MMM d, yyyy", locale).apply { timeZone = zone }.format(Date(epochMillis))

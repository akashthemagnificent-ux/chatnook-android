package com.chatnook.app.domain.model

/** Someone whose typing ping arrived within the last five seconds. */
data class TypingUser(val userId: String, val name: String)

/** Badge state for one channel; [unread] never counts the reader's own messages. */
data class UnreadState(val unread: Int, val mentions: Int) {
    val visible: Boolean get() = unread > 0 || mentions > 0

    /** Capped like Discord so a busy channel cannot widen the row. */
    val label: String get() = if (unread > 99) "99+" else unread.toString()

    companion object {
        val None = UnreadState(0, 0)
    }
}

private const val NAME_START = "a-z0-9._-"

/** '.' ends a sentence, so it terminates a mention even though usernames may contain it. */
private const val NAME_END = "a-z0-9_-"

/**
 * True when [content] mentions `@username` as a whole word.
 *
 * Mirrors the regex in `channel_unread_counts` so the badge count and the highlighted
 * row can never disagree about what counts as a mention.
 */
fun mentionsUser(content: String, username: String?): Boolean {
    if (username.isNullOrBlank()) return false
    val pattern = "(^|[^$NAME_START])@${Regex.escape(username)}($|[^$NAME_END])"
    return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(content)
}

/**
 * Hides the badge of the channel that is open right now. Opening a channel marks it
 * read, but a server refresh can land before that write does, and the badge must not
 * flash back at the reader.
 */
fun unreadBadges(
    counts: Map<String, UnreadState>,
    openChannelId: String?,
): Map<String, UnreadState> =
    if (openChannelId == null || counts[openChannelId]?.visible != true) {
        counts
    } else {
        counts + (openChannelId to UnreadState.None)
    }

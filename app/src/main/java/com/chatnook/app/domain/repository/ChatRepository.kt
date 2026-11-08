package com.chatnook.app.domain.repository

import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.OutgoingImage
import com.chatnook.app.domain.model.Profile
import com.chatnook.app.domain.model.TypingUser
import com.chatnook.app.domain.model.UnreadState
import kotlinx.coroutines.flow.Flow

/**
 * All chat data access. Every [Flow] emits a snapshot immediately and then every
 * later change pushed by Supabase Realtime, so the UI never polls.
 */
interface ChatRepository {

    /** Servers the user is a member of. */
    fun getGuilds(): Flow<List<Guild>>

    /** Text channels inside a guild, ordered by position. */
    fun getChannels(guildId: String): Flow<List<ChatChannel>>

    /** Newest page of a channel, oldest-first, kept live via Realtime. */
    fun getChannelMessages(channelId: String): Flow<List<ChatMessage>>

    /**
     * One page of messages older than [before], oldest-first. Empty means the start of
     * the channel has been reached.
     */
    suspend fun loadOlderMessages(
        channelId: String,
        before: ChatMessage,
        limit: Int = 50,
    ): List<ChatMessage>

    /**
     * Inserts a message under a caller-chosen [messageId] so an optimistic bubble can be
     * reconciled with the realtime echo by id instead of by content.
     */
    suspend fun sendMessage(
        channelId: String,
        content: String,
        replyToId: String? = null,
        messageId: String = java.util.UUID.randomUUID().toString(),
        image: OutgoingImage? = null,
    ): ChatMessage

    suspend fun editMessage(messageId: String, content: String)

    suspend fun deleteMessage(messageId: String)

    /** Who is typing in [channelId] right now; entries expire five seconds after a ping. */
    fun typingUsers(channelId: String): Flow<List<TypingUser>>

    /** Announces the signed-in user as typing; the caller throttles the pings. */
    suspend fun sendTyping(channelId: String, name: String)

    /** Records that the user has read the channel up to [messageId] at [atIso]. */
    suspend fun markRead(channelId: String, messageId: String, atIso: String)

    /** Unread and mention counts per channel of a guild, kept live. */
    fun getUnreadCounts(guildId: String, mention: String?): Flow<Map<String, UnreadState>>

    /** Ids of the members currently connected to a guild, the caller included. */
    fun getOnlineUsers(guildId: String): Flow<Set<String>>

    /** Adds the caller's reaction, or removes it when it already exists. */
    suspend fun toggleReaction(messageId: String, emoji: String)

    suspend fun createGuild(name: String): Guild

    suspend fun createChannel(guildId: String, name: String, topic: String?): ChatChannel

    suspend fun createInvite(guildId: String): String

    /** Redeems an invite code; returns the guild id the user joined. */
    suspend fun joinGuild(code: String): String

    suspend fun getProfile(userId: String): Profile?

    /** Guild-uploaded emoji, readable by members. */
    fun getCustomEmojis(guildId: String): Flow<List<CustomEmoji>>

    /** Registers an already-uploaded emoji file; owner-only, enforced by RLS. */
    suspend fun createCustomEmoji(guildId: String, name: String, url: String): CustomEmoji

    fun getMyProfile(): Flow<Profile?>

    suspend fun updateProfile(profile: Profile)

    /** Uploads bytes to a public bucket and returns the public URL. */
    suspend fun uploadImage(bucket: String, bytes: ByteArray, extension: String): String
}

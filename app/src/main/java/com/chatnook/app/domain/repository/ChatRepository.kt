package com.chatnook.app.domain.repository

import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.DmSummary
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.GuildRole
import com.chatnook.app.domain.model.Invite
import com.chatnook.app.domain.model.Member
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

    /** Unread and mention counts for every channel the user can reach, kept live. */
    fun getUnreadCounts(mention: String?): Flow<Map<String, UnreadState>>

    /** Members of a guild with their top role, for the member list. */
    fun getMembers(guildId: String): Flow<List<Member>>

    /** Direct message conversations with the other person and the newest message. */
    fun getDms(): Flow<List<DmSummary>>

    /** Finds the DM with [userId] or creates it; returns the channel id. */
    suspend fun openDm(userId: String): String

    /** Ids of the members currently connected to a guild, the caller included. */
    fun getOnlineUsers(guildId: String): Flow<Set<String>>

    /** Adds the caller's reaction, or removes it when it already exists. */
    suspend fun toggleReaction(messageId: String, emoji: String)

    suspend fun createGuild(name: String): Guild

    suspend fun createChannel(guildId: String, name: String, topic: String?): ChatChannel

    /** Renames a channel and/or changes its topic; null leaves the field alone. */
    suspend fun updateChannel(channelId: String, name: String? = null, topic: String? = null)

    suspend fun deleteChannel(channelId: String)

    /** Rewrites channel positions as 0..n-1 in the given order; owner only. */
    suspend fun reorderChannels(guildId: String, orderedIds: List<String>)

    /** Renames a guild and/or replaces its icon; owner only. */
    suspend fun updateGuild(guildId: String, name: String? = null, iconUrl: String? = null)

    /** Deletes a guild and everything in it; owner only. */
    suspend fun deleteGuild(guildId: String)

    suspend fun createInvite(
        guildId: String,
        expiresAtIso: String? = null,
        maxUses: Int? = null,
    ): String

    /** Invite codes of a guild, newest first, kept live. */
    fun getInvites(guildId: String): Flow<List<Invite>>

    suspend fun revokeInvite(code: String)

    /** Redeems an invite code; returns the guild id the user joined. */
    suspend fun joinGuild(code: String): String

    /** Roles of a guild, highest position first, kept live. */
    fun getRoles(guildId: String): Flow<List<GuildRole>>

    /** The caller's resolved permission bits in a guild; 0 for outsiders. */
    fun getMyPermissions(guildId: String): Flow<Long>

    suspend fun createRole(guildId: String, name: String, color: Int?, permissions: Long): String

    suspend fun updateRole(roleId: String, name: String, color: Int?, permissions: Long)

    suspend fun deleteRole(roleId: String)

    /** Replaces every role of one member in one call; owner only. */
    suspend fun setMemberRoles(guildId: String, userId: String, roleIds: List<String>)

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

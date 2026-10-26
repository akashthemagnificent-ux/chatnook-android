package com.chatnook.app.domain.repository

import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.Profile
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

    suspend fun sendMessage(channelId: String, content: String, replyToId: String? = null): ChatMessage

    suspend fun editMessage(messageId: String, content: String)

    suspend fun deleteMessage(messageId: String)

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

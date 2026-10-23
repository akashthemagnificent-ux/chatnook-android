package com.chatnook.app.data.repository

import com.chatnook.app.data.Supabase
import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.ChatUser
import com.chatnook.app.domain.repository.ChatRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Supabase-backed implementation of [ChatRepository].
 *
 * Reads/writes through Postgrest. When the build has no Supabase credentials
 * ([Supabase.isConfigured] is false) every call degrades to an in-memory cache
 * so the UI stays usable during development.
 */
class SupabaseChatRepository : ChatRepository {

    private val messageCache = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val channelCache = MutableStateFlow<List<ChatChannel>>(emptyList())

    override fun getChannelMessages(channelId: String): Flow<List<ChatMessage>> =
        messageCache.map { messages ->
            messages.filter { it.channelId == channelId }
        }

    override suspend fun sendMessage(
        channelId: String,
        senderId: String,
        content: String,
        messageType: ChatMessage.MessageType,
    ): ChatMessage {
        val local = ChatMessage(
            messageId = "$channelId-${System.currentTimeMillis()}",
            channelId = channelId,
            senderId = senderId,
            content = content,
            messageType = messageType,
            createdAt = System.currentTimeMillis(),
        )

        val client = Supabase.clientOrNull()
        if (client != null) {
            client.from("messages").insert(
                mapOf(
                    "channel_id" to channelId,
                    "sender_id" to senderId,
                    "content" to content,
                    "message_type" to messageType.name,
                ),
            )
        }

        messageCache.value = messageCache.value + local
        return local
    }

    override fun getUserChannels(userId: String): Flow<List<ChatChannel>> =
        channelCache.asStateFlow()

    override suspend fun createChannel(
        channel: ChatChannel,
        creatorId: String,
    ): ChatChannel {
        channelCache.value = channelCache.value + channel
        return channel
    }

    override suspend fun getUserProfile(userId: String): ChatUser =
        ChatUser(
            userId = userId,
            displayName = "",
            avatarUrl = null,
            isOnline = false,
            lastSeen = 0,
        )

    override suspend fun updateUserProfile(userId: String, user: ChatUser) {
        // TODO: persist via Postgrest once the profiles table exists.
    }

    override fun subscribeToChannel(channelId: String, onNewMessage: (ChatMessage) -> Unit) {
        // TODO: wire up Realtime channel subscription once credentials are configured.
    }

    override suspend fun uploadImage(fileBytes: ByteArray): String {
        // TODO: upload via Storage once a bucket is provisioned.
        return ""
    }
}

package com.chatnook.app.domain.repository

import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.ChatUser
import com.chatnook.app.domain.model.ChatChannel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat operations.
 * Defines the contract for all chat data access.
 */
interface ChatRepository {
    /**
     * Gets all messages for a specific channel.
     * 
     * @param channelId The channel ID
     * @return Flow of messages for the channel
     */
    fun getChannelMessages(channelId: String): Flow<List<ChatMessage>>

    /**
     * Sends a new message to a channel.
     * 
     * @param channelId The target channel ID
     * @param senderId The sender user ID
     * @param content The message content
     * @param messageType The message type
     * @return The sent message
     */
    suspend fun sendMessage(
        channelId: String,
        senderId: String,
        content: String,
        messageType: ChatMessage.MessageType = ChatMessage.MessageType.text
    ): ChatMessage

    /**
     * Gets all channels for the current user.
     * 
     * @param userId The user ID
     * @return Flow of channels for the user
     */
    fun getUserChannels(userId: String): Flow<List<ChatChannel>>

    /**
     * Creates a new channel.
     * 
     * @param channel The channel to create
     * @param creatorId The creator user ID
     * @return The created channel
     */
    suspend fun createChannel(
        channel: ChatChannel,
        creatorId: String
    ): ChatChannel

    /**
     * Gets user profile information.
     * 
     * @param userId The user ID
     * @return The user profile
     */
    suspend fun getUserProfile(userId: String): ChatUser

    /**
     * Updates user profile.
     * 
     * @param userId The user ID
     * @param user The updated user data
     */
    suspend fun updateUserProfile(userId: String, user: ChatUser)

    /**
     * Subscribe to real-time updates for a channel.
     * 
     * @param channelId The channel ID
     * @param onNewMessage Callback for new messages
     */
    fun subscribeToChannel(channelId: String, onNewMessage: (ChatMessage) -> Unit)

    /**
     * Uploads an image to storage.
     * 
     * @param fileBytes The image file bytes
     * @return The storage URL
     */
    suspend fun uploadImage(fileBytes: ByteArray): String
}
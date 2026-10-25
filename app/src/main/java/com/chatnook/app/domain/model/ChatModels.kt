package com.chatnook.app.domain.model

/**
 * Represents a chat message in the ChatNook application.
 */
data class ChatMessage(
    val messageId: String,
    val channelId: String,
    val senderId: String,
    val content: String,
    val messageType: MessageType,
    val createdAt: Long,
    val metadata: Map<String, Any?> = mutableMapOf()
) {

    enum class MessageType {
        text,
        image,
        system,
        notification
    }

    /** Converts a Supabase map to a ChatMessage */
    companion object {
        fun fromMap(map: Map<String, Any?>): ChatMessage {
            return ChatMessage(
                messageId = map["message_id"] as? String ?: "",
                channelId = map["channel_id"] as? String ?: "",
                senderId = map["sender_id"] as? String ?: "",
                content = map["content"] as? String ?: "",
                messageType = when {
                    map["message_type"] == "image" -> MessageType.image
                    map["message_type"] == "system" -> MessageType.system
                    map["message_type"] == "notification" -> MessageType.notification
                    else -> MessageType.text
                },
                createdAt = map["created_at"] as? Long ?: System.currentTimeMillis(),
                metadata = map["metadata"] as? Map<String, Any?> ?: mutableMapOf()
            )
        }
    }
}

/**
 * Represents a chat user in the ChatNook application.
 */
data class ChatUser(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val lastSeen: Long,
    val metadata: Map<String, Any?> = mutableMapOf()
)

/**
 * Represents a chat channel in the ChatNook application.
 */
data class ChatChannel(
    val channelId: String,
    val name: String,
    val description: String?,
    val isGroup: Boolean,
    val lastMessage: ChatMessage?,
    val unreadCount: Int,
    val members: List<ChatUser>,
    val metadata: Map<String, Any?> = mutableMapOf()
)
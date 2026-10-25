package com.chatnook.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTest {

    @Test
    fun `fromMap maps all known fields`() {
        val message = ChatMessage.fromMap(
            mapOf(
                "message_id" to "m1",
                "channel_id" to "general",
                "sender_id" to "u1",
                "content" to "hello",
                "message_type" to "image",
                "created_at" to 1234L,
            ),
        )

        assertEquals("m1", message.messageId)
        assertEquals("general", message.channelId)
        assertEquals("u1", message.senderId)
        assertEquals("hello", message.content)
        assertEquals(ChatMessage.MessageType.image, message.messageType)
        assertEquals(1234L, message.createdAt)
    }

    @Test
    fun `fromMap falls back to text for unknown type`() {
        val message = ChatMessage.fromMap(mapOf("message_type" to "something-new"))

        assertEquals(ChatMessage.MessageType.text, message.messageType)
    }

    @Test
    fun `fromMap tolerates missing keys`() {
        val message = ChatMessage.fromMap(emptyMap())

        assertEquals("", message.messageId)
        assertEquals("", message.content)
        assertTrue(message.createdAt > 0)
        assertTrue(message.metadata.isEmpty())
    }
}

package com.chatnook.app.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

class ChatMessageTest {

    @Test
    fun `fromJson maps columns and author embed`() {
        val message = ChatMessage.fromJson(
            obj(
                """
                {"id":"m1","channel_id":"c1","author_id":"u1","content":"hello",
                 "type":"default","created_at":"2026-10-26T10:00:00.123456+00:00",
                 "author":{"display_name":"Ada","avatar_url":"http://a/x.png"}}
                """,
            ),
        )

        assertEquals("m1", message.messageId)
        assertEquals("c1", message.channelId)
        assertEquals("u1", message.senderId)
        assertEquals("hello", message.content)
        assertEquals(ChatMessage.MessageType.text, message.messageType)
        assertEquals("Ada", message.senderName)
        assertEquals("http://a/x.png", message.senderAvatarUrl)
        assertTrue(message.createdAt > 0)
        assertNull(message.replyToId)
    }

    @Test
    fun `fromJson groups reactions by emoji`() {
        val message = ChatMessage.fromJson(
            obj(
                """
                {"id":"m1","content":"hi","reactions":[
                  {"emoji":"👍","user_id":"u1"},
                  {"emoji":"👍","user_id":"u2"},
                  {"emoji":"🔥","user_id":"u1"}]}
                """,
            ),
        )

        assertEquals(2, message.reactions.size)
        val thumbs = message.reactions.first { it.emoji == "👍" }
        assertEquals(listOf("u1", "u2"), thumbs.userIds)
    }

    @Test
    fun `fromJson tolerates missing keys and unknown type`() {
        val message = ChatMessage.fromJson(obj("""{"type":"something-new"}"""))

        assertEquals("", message.messageId)
        assertEquals("", message.content)
        assertEquals(ChatMessage.MessageType.text, message.messageType)
        assertEquals(0L, message.createdAt)
        assertTrue(message.reactions.isEmpty())
    }

    @Test
    fun `empty content marks the message deleted unless it is a system message`() {
        assertTrue(ChatMessage.fromJson(obj("""{"content":null}""")).isDeleted)
        assertFalse(ChatMessage.fromJson(obj("""{"type":"system"}""")).isDeleted)
    }
}

class ProfileThemeTest {

    @Test
    fun `parses stops when the theme is a gradient`() {
        val theme = ProfileTheme.fromJson(
            obj("""{"preset":"sunset","stops":["#FF7A18","#AF002D"]}"""),
        )

        assertEquals("sunset", theme.preset)
        assertTrue(theme.isGradient)
        assertEquals(listOf(0xFFFF7A18L, 0xFFAF002DL), theme.stops.mapNotNull(HexColor::parse))
    }

    @Test
    fun `drops malformed stops and caps at five`() {
        val theme = ProfileTheme.fromJson(
            obj("""{"stops":["#FFF","nope","#112233","#445566","#778899","#AABBCC","#DDEEFF"]}"""),
        )

        assertEquals(5, theme.stops.size)
        assertFalse(theme.stops.contains("nope"))
    }

    @Test
    fun `missing theme is None`() {
        assertEquals(ProfileTheme.None, ProfileTheme.fromJson(null))
        assertFalse(ProfileTheme.None.isGradient)
    }
}

class CustomEmojiTest {

    @Test
    fun `reaction value identifies uploaded emoji by url`() {
        val emoji = CustomEmoji.fromJson(obj("""{"id":"e1","name":"blob","url":"https://x/y.gif"}"""))

        assertEquals("blob", emoji.name)
        assertEquals("https://x/y.gif", emoji.code)
        assertTrue(emoji.code.isCustomEmoji)
        assertFalse("\uD83D\uDC4D".isCustomEmoji)
    }

    @Test
    fun `fromJson tolerates missing columns`() {
        val emoji = CustomEmoji.fromJson(obj("{}"))

        assertEquals("", emoji.name)
        assertFalse(emoji.code.isCustomEmoji)
    }
}

class HexColorTest {
    @Test
    fun `parses rgb with opaque alpha`() {
        assertEquals(0xFF5865F2L, HexColor.parse("#5865F2"))
        assertEquals(0xFF5865F2L, HexColor.parse("5865f2"))
    }

    @Test
    fun `parses argb`() {
        assertEquals(0x805865F2L, HexColor.parse("#805865F2"))
    }

    @Test
    fun `rejects bad shapes`() {
        assertNull(HexColor.parse("#FFF"))
        assertNull(HexColor.parse("#GGGGGG"))
        assertNull(HexColor.parse(""))
        assertFalse(HexColor.isValid("#12345"))
    }
}

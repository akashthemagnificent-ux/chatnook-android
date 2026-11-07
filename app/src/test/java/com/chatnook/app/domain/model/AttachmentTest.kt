package com.chatnook.app.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `parses the columns the app selects`() {
        val attachment = Attachment.fromJson(
            json("""{"id":"a1","url":"https://cdn/x.jpg","filename":"x.jpg",
                     "content_type":"image/jpeg","width":800,"height":600}"""),
        )
        assertEquals("a1", attachment.id)
        assertEquals("https://cdn/x.jpg", attachment.url)
        assertEquals(800, attachment.width)
        assertEquals(0.75f, attachment.aspectRatio)
    }

    @Test
    fun `missing dimensions fall back to a portrait box`() {
        val attachment = Attachment.fromJson(json("""{"id":"a1","url":"u"}"""))
        assertEquals(0, attachment.width)
        assertEquals(0.75f, attachment.aspectRatio)
    }

    @Test
    fun `an absurd aspect ratio is clamped so the row cannot explode`() {
        val panorama = Attachment.fromJson(json("""{"id":"a","url":"u","width":4000,"height":10}"""))
        val tower = Attachment.fromJson(json("""{"id":"b","url":"u","width":10,"height":4000}"""))
        assertEquals(0.25f, panorama.aspectRatio)
        assertEquals(3f, tower.aspectRatio)
    }
}

package com.chatnook.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun spans(text: String) = parseMarkdown(text)

private fun plainText(text: String): String = spans(text).joinToString("") { it.text }

class MessageMarkdownTest {

    @Test
    fun `bold italic and strike produce their own spans`() {
        val result = spans("**bold** *italic* ~~gone~~")
        assertTrue(result.any { it is MarkdownSpan.Bold && it.text == "bold" })
        assertTrue(result.any { it is MarkdownSpan.Italic && it.text == "italic" })
        assertTrue(result.any { it is MarkdownSpan.Strike && it.text == "gone" })
    }

    @Test
    fun `inline code keeps its content untouched`() {
        val result = spans("run `./gradlew **test**` now")
        val code = result.filterIsInstance<MarkdownSpan.Code>().single()
        assertEquals("./gradlew **test**", code.text)
        assertTrue(result.none { it is MarkdownSpan.Bold })
    }

    @Test
    fun `fenced block becomes one code block and keeps newlines`() {
        val input = "look:\n```\nval x = 1\nval y = 2\n```\ndone"
        val result = spans(input)
        val block = result.filterIsInstance<MarkdownSpan.CodeBlock>().single()
        assertEquals("val x = 1\nval y = 2", block.text)
        assertTrue(plainText(input).startsWith("look:"))
        assertTrue(plainText(input).endsWith("done"))
    }

    @Test
    fun `urls become links and trailing punctuation stays in the text`() {
        val result = spans("see https://example.com/a?b=1.")
        val link = result.filterIsInstance<MarkdownSpan.Link>().single()
        assertEquals("https://example.com/a?b=1", link.url)
        assertTrue(result.any { it is MarkdownSpan.Plain && it.text.endsWith(".") })
    }

    @Test
    fun `mentions only match at a word boundary`() {
        val result = spans("hi @ada and mail@example.com")
        val mention = result.filterIsInstance<MarkdownSpan.Mention>().single()
        assertEquals("@ada", mention.text)
        assertTrue(result.none { it is MarkdownSpan.Mention && it.text.startsWith("@example") })
    }

    @Test
    fun `unbalanced markers stay literal`() {
        assertEquals("2 * 3 = 6", plainText("2 * 3 = 6"))
        assertEquals("**open", plainText("**open"))
    }

    @Test
    fun `an unterminated fence does not swallow the message`() {
        val result = spans("```\nval x = 1")
        assertTrue(result.none { it is MarkdownSpan.CodeBlock })
        assertTrue(plainText("```\nval x = 1").contains("val x = 1"))
    }
}

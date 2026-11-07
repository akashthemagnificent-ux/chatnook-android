package com.chatnook.app.ui.chat

/**
 * Discord-flavoured inline markup, parsed into plain data so the rules can be unit-tested
 * without a Compose runtime. Nesting (`**bold *italic***`) is deliberately unsupported.
 */
sealed interface MarkdownSpan {
    val text: String

    data class Plain(override val text: String) : MarkdownSpan
    data class Bold(override val text: String) : MarkdownSpan
    data class Italic(override val text: String) : MarkdownSpan
    data class Strike(override val text: String) : MarkdownSpan
    data class Code(override val text: String) : MarkdownSpan
    data class CodeBlock(override val text: String) : MarkdownSpan
    data class Link(override val text: String, val url: String) : MarkdownSpan
    data class Mention(override val text: String) : MarkdownSpan

    /** `:name:` of a guild emoji; [url] is resolved by the caller so parsing stays pure. */
    data class Emoji(val name: String, val url: String) : MarkdownSpan {
        override val text: String get() = ":$name:"
    }
}

private val MENTION = Regex("@[a-z0-9._-]{2,32}", RegexOption.IGNORE_CASE)
private val EMOJI_NAME = Regex("[a-z0-9_]{2,32}")

fun parseMarkdown(input: String, emojis: Map<String, String> = emptyMap()): List<MarkdownSpan> {
    if (input.isEmpty()) return emptyList()
    val out = ArrayList<MarkdownSpan>()
    val lines = input.split("\n")
    val prose = StringBuilder()
    var inCode = false
    val code = StringBuilder()

    fun flushProse() {
        if (prose.isNotEmpty()) {
            out += parseInline(prose.toString(), emojis)
            prose.clear()
        }
    }

    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out += MarkdownSpan.CodeBlock(code.toString().trimEnd('\n'))
                code.clear()
                inCode = false
            } else {
                flushProse()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(line).append('\n')
        } else {
            prose.append(line).append('\n')
        }
    }
    // An unterminated fence renders as ordinary text rather than swallowing the message.
    if (inCode) prose.append("```\n").append(code)
    if (prose.endsWith("\n")) prose.deleteCharAt(prose.lastIndex)
    flushProse()
    return out
}

private fun parseInline(source: String, emojis: Map<String, String>): List<MarkdownSpan> {
    val out = ArrayList<MarkdownSpan>()
    val buffer = StringBuilder()
    var i = 0

    fun flush() {
        if (buffer.isNotEmpty()) {
            out += MarkdownSpan.Plain(buffer.toString())
            buffer.clear()
        }
    }

    while (i < source.length) {
        val rest = source.length - i
        when {
            source.startsWith("**", i) && rest > 4 -> {
                val end = source.indexOf("**", i + 2)
                if (end > i + 2) {
                    flush()
                    out += MarkdownSpan.Bold(source.substring(i + 2, end))
                    i = end + 2
                } else {
                    buffer.append(source[i]); i++
                }
            }

            source.startsWith("~~", i) && rest > 4 -> {
                val end = source.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flush()
                    out += MarkdownSpan.Strike(source.substring(i + 2, end))
                    i = end + 2
                } else {
                    buffer.append(source[i]); i++
                }
            }

            source[i] == '`' -> {
                val end = source.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    out += MarkdownSpan.Code(source.substring(i + 1, end))
                    i = end + 1
                } else {
                    buffer.append(source[i]); i++
                }
            }

            source[i] == '*' && rest > 2 -> {
                val end = source.indexOf('*', i + 1)
                if (end > i + 1) {
                    flush()
                    out += MarkdownSpan.Italic(source.substring(i + 1, end))
                    i = end + 1
                } else {
                    buffer.append(source[i]); i++
                }
            }

            source.startsWith("http://", i) || source.startsWith("https://", i) -> {
                var end = i
                while (end < source.length && !source[end].isWhitespace()) end++
                var url = source.substring(i, end)
                // Trailing punctuation belongs to the sentence, not the URL.
                while (url.isNotEmpty() && url.last() in ".,;:!?)]}\"'") url = url.dropLast(1)
                flush()
                out += MarkdownSpan.Link(url, url)
                i += url.length
            }

            source[i] == '@' && (i == 0 || source[i - 1].isWhitespace()) -> {
                val match = MENTION.matchAt(source, i)
                if (match != null) {
                    flush()
                    out += MarkdownSpan.Mention(match.value)
                    i += match.value.length
                } else {
                    buffer.append(source[i]); i++
                }
            }

            source[i] == ':' && emojis.isNotEmpty() -> {
                val end = source.indexOf(':', i + 1)
                val name = if (end > i + 1) source.substring(i + 1, end) else ""
                val url = emojis[name]
                if (url != null && EMOJI_NAME.matches(name)) {
                    flush()
                    out += MarkdownSpan.Emoji(name, url)
                    i = end + 1
                } else {
                    buffer.append(source[i]); i++
                }
            }

            else -> {
                buffer.append(source[i]); i++
            }
        }
    }
    flush()
    return out
}

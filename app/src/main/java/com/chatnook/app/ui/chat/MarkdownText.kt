package com.chatnook.app.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Renders [parseMarkdown] output; links are tappable through [LinkAnnotation.Url]. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emojis: Map<String, String> = emptyMap(),
) {
    val spans = remember(text, emojis) { parseMarkdown(text, emojis) }
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    val inline = remember(emojis) {
        emojis.mapValues { (name, url) ->
            InlineTextContent(Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(
                    model = url,
                    contentDescription = ":$name:",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    val annotated = remember(spans, primary, codeBackground) {
        buildAnnotatedString {
            spans.forEach { span ->
                when (span) {
                    is MarkdownSpan.Plain -> append(span.text)
                    is MarkdownSpan.Bold ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                    is MarkdownSpan.Italic ->
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                    is MarkdownSpan.Strike ->
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(span.text) }
                    is MarkdownSpan.Code ->
                        withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                        ) { append(span.text) }
                    is MarkdownSpan.CodeBlock ->
                        withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                        ) {
                            append("\n")
                            append(span.text)
                            append("\n")
                        }
                    is MarkdownSpan.Link ->
                        withLink(LinkAnnotation.Url(span.url)) {
                            withStyle(
                                SpanStyle(
                                    color = primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) { append(span.text) }
                        }
                    is MarkdownSpan.Mention ->
                        withStyle(
                            SpanStyle(
                                color = primary,
                                background = primary.copy(alpha = 0.15f),
                                fontWeight = FontWeight.Medium,
                            ),
                        ) { append(span.text) }
                    is MarkdownSpan.Emoji ->
                        appendInlineContent(id = span.name, alternateText = span.text)
                }
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        inlineContent = inline,
    )
}

package com.kairos.app.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import java.net.URI

/** Render model text as text; only explicit safe links become actions. */
object MarkdownUtils {
    fun parseMarkdown(text: String): AnnotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.getOrNull(level) == ' ') {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (24 - level * 2).coerceAtLeast(16).sp))
                appendInline(trimmed.drop(level + 1)); pop()
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                append("• "); appendInline(trimmed.drop(2))
            } else appendInline(line)
            if (index < lines.lastIndex) append("\n")
        }
    }

    private fun AnnotatedString.Builder.appendInline(text: String) {
        var index = 0
        while (index < text.length) {
            val marker = when {
                text.startsWith("**", index) -> "**"
                text[index] == '*' -> "*"
                text[index] == '`' -> "`"
                else -> null
            }
            if (marker != null) {
                val end = text.indexOf(marker, index + marker.length)
                if (end > index + marker.length) {
                    pushStyle(when (marker) {
                        "**" -> SpanStyle(fontWeight = FontWeight.Bold)
                        "*" -> SpanStyle(fontStyle = FontStyle.Italic)
                        else -> SpanStyle(fontFamily = FontFamily.Monospace)
                    })
                    append(text.substring(index + marker.length, end)); pop()
                    index = end + marker.length; continue
                }
            }
            if (text[index] == '[') {
                val labelEnd = text.indexOf("](", index + 1)
                val urlEnd = if (labelEnd >= 0) text.indexOf(')', labelEnd + 2) else -1
                if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                    val url = text.substring(labelEnd + 2, urlEnd)
                    if (safeLink(url)) {
                        pushStringAnnotation("URL", url)
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        append(text.substring(index + 1, labelEnd)); pop(); pop()
                        index = urlEnd + 1; continue
                    }
                }
            }
            append(text[index]); index++
        }
    }
    private fun safeLink(value: String): Boolean = runCatching {
        val uri = URI(value)
        when (uri.scheme?.lowercase()) {
            "http", "https" -> !uri.host.isNullOrBlank()
            "mailto" -> uri.schemeSpecificPart.isNotBlank()
            else -> false
        }
    }.getOrDefault(false)
}

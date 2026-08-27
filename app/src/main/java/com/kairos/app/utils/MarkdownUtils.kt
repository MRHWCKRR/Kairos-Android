package com.kairos.app.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A simple utility to parse basic Markdown (headers, bold, and italic) into Compose AnnotatedString.
 */
object MarkdownUtils {
    fun parseMarkdown(text: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("### ") -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                        appendInline(trimmed.removePrefix("### "))
                        pop()
                    }
                    trimmed.startsWith("## ") -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp))
                        appendInline(trimmed.removePrefix("## "))
                        pop()
                    }
                    trimmed.startsWith("# ") -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp))
                        appendInline(trimmed.removePrefix("# "))
                        pop()
                    }
                    else -> {
                        appendInline(line)
                    }
                }
                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(text: String) {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text.startsWith("*", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append("*")
                        i += 1
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

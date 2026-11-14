package com.kairos.app.utils

import org.junit.Assert.*
import org.junit.Test

class MarkdownUtilsTest {
    @Test fun keepsReadableListsCodeAndSafeLinksWithoutExecutingHtml() {
        val value = MarkdownUtils.parseMarkdown("## Plan\n- **Study** and `code`\n[Docs](https://example.com/help) <script>bad()</script>")
        assertEquals("Plan\n• Study and code\nDocs <script>bad()</script>", value.text)
        assertEquals("https://example.com/help", value.getStringAnnotations("URL", 0, value.length).single().item)
        assertTrue(value.spanStyles.size >= 4)
    }
    @Test fun unsafeAndMalformedLinksRemainPlainText() {
        for (url in listOf("javascript:alert", "file:///private/key", "intent:launch", "https://", "data:text/html,bad")) {
            val value = MarkdownUtils.parseMarkdown("[Link]($url)")
            assertEquals("[Link]($url)", value.text)
            assertTrue(value.getStringAnnotations("URL", 0, value.length).isEmpty())
        }
    }
}

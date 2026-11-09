package com.kairos.app.data.repository

import com.kairos.app.data.models.ChatMessage
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiContractTest {
    @Test fun sendsOnlyMessagesWithoutTruncatingOrIncludingClientMetadata() {
        assertEquals("https://kairos-web.kirosapp.workers.dev/api/ai", AiContract.URL)
        val text = "a".repeat(5000)
        val body = Json.parseToJsonElement(AiContract.body(listOf(ChatMessage("user", text, 123))).decodeToString()).jsonObject
        assertEquals(setOf("messages"), body.keys)
        val message = body.getValue("messages").jsonArray.single().jsonObject
        assertEquals(setOf("role", "content"), message.keys)
        assertEquals(text, message.getValue("content").jsonPrimitive.content)
    }

    @Test fun rejectsInvalidMessagesAndMeasuresUtf8BodySize() {
        for (messages in listOf(emptyList(), listOf(ChatMessage("tool", "Hi")), listOf(ChatMessage("user", " ")),
            List(41) { ChatMessage("user", "Hi") }, listOf(ChatMessage("user", "界".repeat(50000))))) {
            assertThrows(IllegalArgumentException::class.java) { AiContract.body(messages) }
        }
    }

    @Test fun preservesServerAllowanceErrorsAndRejectsMalformedOrTruncatedReplies() {
        val error = assertThrows(AiServiceException::class.java) {
            AiContract.response("""{"error":"Weekly allowance reached.","code":"weekly-account","resetsAt":"2026-11-09T00:00:00Z"}""", 429)
        }
        assertEquals(429, error.status)
        assertEquals("weekly-account", error.code)
        assertEquals("2026-11-09T00:00:00Z", error.resetsAt)
        assertEquals("Weekly allowance reached.", error.message)
        assertEquals("Hello", AiContract.response("""{"choices":[{"message":{"content":"Hello"}}]}""", 200))
        for (text in listOf("<html>error</html>", "{}", """{"choices":[{"message":{"content":42}}]}""",
            """{"choices":[{"finish_reason":"length","message":{"content":"Partial"}}]}""")) {
            assertThrows(AiServiceException::class.java) { AiContract.response(text, 200) }
        }
    }
}

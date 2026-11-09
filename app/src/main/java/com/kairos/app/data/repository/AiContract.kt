package com.kairos.app.data.repository

import com.kairos.app.data.models.ChatMessage
import kotlinx.serialization.json.*

class AiServiceException(val status: Int, val code: String?, val resetsAt: String?, message: String) : Exception(message)

internal object AiContract {
    const val URL = "https://kairos-web.kirosapp.workers.dev/api/ai"

    fun body(messages: List<ChatMessage>): ByteArray {
        require(messages.isNotEmpty() && messages.size <= 40 && messages.all {
            it.role in setOf("system", "user", "assistant") && it.content.isNotBlank()
        }) { "This conversation cannot be sent. Start a new chat and try again." }
        val body = buildJsonObject {
            put("messages", buildJsonArray {
                messages.forEach { message -> addJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                } }
            })
        }.toString().toByteArray(Charsets.UTF_8)
        require(body.size <= 128 * 1024) { "This conversation is too long. Start a new chat or shorten your message." }
        return body
    }

    fun response(text: String, status: Int): String {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (status != 200) throw AiServiceException(status,
            (root?.get("code") as? JsonPrimitive)?.contentOrNull,
            (root?.get("resetsAt") as? JsonPrimitive)?.contentOrNull,
            (root?.get("error") as? JsonPrimitive)?.contentOrNull?.take(500)
                ?: "Kairos AI is temporarily unavailable. Please try again later.")
        val choice = (root?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val content = message?.get("content") as? JsonPrimitive
        if ((choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull == "length" ||
            content?.isString != true || content.content.isBlank()) {
            throw AiServiceException(502, null, null, "Kairos AI could not finish its response. Try a shorter request.")
        }
        return content.content
    }
}

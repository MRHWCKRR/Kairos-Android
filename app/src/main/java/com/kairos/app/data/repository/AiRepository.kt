package com.kairos.app.data.repository

import android.util.Log
import com.kairos.app.data.models.KairosScheduleEvent
import com.kairos.app.data.models.KairosSection
import com.kairos.app.data.models.KairosTask
import kotlinx.serialization.json.*

class AiRepository {

    suspend fun generatePlan(
        input: String,
        languageName: String,
        scheduleSummary: String,
        userContext: String
    ): AiResponse {
        val systemPrompt = """
            You are an expert AI Study Coach. The user will provide a syllabus, assignment, or goal.
            Break it down into logical, actionable study sections and tasks.
            IMPORTANT: Write all section titles and task titles in $languageName.
            ADDITIONALLY: if the user's text mentions any RECURRING weekly commitment, extract each one as a recurring event. Only extract things that repeat weekly on a fixed day/time.
            
            CRITICAL INSTRUCTION: You MUST respond with ONLY a valid, raw JSON object.
            Do NOT include markdown formatting, backticks, or the word 'json'.
            Just the raw object, using this exact structure:
            {
              "sections": [
                {
                  "title": "Section 1: Research",
                  "tasks": [
                    { "title": "Find 3 academic sources" },
                    { "title": "Read and highlight sources" }
                  ]
                }
              ]
            }
            $scheduleSummary
            $userContext
            
            User's Request:
            $input
        """.trimIndent()

        val messages = listOf(
            com.kairos.app.data.models.ChatMessage(role = "system", content = "You are an expert study coach that outputs raw JSON."),
            com.kairos.app.data.models.ChatMessage(role = "user", content = systemPrompt.take(4000))
        )
        
        val rawResponse = sendChatRequest(messages)
        val text = rawResponse.replace("```json", "").replace("```", "").trim()
        
        Log.d("AiRepository", "AI Raw Response: $text")

        val json = Json { ignoreUnknownKeys = true }
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            Log.e("AiRepository", "JSON Parse Error", e)
            throw Exception("AI response was not valid JSON: ${e.message}")
        }

        val uniqueId = System.currentTimeMillis()
        
        val sections = root["sections"]?.jsonArray?.mapIndexed { sIndex, secElement ->
            val secObj = secElement.jsonObject
            com.kairos.app.data.models.KairosSection(
                id = "ai-sec-$uniqueId-$sIndex",
                title = secObj["title"]?.jsonPrimitive?.content ?: "Untitled Section",
                tasks = secObj["tasks"]?.jsonArray?.mapIndexed { tIndex, taskElement ->
                    val taskObj = taskElement.jsonObject
                    com.kairos.app.data.models.KairosTask(
                        id = "ai-task-$uniqueId-$sIndex-$tIndex",
                        title = taskObj["title"]?.jsonPrimitive?.content ?: "Untitled Task",
                        completed = false
                    )
                } ?: emptyList()
            )
        } ?: emptyList()

        return AiResponse(sections, emptyList()) // Simplified for now
    }

    suspend fun generateText(prompt: String): String {
        val messages = listOf(
            com.kairos.app.data.models.ChatMessage(role = "system", content = "You are a supportive productivity coach."),
            com.kairos.app.data.models.ChatMessage(role = "user", content = prompt.take(4000))
        )
        return sendChatRequest(messages)
    }

    /**
     * Chatbot: Calls the Kairos Relay (Hack Club AI Proxy).
     */
    suspend fun sendChatRequest(messages: List<com.kairos.app.data.models.ChatMessage>): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = java.net.URL("https://kairos.kirosapp.workers.dev")
        val connection = url.openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            // This secret is injected from local.properties during build
            connection.setRequestProperty("X-Kairos-Auth", com.kairos.app.BuildConfig.KAIROS_RELAY_SECRET) 
            connection.doOutput = true
            connection.connectTimeout = 60000 // 60 seconds for complex board generation
            connection.readTimeout = 60000

            // Safely build the JSON body using kotlinx.serialization
            val serializer = Json { ignoreUnknownKeys = true }
            val bodyObj = buildJsonObject {
                put("messages", buildJsonArray {
                    messages.forEach { msg ->
                        addJsonObject {
                            put("role", msg.role)
                            // Truncate to 4000 characters to match proxy limit
                            val safeContent = if (msg.content.length > 4000) {
                                msg.content.take(3997) + "..."
                            } else {
                                msg.content
                            }
                            put("content", safeContent)
                        }
                    }
                })
            }
            val body = serializer.encodeToString(JsonObject.serializer(), bodyObj)

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e("AiRepository", "Chat proxy error ${connection.responseCode}: $error")
                throw Exception("Chat proxy error ${connection.responseCode}")
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            root["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: throw Exception("Malformed AI response")
        } finally {
            connection.disconnect()
        }
    }
}

data class AiResponse(
    val sections: List<KairosSection>,
    val recurringEvents: List<KairosScheduleEvent>
)

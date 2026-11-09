package com.kairos.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
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

        val json = Json { ignoreUnknownKeys = true }
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
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

    suspend fun sendChatRequest(messages: List<com.kairos.app.data.models.ChatMessage>): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val body = AiContract.body(messages)
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: throw Exception("Sign in to use Kairos AI.")
        val identity = user.getIdToken(false).await()
        check(identity.claims["email_verified"] == true) { "Verify your email before using Kairos AI." }
        val idToken = identity.token
        val appToken = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        check(!idToken.isNullOrBlank() && appToken.isNotBlank()) { "Kairos could not verify this app session. Sign in again and retry." }
        val context = currentCoroutineContext()
        fun requireOwner() {
            context.ensureActive()
            check(auth.currentUser?.uid == user.uid) { "Your account changed. Sign in again and retry." }
        }
        requireOwner()
        val connection = java.net.URL(AiContract.URL).openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("X-Firebase-AppCheck", appToken)
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 100000
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status == 200) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            requireOwner()
            AiContract.response(response, status)
        } finally {
            connection.disconnect()
        }
    }
}

data class AiResponse(
    val sections: List<KairosSection>,
    val recurringEvents: List<KairosScheduleEvent>
)

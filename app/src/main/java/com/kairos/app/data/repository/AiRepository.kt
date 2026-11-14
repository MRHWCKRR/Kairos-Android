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

open class AiRepository {

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
              ],
              "recurringEvents": [
                { "title": "Weekly commitment", "category": "other", "day": 1, "start": "16:00", "end": "17:00" }
              ]
            }
            Only include explicitly requested weekly commitments; otherwise recurringEvents is empty. day is 0=Sunday through 6=Saturday. Times use HH:MM; categories are sleep,class,tutoring,training,other.
            $scheduleSummary
            $userContext
            
            User's Request:
            $input
        """.trimIndent()

        val messages = listOf(
            com.kairos.app.data.models.ChatMessage(role = "system", content = "You are an expert study coach that outputs raw JSON."),
            com.kairos.app.data.models.ChatMessage(role = "user", content = systemPrompt.take(4000))
        )
        
        return parsePlan(sendChatRequest(messages))
    }

    companion object {
        internal fun parsePlan(raw: String): AiResponse {
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root = Json.parseToJsonElement(clean).jsonObject
            val identity = java.util.UUID.randomUUID().toString()
            fun title(value: JsonElement?): String {
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
                require(text.isNotEmpty() && text.length <= 180) { "Kairos returned an invalid plan title. Request a shorter plan." }
                return text
            }
            val sections = (root["sections"] as? JsonArray ?: error("Kairos did not return plan sections.")).mapIndexed { sectionIndex, value ->
                val section = value.jsonObject
                val tasks = (section["tasks"] as? JsonArray ?: error("Kairos did not return tasks.")).mapIndexed { taskIndex, task ->
                    KairosTask(id = "ai-task-$identity-$sectionIndex-$taskIndex", title = title(task.jsonObject["title"]))
                }
                KairosSection(id = "ai-section-$identity-$sectionIndex", title = title(section["title"]), tasks = tasks)
            }
            require(sections.any { it.tasks.isNotEmpty() }) { "Kairos did not return a usable plan." }
            val eventValues = root["recurringEvents"]
            require(eventValues == null || eventValues is JsonArray) { "Kairos returned invalid recurring events." }
            val events = (eventValues as? JsonArray).orEmpty().mapIndexed { index, value ->
                val event = value.jsonObject
                val day = event["day"]?.jsonPrimitive?.intOrNull
                val start = event["start"]?.jsonPrimitive?.content.orEmpty()
                val end = event["end"]?.jsonPrimitive?.content.orEmpty()
                val category = event["category"]?.jsonPrimitive?.content ?: "other"
                val time = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                require(day != null && day in 0..6 && time.matches(start) && time.matches(end) && start < end &&
                    category in setOf("sleep", "class", "tutoring", "training", "other")) { "Kairos returned an invalid recurring event. Review the times and ask again." }
                KairosScheduleEvent(id = "ai-event-$identity-$index", title = title(event["title"]), category = category, day = day, start = start, end = end)
            }
            return AiResponse(sections, events)
        }
    }

    suspend fun generateText(prompt: String): String {
        val messages = listOf(
            com.kairos.app.data.models.ChatMessage(role = "system", content = "You are a supportive productivity coach."),
            com.kairos.app.data.models.ChatMessage(role = "user", content = prompt.take(4000))
        )
        return sendChatRequest(messages)
    }

    open suspend fun sendChatRequest(messages: List<com.kairos.app.data.models.ChatMessage>): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

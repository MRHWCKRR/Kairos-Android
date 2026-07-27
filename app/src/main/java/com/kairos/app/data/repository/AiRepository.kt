package com.kairos.app.data.repository

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.kairos.app.data.models.KairosScheduleEvent
import com.kairos.app.data.models.KairosSection
import com.kairos.app.data.models.KairosTask
import kotlinx.serialization.json.*

class AiRepository {

    suspend fun generatePlan(
        input: String,
        apiKey: String,
        languageName: String,
        scheduleSummary: String,
        userContext: String
    ): AiResponse {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )

        val systemPrompt = """
            You are an expert AI Study Coach. The user will provide a syllabus, assignment, or goal.
            Break it down into logical, actionable study sections and tasks.
            IMPORTANT: Write all section titles and task titles in $languageName, since that is the user's chosen app language.
            ADDITIONALLY: if the user's text mentions any RECURRING weekly commitment (e.g. "I have football training every Tuesday 4-6pm", "I sleep from 10pm to 6am", "math class on Mondays and Wednesdays 9-10am"), extract each one as a recurring event. Only extract things that repeat weekly on a fixed day/time — do NOT extract one-off deadlines or dates, those belong in tasks instead. CRITICAL: only extract commitments that are NEW — if a commitment is already listed under "recurring weekly commitments" below, do NOT include it again in recurringEvents, even if the user's text also mentions it. If nothing new is mentioned, return an empty array for recurringEvents. CRITICAL: "day" must ALWAYS be a single integer 0-6, never a string, range, or array. If a commitment repeats on multiple days (e.g. "every weekday", "Monday and Wednesday", "weekends"), you MUST output one separate event object per day, each with its own single "day" integer — e.g. "weekdays 9:30pm-7:30am" becomes 5 separate objects with day 1, 2, 3, 4, and 5, each identical except for "day".            

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
                { "title": "Football Training", "category": "training", "day": 2, "start": "16:00", "end": "18:00" }
              ]
            }
            "day" is 0-6 where 0=Sunday, 1=Monday, ... 6=Saturday. "category" must be one of: sleep, class, tutoring, training, other. Times are 24-hour "HH:MM".
            $scheduleSummary
            $userContext
            
            User's Request:
            $input
        """.trimIndent()

        val response = generativeModel.generateContent(systemPrompt)
        val text = response.text?.replace("```json", "")?.replace("```", "")?.trim() ?: throw Exception("Empty AI response")
        
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
            KairosSection(
                id = "ai-sec-$uniqueId-$sIndex",
                title = secObj["title"]?.jsonPrimitive?.content ?: "Untitled Section",
                tasks = secObj["tasks"]?.jsonArray?.mapIndexed { tIndex, taskElement ->
                    val taskObj = taskElement.jsonObject
                    KairosTask(
                        id = "ai-task-$uniqueId-$sIndex-$tIndex",
                        title = taskObj["title"]?.jsonPrimitive?.content ?: "Untitled Task",
                        completed = false
                    )
                } ?: emptyList()
            )
        } ?: emptyList()

        val recurringEvents = root["recurringEvents"]?.jsonArray?.mapIndexed { i, evElement ->
            val evObj = evElement.jsonObject
            KairosScheduleEvent(
                id = "ai-sched-$uniqueId-$i",
                title = evObj["title"]?.jsonPrimitive?.content ?: "Untitled Event",
                category = evObj["category"]?.jsonPrimitive?.content ?: "other",
                day = evObj["day"]?.jsonPrimitive?.intOrNull ?: 1,
                start = evObj["start"]?.jsonPrimitive?.content ?: "09:00",
                end = evObj["end"]?.jsonPrimitive?.content ?: "10:00"
            )
        } ?: emptyList()

        return AiResponse(sections, recurringEvents)
    }

    suspend fun generateText(prompt: String, apiKey: String): String {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
        val response = generativeModel.generateContent(prompt)
        return response.text ?: throw Exception("Empty AI response")
    }
}

data class AiResponse(
    val sections: List<KairosSection>,
    val recurringEvents: List<KairosScheduleEvent>
)

package com.kairos.app.ui.screens.calendar

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.local.PreferenceManager
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.repository.AiRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarViewModel @JvmOverloads constructor(
    application: Application,
    private val aiRepository: AiRepository = AiRepository()
) : AndroidViewModel(application) {

    private val preferenceManager = PreferenceManager(application)

    var currentMonth by mutableStateOf(YearMonth.now())
    var selectedDate by mutableStateOf<LocalDate?>(null)
    var isGeneratingInsight by mutableStateOf(false)
    var insightError by mutableStateOf<String?>(null)

    fun onPreviousMonth() {
        currentMonth = currentMonth.minusMonths(1)
    }

    fun onNextMonth() {
        currentMonth = currentMonth.plusMonths(1)
    }

    fun generateInsight(date: LocalDate, plan: KairosPlan?, onResult: (String) -> Unit) {
        val geminiKey = preferenceManager.getGeminiKey()
        if (geminiKey.isNullOrBlank()) {
            insightError = "Add a Gemini API key in Settings to unlock AI insights."
            return
        }

        val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val tasksForDay = plan?.boards?.flatMap { it.sections }?.flatMap { it.tasks }
            ?.filter { it.date == dateKey && !it.archived } ?: emptyList()

        if (tasksForDay.isEmpty()) {
            insightError = "No tasks scheduled — nothing to comment on."
            return
        }

        isGeneratingInsight = true
        insightError = null

        viewModelScope.launch {
            try {
                val taskSummary = tasksForDay.joinToString("\n") { "- ${it.title} [${if (it.completed) "done" else "pending"}]" }
                val prompt = "You are a supportive productivity coach. Here is a user's task list for $dateKey:\n$taskSummary\n\nWrite a short, encouraging 1-2 sentence comment about their day in English. Be specific about what they've completed or still need to do. Do not use markdown formatting."
                
                val result = aiRepository.generateText(prompt, geminiKey)
                onResult(result)
            } catch (e: Exception) {
                insightError = "Couldn't generate an insight right now."
            } finally {
                isGeneratingInsight = false
            }
        }
    }
}

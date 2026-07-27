package com.kairos.app.ui.screens.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.local.PreferenceManager
import com.kairos.app.data.models.KairosBoard
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.repository.AiRepository
import com.kairos.app.data.repository.AiResponse
import kotlinx.coroutines.launch

class AiHelperViewModel @JvmOverloads constructor(
    application: Application,
    private val aiRepository: AiRepository = AiRepository()
) : AndroidViewModel(application) {

    private val preferenceManager = PreferenceManager(application)
    
    var userInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    
    var pendingResponse by mutableStateOf<AiResponse?>(null)
    var showConfirmationDialog by mutableStateOf(false)
    
    // Board Selection for Confirmation
    var targetBoardMode by mutableStateOf(TargetBoardMode.NEW)
    var newBoardName by mutableStateOf("AI Plan")
    var selectedExistingBoardId by mutableStateOf("")

    fun onGenerate(currentPlan: KairosPlan?) {
        val geminiKey = preferenceManager.getGeminiKey() 
        
        if (geminiKey.isNullOrBlank()) {
            errorMessage = "Please go to Settings and save your Gemini API Key first!"
            return
        }
        
        if (userInput.isBlank()) {
            errorMessage = "Empty input, please put your assignment details or a syllabus in first!"
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                // Future improvement: Build scheduleSummary from currentPlan
                val result = aiRepository.generatePlan(
                    input = userInput,
                    apiKey = geminiKey,
                    languageName = "English",
                    scheduleSummary = "",
                    userContext = ""
                )
                pendingResponse = result
                showConfirmationDialog = true
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "AI generation failed."
            } finally {
                isLoading = false
            }
        }
    }

    fun onConfirm(currentPlan: KairosPlan?): KairosPlan {
        val response = pendingResponse ?: return currentPlan ?: KairosPlan()
        val plan = currentPlan ?: KairosPlan()
        
        val updatedBoards = plan.boards.toMutableList()
        
        if (targetBoardMode == TargetBoardMode.NEW) {
            updatedBoards.add(
                KairosBoard(
                    id = "board-${System.currentTimeMillis()}",
                    title = newBoardName.ifBlank { "AI Plan" },
                    sections = response.sections
                )
            )
        } else {
            val boardIndex = updatedBoards.indexOfFirst { it.id == selectedExistingBoardId }
            if (boardIndex != -1) {
                val board = updatedBoards[boardIndex]
                updatedBoards[boardIndex] = board.copy(
                    sections = board.sections + response.sections
                )
            }
        }

        val updatedSchedule = plan.scheduleEvents + response.recurringEvents
        
        showConfirmationDialog = false
        userInput = ""
        pendingResponse = null
        
        return plan.copy(
            boards = updatedBoards,
            scheduleEvents = updatedSchedule
        )
    }
    
    fun dismissDialog() {
        showConfirmationDialog = false
        pendingResponse = null
    }
}

enum class TargetBoardMode { NEW, EXISTING }

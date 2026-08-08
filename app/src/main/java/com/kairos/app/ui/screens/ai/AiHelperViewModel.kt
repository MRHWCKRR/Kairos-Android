package com.kairos.app.ui.screens.ai

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.local.PreferenceManager
import com.kairos.app.data.models.*
import com.kairos.app.data.repository.AiRepository
import com.kairos.app.data.repository.AiResponse
import com.kairos.app.data.repository.FirebaseRepository
import com.kairos.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AiHelperViewModel @JvmOverloads constructor(
    application: Application,
    private val aiRepository: AiRepository = AiRepository(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : AndroidViewModel(application) {

    private val preferenceManager = PreferenceManager(application)
    
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    var userInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    
    var pendingResponse by mutableStateOf<AiResponse?>(null)
    var showConfirmationDialog by mutableStateOf(false)
    
    // Board Selection for Confirmation
    var targetBoardMode by mutableStateOf(TargetBoardMode.NEW)
    var newBoardName by mutableStateOf("AI Plan")
    var selectedExistingBoardId by mutableStateOf("")

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            firebaseRepository.getUserProfile(user.uid)
                .catch { Log.e("AiHelperViewModel", "Failed to load history", it) }
                .collect { profile ->
                    if (profile != null) {
                        _chatMessages.value = profile.aiChatHistory
                    }
                }
        }
    }

    fun handleSend() {
        val text = userInput.trim()
        if (text.isEmpty() || isLoading) return

        val userMessage = ChatMessage(role = "user", content = text)
        _chatMessages.value = _chatMessages.value + userMessage
        userInput = ""
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                // system prompt
                val systemMessage = ChatMessage(role = "system", content = "You are a helpful, friendly assistant inside the Kairos productivity app. Always respond in English, regardless of what language the user writes in, unless they explicitly ask you to reply in a different language.")
                val apiMessages = listOf(systemMessage) + _chatMessages.value
                
                val replyContent = aiRepository.sendChatRequest(apiMessages)
                val assistantMessage = ChatMessage(role = "assistant", content = replyContent)
                
                _chatMessages.value = _chatMessages.value + assistantMessage
                saveHistory()
            } catch (e: Exception) {
                errorMessage = "AI Error: ${e.localizedMessage}"
                _chatMessages.value = _chatMessages.value + ChatMessage(role = "assistant", content = "Sorry, I hit an error: ${e.localizedMessage}")
            } finally {
                isLoading = false
            }
        }
    }

    fun createPlanFromChat(currentPlan: KairosPlan?) {
        val geminiKey = preferenceManager.getGeminiKey() 
        if (geminiKey.isNullOrBlank()) {
            errorMessage = "Please go to Settings and save your Gemini API Key first!"
            return
        }
        if (_chatMessages.value.isEmpty()) return

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val transcript = _chatMessages.value.joinToString("\n") { "${it.role}: ${it.content}" }
                val promptPrefix = "Below is a conversation transcript between a user and an AI Study Coach. " +
                                  "Please extract the core study/action plan discussed into a structured format.\n\n"
                
                val result = aiRepository.generatePlan(
                    input = promptPrefix + transcript,
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

    fun clearChat() {
        _chatMessages.value = emptyList()
        saveHistory()
    }

    private fun saveHistory() {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            try {
                firebaseRepository.updateUserProfile(user.uid, mapOf("aiChatHistory" to _chatMessages.value.takeLast(60)))
            } catch (e: Exception) {
                Log.e("AiHelperViewModel", "Failed to save history", e)
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

package com.kairos.app.ui.screens.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.kairos.app.data.models.*
import com.kairos.app.data.repository.AiRepository
import com.kairos.app.data.repository.AiResponse
import com.kairos.app.data.repository.FirebaseRepository
import com.kairos.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AiHelperViewModel @JvmOverloads constructor(
    application: Application,
    private val aiRepository: AiRepository = AiRepository(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : AndroidViewModel(application) {

    
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    var userInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var isGeneratingBoard by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    
    var pendingResponse by mutableStateOf<AiResponse?>(null)
    var showConfirmationDialog by mutableStateOf(false)
    
    // Board Selection for Confirmation
    var targetBoardMode by mutableStateOf(TargetBoardMode.NEW)
    var newBoardName by mutableStateOf("AI Plan")
    var selectedExistingBoardId by mutableStateOf("")

    private var ownerUid: String? = null
    private var session = 0L
    private var requestJob: Job? = null
    private var requestVersion = 0L
    private var profileBaseline: KairosUserProfile? = null
    private var pendingSession: Long? = null

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { user ->
                session++
                requestJob?.cancel()
                ownerUid = user?.uid
                profileBaseline = null
                _chatMessages.value = emptyList()
                userInput = ""
                isLoading = false
                isGeneratingBoard = false
                errorMessage = null
                dismissDialog()
                targetBoardMode = TargetBoardMode.NEW
                newBoardName = "AI Plan"
                selectedExistingBoardId = ""
                val uid = user?.uid ?: return@collectLatest
                val capturedSession = session
                firebaseRepository.getUserProfile(uid)
                    .catch { if (isCurrent(uid, capturedSession)) Log.e("AiHelperViewModel", "Failed to load history", it) }
                    .collect { profile ->
                        if (isCurrent(uid, capturedSession)) {
                            profileBaseline = profile ?: KairosUserProfile()
                            if (!isLoading) _chatMessages.value = profile?.aiChatHistory.orEmpty()
                        }
                    }
            }
        }
    }

    private fun isCurrent(uid: String, capturedSession: Long): Boolean =
        ownerUid == uid && session == capturedSession && authRepository.currentUser?.uid == uid

    fun handleSend() {
        val uid = ownerUid ?: return
        val capturedSession = session
        val baseline = profileBaseline ?: return
        if (!isCurrent(uid, capturedSession)) return
        val text = userInput.trim()
        if (text.isEmpty() || isLoading) return

        val userMessage = ChatMessage(role = "user", content = text)
        _chatMessages.value = _chatMessages.value + userMessage
        userInput = ""
        isLoading = true
        errorMessage = null

        val version = ++requestVersion
        requestJob = viewModelScope.launch {
            try {
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                // system prompt
                val systemMessage = ChatMessage(role = "system", content = "You are a helpful, friendly assistant inside the Kairos productivity app. Always respond in English, regardless of what language the user writes in, unless they explicitly ask you to reply in a different language.")
                
                // Proxy limit is 40 messages total. We take the last 38 to allow for system prompt + history.
                val chatHistory = _chatMessages.value.takeLast(38)
                val apiMessages = listOf(systemMessage) + chatHistory
                
                val replyContent = aiRepository.sendChatRequest(apiMessages)
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                val assistantMessage = ChatMessage(role = "assistant", content = replyContent)
                
                _chatMessages.value = _chatMessages.value + assistantMessage
                saveHistory(uid, capturedSession, baseline, _chatMessages.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                errorMessage = "AI Error: ${e.localizedMessage}"
                _chatMessages.value = _chatMessages.value + ChatMessage(role = "assistant", content = "Sorry, I hit an error: ${e.localizedMessage}")
            } finally {
                if (isCurrent(uid, capturedSession) && version == requestVersion) isLoading = false
            }
        }
    }

    fun createPlanFromChat(currentPlan: KairosPlan?) {
        val uid = ownerUid ?: return
        val capturedSession = session
        if (!isCurrent(uid, capturedSession) || isLoading || _chatMessages.value.isEmpty()) return

        isLoading = true
        isGeneratingBoard = true
        errorMessage = null

        val version = ++requestVersion
        requestJob = viewModelScope.launch {
            try {
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                // Focus on the core discussion for faster parsing
                val transcript = _chatMessages.value.takeLast(20).joinToString("\n") { "${it.role}: ${it.content}" }
                val promptPrefix = "Extract a structured routine from this chat into raw JSON sections and tasks.\n\n"
                
                val result = aiRepository.generatePlan(
                    input = promptPrefix + transcript,
                    languageName = "English",
                    scheduleSummary = "",
                    userContext = ""
                )
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                pendingSession = capturedSession
                pendingResponse = result
                showConfirmationDialog = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                errorMessage = "Board generation failed: ${e.localizedMessage}"
            } finally {
                if (isCurrent(uid, capturedSession) && version == requestVersion) {
                    isLoading = false
                    isGeneratingBoard = false
                }
            }
        }
    }

    fun clearChat() {
        val uid = ownerUid ?: return
        val capturedSession = session
        val baseline = profileBaseline ?: return
        if (!isCurrent(uid, capturedSession)) return
        requestVersion++
        requestJob?.cancel()
        isLoading = false
        isGeneratingBoard = false
        dismissDialog()
        _chatMessages.value = emptyList()
        saveHistory(uid, capturedSession, baseline, emptyList())
    }

    private fun saveHistory(uid: String, capturedSession: Long, baseline: KairosUserProfile, messages: List<ChatMessage>) {
        viewModelScope.launch {
            try {
                if (!isCurrent(uid, capturedSession)) return@launch
                firebaseRepository.updateUserProfile(uid, baseline, mapOf("aiChatHistory" to messages.takeLast(38)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiHelperViewModel", "Failed to save history", e)
            }
        }
    }

    fun onConfirm(currentPlan: KairosPlan?): KairosPlan? {
        val uid = ownerUid ?: return null
        if (pendingSession != session || !isCurrent(uid, session)) return null
        val response = pendingResponse ?: return null
        val plan = currentPlan ?: return null

        val updatedBoards = plan.boards.toMutableList()
        
        if (targetBoardMode == TargetBoardMode.NEW) {
            // Generate unique IDs for imported tasks/sections to ensure sync works
            val timestamp = System.currentTimeMillis()
            val newSections = response.sections.mapIndexed { sIdx, sec ->
                sec.copy(
                    id = "ai-sec-$timestamp-$sIdx",
                    tasks = sec.tasks.mapIndexed { tIdx, task ->
                        task.copy(id = "ai-task-$timestamp-$sIdx-$tIdx")
                    }
                )
            }
            
            updatedBoards.add(
                KairosBoard(
                    id = "board-$timestamp",
                    title = newBoardName.ifBlank { "AI Plan" },
                    sections = newSections
                )
            )
        } else {
            val boardIndex = updatedBoards.indexOfFirst { it.id == selectedExistingBoardId }
            if (boardIndex != -1) {
                val board = updatedBoards[boardIndex]
                val timestamp = System.currentTimeMillis()
                val newSections = response.sections.mapIndexed { sIdx, sec ->
                    sec.copy(
                        id = "ai-sec-$timestamp-$sIdx",
                        tasks = sec.tasks.mapIndexed { tIdx, task ->
                            task.copy(id = "ai-task-$timestamp-$sIdx-$tIdx")
                        }
                    )
                }
                updatedBoards[boardIndex] = board.copy(
                    sections = board.sections + newSections
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
        pendingSession = null
        showConfirmationDialog = false
        pendingResponse = null
    }
}

enum class TargetBoardMode { NEW, EXISTING }

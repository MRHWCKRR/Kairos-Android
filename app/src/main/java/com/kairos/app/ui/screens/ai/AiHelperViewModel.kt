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
import com.kairos.app.data.repository.SharedDataMerge
import com.kairos.app.data.repository.sharedFields
import com.kairos.app.data.repository.profilePatch
import com.kairos.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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
    var historySaveError by mutableStateOf<String?>(null)
        private set
    var isHistoryBlocked by mutableStateOf(true)
        private set
    var historyRecoveryMessage by mutableStateOf<String?>(null)
        private set
    var isSavingHistory by mutableStateOf(false)
        private set
    var isJournalSaving by mutableStateOf(false)
        private set
    
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
    private var historyDirty = false
    private var latestObservedProfile: KairosUserProfile? = null
    private var historySaveJob: Job? = null
    private data class HistorySave(
        val uid: String,
        val session: Long,
        val baseline: KairosUserProfile,
        val messages: List<ChatMessage>,
        val id: String = UUID.randomUUID().toString()
    )
    private var pendingHistorySave: HistorySave? = null
    private var journalHistoryIds = emptySet<String>()
    private var pendingHistoryWasJournaled = false
    private var journalLoaded = false
    private var journalError: String? = null

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { user ->
                session++
                requestJob?.cancel()
                historySaveJob?.cancel()
                pendingHistorySave = null
                journalHistoryIds = emptySet()
                pendingHistoryWasJournaled = false
                journalLoaded = false
                journalError = null
                isHistoryBlocked = true
                isJournalSaving = false
                historyRecoveryMessage = null
                historyDirty = false
                isSavingHistory = false
                historySaveError = null
                latestObservedProfile = null
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
                coroutineScope {
                    launch {
                        firebaseRepository.observePendingEditStatus(uid)
                            .catch {
                                if (isCurrent(uid, capturedSession)) {
                                    journalError = it.localizedMessage ?: "Pending changes could not be read."
                                    updateHistoryGate()
                                }
                            }
                            .collect { status ->
                                if (!isCurrent(uid, capturedSession)) return@collect
                                val previousIds = journalHistoryIds
                                journalLoaded = true
                                journalError = status.error
                                isJournalSaving = status.isSaving
                                journalHistoryIds = status.edits.filter {
                                    it.kind == "profile" && it.base["aiChatHistory"] != it.edited["aiChatHistory"]
                                }.map { it.id }.toSet()
                                val local = pendingHistorySave
                                if (local != null && local.id in journalHistoryIds) pendingHistoryWasJournaled = true
                                updateHistoryGate()
                                if (status.error == null && !isSavingHistory && (
                                    (local != null && pendingHistoryWasJournaled && local.id !in journalHistoryIds) ||
                                    (local == null && previousIds.isNotEmpty() && journalHistoryIds.isEmpty())
                                )) {
                                    reconcileSavedHistory(uid, capturedSession, local)
                                }
                            }
                    }
                    firebaseRepository.getUserProfile(uid)
                        .catch { if (isCurrent(uid, capturedSession)) Log.e("AiHelperViewModel", "Failed to load history", it) }
                        .collect { profile ->
                            if (isCurrent(uid, capturedSession)) {
                                latestObservedProfile = profile ?: KairosUserProfile()
                                if (!historyDirty) {
                                    profileBaseline = latestObservedProfile
                                    if (!isLoading) _chatMessages.value = profile?.aiChatHistory.orEmpty()
                                }
                            }
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
        if (text.isEmpty() || isLoading || historyDirty || isHistoryBlocked) return

        historyDirty = true
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
                // A request-local timeout can cancel the request while this account session
                // remains active. Persist its user message so the dirty composer can recover.
                if (currentCoroutineContext().isActive && isCurrent(uid, capturedSession) && version == requestVersion) {
                    errorMessage = "AI request was interrupted. You can send another message after the chat is saved."
                    saveHistory(uid, capturedSession, baseline, _chatMessages.value)
                }
                throw e
            } catch (e: Exception) {
                if (!isCurrent(uid, capturedSession) || version != requestVersion) return@launch
                errorMessage = "AI Error: ${e.localizedMessage}"
                _chatMessages.value = _chatMessages.value + ChatMessage(role = "assistant", content = "Sorry, I hit an error: ${e.localizedMessage}")
                saveHistory(uid, capturedSession, baseline, _chatMessages.value)
            } finally {
                if (isCurrent(uid, capturedSession) && version == requestVersion) isLoading = false
            }
        }
    }

    fun createPlanFromChat(currentPlan: KairosPlan?) {
        val uid = ownerUid ?: return
        val capturedSession = session
        if (!isCurrent(uid, capturedSession) || isLoading || isHistoryBlocked || _chatMessages.value.isEmpty()) return

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
        if (!isCurrent(uid, capturedSession) || isSavingHistory || isHistoryBlocked || pendingHistorySave != null) return
        requestVersion++
        requestJob?.cancel()
        isLoading = false
        isGeneratingBoard = false
        dismissDialog()
        _chatMessages.value = emptyList()
        saveHistory(uid, capturedSession, baseline, emptyList())
    }

    private fun saveHistory(uid: String, capturedSession: Long, baseline: KairosUserProfile, messages: List<ChatMessage>) {
        if (!isCurrent(uid, capturedSession)) return
        historyDirty = true
        val save = HistorySave(uid, capturedSession, baseline, messages.takeLast(38).toList())
        pendingHistorySave = save
        pendingHistoryWasJournaled = false
        attemptHistorySave(save)
    }

    fun retryHistorySave() {
        val save = pendingHistorySave ?: return
        if (isSavingHistory || isJournalSaving || !isCurrent(save.uid, save.session)) return
        attemptHistorySave(save, retryRetained = pendingHistoryWasJournaled)
    }

    fun reloadSavedChat() {
        val save = pendingHistorySave ?: return
        if (isSavingHistory || isJournalSaving || !isCurrent(save.uid, save.session)) return
        isSavingHistory = true
        historySaveJob = viewModelScope.launch {
            try {
                // Discard the durable intent before releasing the local dirty state.
                firebaseRepository.discardPendingEdit(save.uid, save.id)
                if (!isCurrent(save.uid, save.session) || pendingHistorySave !== save) return@launch
                reconcileSavedHistory(save.uid, save.session, save)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrent(save.uid, save.session) && pendingHistorySave === save) {
                    historySaveError = "Saved draft could not be discarded. ${e.localizedMessage.orEmpty()}"
                }
            } finally {
                if (isCurrent(save.uid, save.session)) isSavingHistory = false
            }
        }
    }

    private fun updateHistoryGate() {
        isHistoryBlocked = !journalLoaded || journalError != null || journalHistoryIds.isNotEmpty() || isJournalSaving
        historyRecoveryMessage = when {
            journalError != null -> "Pending changes could not be read. Recover or discard them using the saved changes banner before editing chat."
            journalHistoryIds.any { it != pendingHistorySave?.id } -> "An unsaved chat change is stored on this device. Retry or discard it using the saved changes banner before editing chat."
            else -> null
        }
    }

    private suspend fun reconcileSavedHistory(uid: String, capturedSession: Long, save: HistorySave?) {
        if (!isCurrent(uid, capturedSession)) return
        isHistoryBlocked = true
        // A global retry/discard can finish before the existing profile callback is delivered.
        val observed = try {
            firebaseRepository.getUserProfile(uid).first() ?: KairosUserProfile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrent(uid, capturedSession)) return
            errorMessage = "Saved chat could not be refreshed. ${e.localizedMessage.orEmpty()}"
            latestObservedProfile
        }
        if (!isCurrent(uid, capturedSession) || pendingHistorySave !== save) return
        requestVersion++
        requestJob?.cancel()
        isLoading = false
        isGeneratingBoard = false
        dismissDialog()
        latestObservedProfile = observed
        profileBaseline = observed
        _chatMessages.value = observed?.aiChatHistory.orEmpty()
        historyDirty = false
        pendingHistorySave = null
        pendingHistoryWasJournaled = false
        historySaveError = null
        updateHistoryGate()
    }

    private fun attemptHistorySave(save: HistorySave, retryRetained: Boolean = false) {
        isSavingHistory = true
        historySaveError = null
        historySaveJob = viewModelScope.launch {
            try {
                if (!isCurrent(save.uid, save.session) || pendingHistorySave !== save) return@launch
                if (retryRetained) {
                    // A global discard/acknowledgement must never be reversed by a stale Retry.
                    firebaseRepository.retryPendingEdit(save.uid, save.id)
                    if (isCurrent(save.uid, save.session) && pendingHistorySave === save) {
                        reconcileSavedHistory(save.uid, save.session, save)
                    }
                    return@launch
                }
                firebaseRepository.updateUserProfile(save.uid, save.baseline, mapOf("aiChatHistory" to save.messages), operationId = save.id)
                if (!isCurrent(save.uid, save.session) || pendingHistorySave !== save) return@launch
                // An unrelated profile event cannot discard unsaved local chat. After acknowledgement,
                // advance only this field if the matching server snapshot has not arrived yet.
                profileBaseline = latestObservedProfile?.takeIf { it.aiChatHistory == save.messages }?.copy(documentExists = true)
                    ?: save.baseline.copy(
                        aiChatHistory = save.messages,
                        documentExists = true,
                        sourceFields = SharedDataMerge.merge(
                            save.baseline.sharedFields(),
                            profilePatch(save.baseline, mapOf("aiChatHistory" to save.messages)),
                            save.baseline.sourceFields,
                            save.baseline.sourceFields
                        )
                    )
                historyDirty = false
                pendingHistorySave = null
                pendingHistoryWasJournaled = false
                historySaveError = null
                updateHistoryGate()
            } catch (e: CancellationException) {
                // The repository also uses cancellation when a global action already removed
                // this operation. Recover saved history without recreating that durable intent.
                if (currentCoroutineContext().isActive && isCurrent(save.uid, save.session) && pendingHistorySave === save) {
                    reconcileSavedHistory(save.uid, save.session, save)
                }
                throw e
            } catch (e: Exception) {
                if (isCurrent(save.uid, save.session) && pendingHistorySave === save) {
                    historySaveError = "Chat changes couldn't be saved. Retry, or reload saved chat to discard local changes. ${e.localizedMessage.orEmpty()}"
                }
            } finally {
                if (isCurrent(save.uid, save.session) && (pendingHistorySave === save || pendingHistorySave == null)) {
                    isSavingHistory = false
                    if (pendingHistorySave === save && pendingHistoryWasJournaled && save.id !in journalHistoryIds && journalError == null) {
                        reconcileSavedHistory(save.uid, save.session, save)
                    }
                }
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

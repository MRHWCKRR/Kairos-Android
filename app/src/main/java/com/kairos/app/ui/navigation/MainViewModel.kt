package com.kairos.app.ui.navigation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.*
import com.kairos.app.data.repository.AuthRepository
import com.kairos.app.data.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

class MainViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _plan = MutableStateFlow<KairosPlan?>(null)
    val plan: StateFlow<KairosPlan?> = _plan.asStateFlow()

    private val _user = MutableStateFlow(authRepository.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user.asStateFlow()

    private val _profile = MutableStateFlow(KairosUserProfile())
    val profile: StateFlow<KairosUserProfile> = _profile.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Focus Timer State
    var focusTimerRunning by mutableStateOf(false)
        private set
    var focusSecondsActive by mutableStateOf(0L)
        private set
    private var focusTimerJob: Job? = null
    
    // Achievement Toast State
    var newlyUnlockedAchievement by mutableStateOf<AchievementDef?>(null)

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { firebaseUser ->
                _user.value = firebaseUser
                if (firebaseUser != null) {
                    launch {
                        firebaseRepository.getLatestPlan(firebaseUser.uid)
                            .catch { e -> errorMessage = e.localizedMessage }
                            .collect { _plan.value = it }
                    }
                    launch {
                        firebaseRepository.getUserProfile(firebaseUser.uid)
                            .catch { e -> Log.e("MainViewModel", "Profile sync error", e) }
                            .collect { if (it != null) _profile.value = it }
                    }
                } else {
                    _plan.value = null
                    _profile.value = KairosUserProfile()
                }
            }
        }
    }

    // --- Task Management ---

    fun toggleTask(taskId: String, completed: Boolean) {
        val currentPlan = _plan.value ?: return
        val currentProfile = _profile.value
        
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(completed = completed) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))

        if (completed && !currentProfile.achievements.countedTaskIds.contains(taskId)) {
            val updatedAchievements = currentProfile.achievements.copy(
                countedTaskIds = currentProfile.achievements.countedTaskIds + taskId,
                lifetimeTasksCompleted = currentProfile.achievements.lifetimeTasksCompleted + 1
            )
            
            val dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val updatedFocusData = currentProfile.focusData.copy(
                dailyTasksLog = currentProfile.focusData.dailyTasksLog.toMutableMap().apply {
                    this[dateKey] = (this[dateKey] ?: 0) + 1
                }
            )
            
            updateProfileInternal(mapOf(
                "achievements" to updatedAchievements,
                "focusData" to updatedFocusData
            ))
            
            checkAchievements(updatedAchievements, updatedFocusData)
        }
    }

    // --- Focus Timer Logic ---

    fun startFocusTimer() {
        if (focusTimerRunning) return
        focusTimerRunning = true
        focusTimerJob = viewModelScope.launch {
            while (focusTimerRunning) {
                delay(1.seconds)
                focusSecondsActive++
            }
        }
    }

    fun pauseFocusTimer() {
        focusTimerRunning = false
        focusTimerJob?.cancel()
    }

    fun stopAndLogFocus() {
        val secondsLogged = focusSecondsActive
        pauseFocusTimer()
        focusSecondsActive = 0
        
        if (secondsLogged < 1) return

        val currentProfile = _profile.value
        val dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val updatedFocusData = currentProfile.focusData.copy(
            totalSeconds = currentProfile.focusData.totalSeconds + secondsLogged,
            longestSessionSeconds = maxOf(currentProfile.focusData.longestSessionSeconds, secondsLogged),
            dailyFocusLog = currentProfile.focusData.dailyFocusLog.toMutableMap().apply {
                this[dateKey] = (this[dateKey] ?: 0L) + secondsLogged
            }
        )

        updateProfileInternal(mapOf("focusData" to updatedFocusData))
        checkAchievements(currentProfile.achievements, updatedFocusData)
    }

    // --- Achievement Engine ---

    private fun checkAchievements(achievements: KairosAchievementsData, focus: KairosFocusData) {
        KAIROS_ACHIEVEMENTS.forEach { def ->
            if (!achievements.unlocked.containsKey(def.id)) {
                val isUnlocked = when (def.type) {
                    "focus_seconds" -> focus.totalSeconds >= def.threshold
                    "tasks_completed" -> achievements.lifetimeTasksCompleted >= def.threshold.toInt()
                    else -> false
                }

                if (isUnlocked) {
                    unlockAchievement(def)
                }
            }
        }
    }

    private fun unlockAchievement(def: AchievementDef) {
        val userId = _user.value?.uid ?: return
        val updatedUnlocked = _profile.value.achievements.unlocked.toMutableMap()
        updatedUnlocked[def.id] = System.currentTimeMillis()
        
        newlyUnlockedAchievement = def
        
        viewModelScope.launch {
            firebaseRepository.updateUserProfile(userId, mapOf("achievements.unlocked" to updatedUnlocked))
        }
    }

    fun dismissAchievement() {
        newlyUnlockedAchievement = null
    }

    // --- Management Methods ---

    fun addBoard(title: String) {
        val currentPlan = _plan.value ?: KairosPlan(userID = _user.value?.uid ?: "")
        updatePlanInternal(currentPlan.copy(boards = currentPlan.boards + KairosBoard(id = "board-${System.currentTimeMillis()}", title = title)))
    }

    fun renameBoard(boardId: String, newTitle: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { if (it.id == boardId) it.copy(title = newTitle) else it }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun archiveBoard(boardId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { if (it.id == boardId) it.copy(archived = true) else it }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun addSection(boardId: String, title: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            if (board.id == boardId) board.copy(sections = board.sections + KairosSection(id = "sec-${System.currentTimeMillis()}", title = title)) else board
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun renameSection(sectionId: String, newTitle: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) section.copy(title = newTitle) else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun archiveSection(sectionId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) section.copy(archived = true) else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun addTask(sectionId: String, title: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) section.copy(tasks = section.tasks + KairosTask(id = "task-${System.currentTimeMillis()}", title = title)) else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun renameTask(taskId: String, newTitle: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(title = newTitle) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun archiveTask(taskId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(archived = true) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    // --- Persistence ---

    fun updatePlan(updatedPlan: KairosPlan) {
        updatePlanInternal(updatedPlan)
    }

    private fun updatePlanInternal(updatedPlan: KairosPlan) {
        val userId = _user.value?.uid ?: return
        _plan.value = updatedPlan
        viewModelScope.launch {
            try {
                firebaseRepository.updatePlan(userId, updatedPlan)
            } catch (e: Exception) {
                errorMessage = "Sync failed: ${e.localizedMessage}"
            }
        }
    }

    private fun updateProfileInternal(data: Map<String, Any>) {
        val userId = _user.value?.uid ?: return
        viewModelScope.launch {
            try {
                firebaseRepository.updateUserProfile(userId, data)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update profile", e)
            }
        }
    }

    fun updateDayInsight(dateKey: String, insight: String) {
        val currentPlan = _plan.value ?: return
        val updatedInsights = currentPlan.dayInsights.toMutableMap()
        updatedInsights[dateKey] = insight
        updatePlanInternal(currentPlan.copy(dayInsights = updatedInsights))
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun clearError() {
        errorMessage = null
    }
    
    fun setGoal(index: Int, achievementId: String?) {
        val userId = _user.value?.uid ?: return
        val currentGoals = _profile.value.achievements.goals.toMutableList()
        if (index < currentGoals.size) {
            currentGoals[index] = achievementId
        }
        viewModelScope.launch {
            firebaseRepository.updateUserProfile(userId, mapOf("achievements.goals" to currentGoals))
        }
    }
}

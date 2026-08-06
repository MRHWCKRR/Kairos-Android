package com.kairos.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import com.kairos.app.utils.WidgetManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.*
import com.kairos.app.data.repository.AuthRepository
import com.kairos.app.data.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    var isInitializing by mutableStateOf(true)
        private set

    var isUploadingAvatar by mutableStateOf(false)
        private set

    var settingsError by mutableStateOf<String?>(null)
        private set

    var securityError by mutableStateOf<String?>(null)
        private set

    var securitySuccess by mutableStateOf<String?>(null)
        private set

    private var pendingWidgetAction: String? = null
    private var pendingWidgetTaskId: String? = null
    var lastApplicationContext: Context? = null
        private set

    // Events for system notifications
    private val _notificationEvents = MutableSharedFlow<Pair<String, String>>()
    val notificationEvents: SharedFlow<Pair<String, String>> = _notificationEvents.asSharedFlow()

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
                            .collect { 
                                _plan.value = it
                                processPendingActions()
                            }
                    }
                    launch {
                        firebaseRepository.getUserProfile(firebaseUser.uid)
                            .catch { e -> 
                                Log.e("MainViewModel", "Profile sync error", e)
                                isInitializing = false
                            }
                            .collect { 
                                if (it != null) _profile.value = it 
                                isInitializing = false
                            }
                    }
                    launch {
                        while (true) {
                            checkBedtimeReminders()
                            delay(60000L)
                        }
                    }
                } else {
                    _plan.value = null
                    _profile.value = KairosUserProfile()
                    isInitializing = false
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
                // Aggressive Widget Sync (Every second while running)
                syncWidgetsInternal(lastApplicationContext)
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
        
        pushNotification("🎉 Achievement Unlocked!", "You earned the ${def.name} badge.")
        
        viewModelScope.launch {
            firebaseRepository.updateUserProfile(userId, mapOf("achievements.unlocked" to updatedUnlocked))
        }
    }

    // --- Notifications ---

    fun pushNotification(title: String, message: String) {
        val userId = _user.value?.uid ?: return
        val newNotif = KairosNotification(
            id = "notif-${System.currentTimeMillis()}",
            title = title,
            message = message,
            time = System.currentTimeMillis(),
            read = false
        )
        val updatedNotifications = (listOf(newNotif) + _profile.value.notifications).take(50)
        updateProfileInternal(mapOf("notifications" to updatedNotifications))
        
        // Trigger system notification event
        viewModelScope.launch {
            _notificationEvents.emit(title to message)
        }
    }

    fun markNotificationsRead() {
        val updatedNotifications = _profile.value.notifications.map { it.copy(read = true) }
        updateProfileInternal(mapOf("notifications" to updatedNotifications))
    }

    fun deleteNotification(id: String) {
        val updatedNotifications = _profile.value.notifications.filter { it.id != id }
        updateProfileInternal(mapOf("notifications" to updatedNotifications))
    }

    private var lastBedtimeFiredDate = ""

    private fun checkBedtimeReminders() {
        if (!_profile.value.settings.notifications.enabled || !_profile.value.settings.notifications.bedtimeReminders) return
        
        val schedule = _plan.value?.scheduleEvents ?: return
        val now = java.time.LocalTime.now()
        val today = LocalDate.now().dayOfWeek.value % 7 // 0=Sun, 1=Mon...
        val dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        schedule.filter { it.category == "sleep" && it.day == today }.forEach { ev ->
            try {
                val startParts = ev.start.split(":")
                val sleepTime = java.time.LocalTime.of(startParts[0].toInt(), startParts[1].toInt())
                val reminderTime = sleepTime.minusMinutes(15)
                
                // If current time is within the reminder minute and hasn't fired today
                if (now.hour == reminderTime.hour && now.minute == reminderTime.minute && lastBedtimeFiredDate != dateKey) {
                    pushNotification("🛌 Bedtime coming up", "\"${ev.title}\" starts in 15 minutes — start winding down.")
                    lastBedtimeFiredDate = dateKey
                    // This will also be caught by MainActivity to show a system notif
                }
            } catch (e: Exception) {}
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

    // --- Archive Management ---

    fun restoreBoard(boardId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map {
            if (it.id == boardId) it.copy(archived = false) else it
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun deleteBoardForever(boardId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.filter { it.id != boardId }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun restoreSection(sectionId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) section.copy(archived = false) else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun deleteSectionForever(boardId: String, sectionId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            if (board.id == boardId) {
                board.copy(sections = board.sections.filter { it.id != sectionId })
            } else board
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun restoreTask(taskId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(archived = false) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun deleteTaskForever(sectionId: String, taskId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) {
                    section.copy(tasks = section.tasks.filter { it.id != taskId })
                } else section
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
        // Aggressive Widget Update
        syncWidgetsInternal(lastApplicationContext) 
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

    fun updateSettings(newSettings: KairosSettings) {
        _profile.value = _profile.value.copy(settings = newSettings)
        updateProfileInternal(mapOf("settings" to newSettings))
        syncWidgetsInternal(lastApplicationContext) // Sync widgets on settings change
    }

    fun updateAvatar(url: String) {
        val currentSettings = _profile.value.settings
        updateSettings(currentSettings.copy(
            profile = currentSettings.profile.copy(avatarURL = url)
        ))
    }

    fun removeAvatar() {
        updateAvatar("")
    }

    fun uploadAvatar(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            isUploadingAvatar = true
            try {
                // Encode to Base64 (Compressed to stay under Firestore limit)
                val base64String = com.kairos.app.utils.ImageUtils.convertToBase64(context, uri)
                    ?: throw Exception("Could not process image")
                
                updateAvatar(base64String)
                settingsError = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Avatar encoding failed", e)
                settingsError = "Failed to process image: ${e.localizedMessage}"
            } finally {
                isUploadingAvatar = false
            }
        }
    }

    fun clearSettingsError() {
        settingsError = null
    }

    fun clearSecurityMessages() {
        securityError = null
        securitySuccess = null
    }

    fun updateEmail(password: String, newEmail: String) {
        viewModelScope.launch {
            try {
                authRepository.reauthenticate(password)
                authRepository.updateEmail(newEmail)
                securitySuccess = "Email updated successfully"
                securityError = null
            } catch (e: Exception) {
                securityError = e.localizedMessage ?: "Failed to update email"
            }
        }
    }

    fun updatePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                authRepository.reauthenticate(currentPassword)
                authRepository.updatePassword(newPassword)
                securitySuccess = "Password updated successfully"
                securityError = null
            } catch (e: Exception) {
                securityError = e.localizedMessage ?: "Failed to update password"
            }
        }
    }

    fun toggleAppearanceMode() {
        val currentSettings = _profile.value.settings
        val newMode = if (currentSettings.appearance.mode == "dark") "light" else "dark"
        updateSettings(currentSettings.copy(
            appearance = currentSettings.appearance.copy(mode = newMode)
        ))
    }

    fun syncAmbientAudio(context: Context) {
        val settings = _profile.value.settings.appearance
        val intent = Intent(context, com.kairos.app.utils.AmbientAudioService::class.java).apply {
            putExtra("sound", settings.ambientSound)
            putExtra("volume", settings.ambientVolume)
        }
        if (settings.ambientSound == "none") {
            context.stopService(intent)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun syncWidgets(context: Context) {
        lastApplicationContext = context.applicationContext
        syncWidgetsInternal(context)
    }

    private fun syncWidgetsInternal(context: Context? = null) {
        val ctx = context ?: lastApplicationContext
        if (ctx == null) {
            Log.w("MainViewModel", "Cannot sync widgets: Context is null")
            return
        }
        
        viewModelScope.launch {
            WidgetManager.updateFocusState(ctx, focusTimerRunning, focusSecondsActive)
            val topTasks = _plan.value?.boards
                ?.filter { !it.archived }
                ?.flatMap { it.sections }
                ?.filter { !it.archived }
                ?.flatMap { it.tasks }
                ?.filter { !it.completed && !it.archived }
                ?.distinctBy { it.id }
                ?.take(3)
                ?.map { "${it.id}:${it.title}" } ?: emptyList()
            WidgetManager.updateTasks(ctx, topTasks)
        }
    }

    fun handleWidgetAction(action: String?, taskId: String?, context: Context) {
        lastApplicationContext = context.applicationContext
        if (action == null) return
        
        if (_plan.value == null) {
            pendingWidgetAction = action
            pendingWidgetTaskId = taskId
        } else {
            executeAction(action, taskId, context)
        }
    }

    private fun processPendingActions() {
        val action = pendingWidgetAction ?: return
        val taskId = pendingWidgetTaskId
        val context = lastApplicationContext ?: return
        
        executeAction(action, taskId, context)
        pendingWidgetAction = null
        pendingWidgetTaskId = null
    }

    private fun executeAction(action: String, taskId: String?, context: Context) {
        when (action) {
            "toggle_focus" -> {
                if (focusTimerRunning) pauseFocusTimer() else startFocusTimer()
            }
            "complete_task" -> {
                if (taskId != null) toggleTask(taskId, true)
            }
        }
        syncWidgetsInternal(context)
    }

    fun shareBoard(board: KairosBoard, description: String, category: String) {
        val userId = _user.value?.uid ?: return
        val profile = _profile.value
        
        val sharedRoutine = KairosSharedRoutine(
            id = "shared-${System.currentTimeMillis()}",
            creatorId = userId,
            creatorName = profile.settings.profile.displayName.ifBlank { "Anonymous" },
            creatorAvatar = profile.settings.profile.avatarURL,
            title = board.title,
            description = description,
            category = category,
            boards = listOf(board),
            createdAt = com.google.firebase.Timestamp.now()
        )

        viewModelScope.launch {
            try {
                firebaseRepository.shareRoutine(sharedRoutine)
                pushNotification("🚀 Routine Published!", "Your routine '${board.title}' is now live in Discovery.")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to share routine", e)
                errorMessage = "Sharing failed: ${e.localizedMessage}"
            }
        }
    }

    fun importRoutine(routine: KairosSharedRoutine) {
        val currentPlan = _plan.value ?: return
        val newBoards = routine.boards.map { board ->
            board.copy(
                id = "board-${System.currentTimeMillis()}-${(0..1000).random()}",
                sections = board.sections.map { section ->
                    section.copy(
                        id = "sec-${System.currentTimeMillis()}-${(0..1000).random()}",
                        tasks = section.tasks.map { task ->
                            task.copy(
                                id = "task-${System.currentTimeMillis()}-${(0..1000).random()}",
                                completed = false // Imported tasks should be fresh
                            )
                        }
                    )
                }
            )
        }
        updatePlanInternal(currentPlan.copy(boards = currentPlan.boards + newBoards))
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

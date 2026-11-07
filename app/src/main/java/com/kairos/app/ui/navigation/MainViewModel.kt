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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
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
    private var isInitialSync = true
    private var planReady = false
    private var profileReady = false

    private var session = 0L
    private var planSaving by mutableStateOf(false)
    private var profileSaves by mutableStateOf(0)
    val isSaving: Boolean get() = planSaving || profileSaves > 0
    private data class FailedSave(val id: String, val message: String, val retry: () -> Unit)
    private var failedSaves by mutableStateOf<List<FailedSave>>(emptyList())
    val canRetrySave: Boolean get() = failedSaves.isNotEmpty()
    private val accountJobs = mutableSetOf<Job>()

    private fun owns(uid: String, token: Long) = session == token &&
        _user.value?.uid == uid && authRepository.currentUser?.uid == uid

    private fun resetAccountState() {
        session++
        accountJobs.forEach { it.cancel() }
        accountJobs.clear()
        pauseFocusTimer()
        focusSecondsActive = 0
        _plan.value = null
        _profile.value = KairosUserProfile()
        planReady = false
        profileReady = false
        isInitialSync = true
        newlyUnlockedAchievement = null
        pendingWidgetAction = null
        pendingWidgetTaskId = null
        lastBedtimeFiredDate = ""
        planSaving = false
        profileSaves = 0
        failedSaves = emptyList()
        errorMessage = null
        settingsError = null
        securityError = null
        securitySuccess = null
        isUploadingAvatar = false
    }


    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { firebaseUser ->
                resetAccountState()
                _user.value = firebaseUser
                val token = session
                val uid = firebaseUser?.uid
                isInitializing = uid != null
                lastApplicationContext?.let { ctx ->
                    WidgetManager.clearAccountState(ctx, uid) { session == token && authRepository.currentUser?.uid == uid }
                }
                if (uid != null) coroutineScope {
                    launch {
                        firebaseRepository.getLatestPlan(uid)
                            .catch { if (owns(uid, token)) { errorMessage = it.localizedMessage; planReady = false } }
                            .collect {
                                if (!owns(uid, token)) return@collect
                                _plan.value = it
                                planReady = true
                                checkInitialAchievementSync()
                                processPendingActions()
                            }
                    }
                    launch {
                        firebaseRepository.getUserProfile(uid)
                            .catch { if (owns(uid, token)) { errorMessage = it.localizedMessage; isInitializing = false } }
                            .collect {
                                if (!owns(uid, token)) return@collect
                                _profile.value = it ?: KairosUserProfile()
                                profileReady = true
                                isInitializing = false
                                checkInitialAchievementSync()
                            }
                    }
                    launch {
                        while (true) { checkBedtimeReminders(); delay(60000L) }
                    }
                }
            }
        }
    }

    private fun checkInitialAchievementSync() {
        if (planReady && profileReady && isInitialSync) {
            val token = session
            val uid = _user.value?.uid ?: return
            isInitialSync = false
            accountJobs.removeAll { it.isCompleted }
            accountJobs += viewModelScope.launch {
                delay(2000)
                if (!owns(uid, token)) return@launch

                // MANDATORY RE-CHECK: Force evaluation of all thresholds against current stats
                val currentProfile = _profile.value
                checkAchievements(currentProfile.achievements, currentProfile.focusData)
                checkBackgroundCompletions(_plan.value)

                Log.d("MainViewModel", "Initial Achievement Sync Complete")
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
        updatePlanInternal(currentPlan.copy(boards = updatedBoards)) {
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
                ), currentProfile)

                checkAchievements(updatedAchievements, updatedFocusData)
            }
        }
    }


    private fun checkBackgroundCompletions(plan: KairosPlan?) {
        if (isInitialSync) return // Prevent popups during first data load

        val currentProfile = _profile.value
        val newlyCompletedTaskIds = mutableListOf<String>()

        plan?.boards?.flatMap { it.sections }?.flatMap { it.tasks }?.forEach { task ->
            if (task.completed && !currentProfile.achievements.countedTaskIds.contains(task.id)) {
                newlyCompletedTaskIds.add(task.id)
            }
        }

        if (newlyCompletedTaskIds.isNotEmpty()) {
            val updatedAchievements = currentProfile.achievements.copy(
                countedTaskIds = currentProfile.achievements.countedTaskIds + newlyCompletedTaskIds,
                lifetimeTasksCompleted = currentProfile.achievements.lifetimeTasksCompleted + newlyCompletedTaskIds.size
            )

            val dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val updatedFocusData = currentProfile.focusData.copy(
                dailyTasksLog = currentProfile.focusData.dailyTasksLog.toMutableMap().apply {
                    this[dateKey] = (this[dateKey] ?: 0) + newlyCompletedTaskIds.size
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
                    Log.d("MainViewModel", "Unlocking achievement: ${def.id}")
                    unlockAchievement(def)
                }
            }
        }
    }

    private fun unlockAchievement(def: AchievementDef) {
        val userId = _user.value?.uid ?: return
        val currentProfile = _profile.value
        val updatedUnlocked = currentProfile.achievements.unlocked.toMutableMap()
        updatedUnlocked[def.id] = System.currentTimeMillis()


        if (!isInitialSync) {
            newlyUnlockedAchievement = def
            pushNotification("🎉 Achievement Unlocked!", "You earned the ${def.name} badge.")
        }

        updateProfileInternal(mapOf("achievements.unlocked" to updatedUnlocked))
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
        val token = session
        accountJobs.removeAll { it.isCompleted }
        accountJobs += viewModelScope.launch {
            if (owns(userId, token)) _notificationEvents.emit(title to message)
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

    private fun updatePlanInternal(updatedPlan: KairosPlan, onSaved: () -> Unit = {}) {
        val uid = _user.value?.uid ?: return
        val token = session
        if (!owns(uid, token) || !planReady) { errorMessage = "Wait for your planner to load before editing."; return }
        if (planSaving) { errorMessage = "Wait for your current planner change to finish saving."; return }
        val before = _plan.value ?: KairosPlan(userID = uid)
        savePlan(uid, token, before, updatedPlan, onSaved)
    }

    private fun savePlan(uid: String, token: Long, before: KairosPlan, after: KairosPlan, onSaved: () -> Unit, operationId: String = java.util.UUID.randomUUID().toString()) {
        if (!owns(uid, token) || planSaving) return
        planSaving = true
        accountJobs.removeAll { it.isCompleted }
        accountJobs += viewModelScope.launch {
            try {
                firebaseRepository.updatePlan(uid, before, after)
                if (owns(uid, token)) {
                    failedSaves = failedSaves.filterNot { it.id == operationId }
                    errorMessage = failedSaves.firstOrNull()?.message
                    onSaved()
                    syncWidgetsInternal(lastApplicationContext)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (owns(uid, token)) {
                    errorMessage = "Change not saved: ${e.localizedMessage}. Your edit is retained for retry."
                    failedSaves = failedSaves.filterNot { it.id == operationId } + FailedSave(operationId, errorMessage.orEmpty()) {
                        savePlan(uid, token, before, after, onSaved, operationId)
                    }
                }
            } finally { if (owns(uid, token)) planSaving = false }
        }
    }

    private fun updateProfileInternal(data: Map<String, Any>, expectedProfile: KairosUserProfile = _profile.value) {
        val uid = _user.value?.uid ?: return
        val token = session
        if (!owns(uid, token) || !profileReady) { errorMessage = "Wait for your profile to load before editing."; return }
        saveProfile(uid, token, expectedProfile, data)
    }

    private fun saveProfile(uid: String, token: Long, before: KairosUserProfile, data: Map<String, Any>, operationId: String = java.util.UUID.randomUUID().toString()) {
        if (!owns(uid, token)) return
        profileSaves++
        accountJobs.removeAll { it.isCompleted }
        accountJobs += viewModelScope.launch {
            try {
                firebaseRepository.updateUserProfile(uid, before, data)
                if (owns(uid, token)) {
                    failedSaves = failedSaves.filterNot { it.id == operationId }
                    errorMessage = failedSaves.firstOrNull()?.message
                    syncWidgetsInternal(lastApplicationContext)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (owns(uid, token)) {
                    errorMessage = "Change not saved: ${e.localizedMessage}"
                    failedSaves = failedSaves.filterNot { it.id == operationId } + FailedSave(operationId, errorMessage.orEmpty()) {
                        saveProfile(uid, token, before, data, operationId)
                    }
                }
            } finally { if (owns(uid, token)) profileSaves-- }
        }
    }

    fun retryFailedSave() { if (!isSaving) failedSaves.firstOrNull()?.retry?.invoke() }

    fun updateDayInsight(dateKey: String, insight: String) {
        val currentPlan = _plan.value ?: return
        val updatedInsights = currentPlan.dayInsights.toMutableMap()
        updatedInsights[dateKey] = insight
        updatePlanInternal(currentPlan.copy(dayInsights = updatedInsights))
    }

    fun updateSettings(newSettings: KairosSettings, expectedSettings: KairosSettings = _profile.value.settings) {
        val uid = _user.value?.uid ?: return
        val token = session
        if (!owns(uid, token) || !profileReady) { errorMessage = "Wait for your profile to load before editing."; return }
        val before = _profile.value.copy(settings = expectedSettings)
        saveProfile(uid, token, before, mapOf("settings" to newSettings))
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
        val uid = _user.value?.uid ?: return
        val token = session
        accountJobs.removeAll { it.isCompleted }
        accountJobs += viewModelScope.launch {
            isUploadingAvatar = true
            try {
                // Encode to Base64 (Compressed to stay under Firestore limit)
                val base64String = com.kairos.app.utils.ImageUtils.convertToBase64(context, uri)
                    ?: throw Exception("Could not process image")

                if (!owns(uid, token)) return@launch
                updateAvatar(base64String)
                settingsError = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Avatar encoding failed", e)
                if (owns(uid, token)) settingsError = "Failed to process image: ${e.localizedMessage}"
            } finally {
                if (owns(uid, token)) isUploadingAvatar = false
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
        val uid = _user.value?.uid ?: return
        val token = session
        accountJobs.removeAll { it.isCompleted }
        accountJobs += viewModelScope.launch {
            try {
                if (!owns(uid, token)) return@launch
                authRepository.updateEmail(uid, password, newEmail)
                if (owns(uid, token)) {
                    securitySuccess = "Check your new email to confirm the change."
                    securityError = null
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (owns(uid, token)) securityError = e.localizedMessage ?: "Failed to update email" }
        }
    }

    fun updatePassword(currentPassword: String, newPassword: String) {
        val uid = _user.value?.uid ?: return
        val token = session
        accountJobs.removeAll { it.isCompleted }
        accountJobs += viewModelScope.launch {
            try {
                if (!owns(uid, token)) return@launch
                authRepository.updatePassword(uid, currentPassword, newPassword)
                if (owns(uid, token)) {
                    securitySuccess = "Password updated successfully"
                    securityError = null
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (owns(uid, token)) securityError = e.localizedMessage ?: "Failed to update password" }
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

        val uid = _user.value?.uid
        val token = session
        val appearance = _profile.value.settings.appearance
        val running = focusTimerRunning
        val seconds = focusSecondsActive
        val currentPlan = _plan.value
        viewModelScope.launch {
            if (session != token || authRepository.currentUser?.uid != uid) return@launch
            val isDark = appearance.mode == "dark"
            val accentHex = when (appearance.theme) {
                "fairyfloss" -> "#FFFF8FC9"
                "poseidon" -> "#FF38BDF8"
                "peacefulplains" -> "#FF4ADE80"
                "blue" -> "#FF3373F2"
                "green" -> "#FF2E9E66"
                "orange" -> "#FFF26B29"
                "pink" -> "#FFE64094"
                "red" -> "#FFEB3D47"
                else -> "#FF7D45F5"
            }
            WidgetManager.updateTheme(ctx, uid, accentHex, isDark) { session == token && authRepository.currentUser?.uid == uid }

            WidgetManager.updateFocusState(ctx, uid, running, seconds) { session == token && authRepository.currentUser?.uid == uid }
            val topTasks = currentPlan?.boards
                ?.filter { !it.archived }
                ?.flatMap { it.sections }
                ?.filter { !it.archived }
                ?.flatMap { it.tasks }
                ?.filter { !it.completed && !it.archived }
                ?.distinctBy { it.id }
                ?.take(3)
                ?.map { "${it.id}:${it.title}" } ?: emptyList()
            WidgetManager.updateTasks(ctx, uid, topTasks) { session == token && authRepository.currentUser?.uid == uid }
        }
    }

    fun handleWidgetAction(action: String?, taskId: String?, context: Context, ownerUid: String? = null) {
        lastApplicationContext = context.applicationContext
        if (action == null || ownerUid == null || ownerUid != _user.value?.uid || ownerUid != authRepository.currentUser?.uid) return

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

    fun signOut() {
        resetAccountState()
        _user.value = null
        authRepository.signOut()
        syncWidgetsInternal(lastApplicationContext)
    }

    fun deleteAccount() {
        val userId = _user.value?.uid ?: return
        viewModelScope.launch {
            try {
                // In a production app, we would delete all user data here first
                // firebaseRepository.deleteAllUserData(userId)
                // authRepository.deleteUser()
                signOut()
            } catch (e: Exception) {
                errorMessage = "Account deletion failed: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        errorMessage = null
        failedSaves = emptyList()
    }

    fun completeOnboarding(displayName: String, birthday: String, country: String, language: String, timezone: String) {
        val userId = _user.value?.uid ?: return
        val currentProfile = _profile.value
        val newSettings = currentProfile.settings.copy(
            profile = currentProfile.settings.profile.copy(
                displayName = displayName,
                birthday = birthday,
                country = country,
                timezone = timezone
            ),
            accessibility = currentProfile.settings.accessibility.copy(
                language = language
            )
        )

        updateProfileInternal(mapOf(
            "settings" to newSettings,
            "onboardingCompleted" to true
        ))
    }

    fun setGoal(index: Int, achievementId: String?) {
        val userId = _user.value?.uid ?: return
        val currentProfile = _profile.value
        val achievements = currentProfile.achievements

        // Robust Goal List Management
        val currentGoals = achievements.goals.toMutableList()
        while (currentGoals.size <= index) {
            currentGoals.add(null)
        }
        currentGoals[index] = achievementId

        updateProfileInternal(mapOf("achievements.goals" to currentGoals))
    }
}

package com.kairos.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class KairosTask(
    val id: String = "",
    val title: String = "",
    var completed: Boolean = false,
    val date: String? = null,
    val archived: Boolean = false
)

@IgnoreExtraProperties
data class KairosSection(
    val id: String = "",
    val title: String = "",
    val tasks: List<KairosTask> = emptyList(),
    val archived: Boolean = false
)

@IgnoreExtraProperties
data class KairosBoard(
    val id: String = "",
    val title: String = "",
    val sections: List<KairosSection> = emptyList(),
    val archived: Boolean = false
)

@IgnoreExtraProperties
data class KairosScheduleEvent(
    val id: String = "",
    val title: String = "",
    val category: String = "other",
    val day: Int = 0,
    val start: String = "",
    val end: String = ""
)

@IgnoreExtraProperties
data class KairosPlan(
    val boards: List<KairosBoard> = emptyList(),
    val dayInsights: Map<String, String> = emptyMap(),
    val scheduleEvents: List<KairosScheduleEvent> = emptyList(),
    val userID: String = "",
    val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class KairosAchievementsData(
    val unlocked: Map<String, Long> = emptyMap(), // id to timestamp
    val countedTaskIds: List<String> = emptyList(),
    val lifetimeTasksCompleted: Int = 0,
    val goals: List<String?> = listOf(null, null, null)
)

@IgnoreExtraProperties
data class KairosFocusData(
    val totalSeconds: Long = 0,
    val longestSessionSeconds: Long = 0,
    val dailyFocusLog: Map<String, Long> = emptyMap(),
    val dailyTasksLog: Map<String, Int> = emptyMap()
)

@IgnoreExtraProperties
data class ProfileSettings(
    val displayName: String = "",
    val avatarURL: String = "",
    val birthday: String = "",
    val timezone: String = "UTC"
)

@IgnoreExtraProperties
data class AccessibilitySettings(
    val density: String = "default", // compact, default, spacious
    val timeFormat: String = "12", // 12, 24
    val reduceMotion: Boolean = false,
    val language: String = "en"
)

@IgnoreExtraProperties
data class AppearanceSettings(
    val mode: String = "dark", // dark, light
    val theme: String = "default", // default, fairyfloss, poseidon, peacefulplains
    val textColor: String = "default",
    val font: String = "sans", // sans, round, mono
    val background: String = "none",
    val customBackground: String? = null,
    val cursor: String = "default",
    val ambientSound: String = "none",
    val ambientVolume: Int = 35,
    val customAmbientYoutubeUrl: String = "",
    val confetti: Boolean = true
)

@IgnoreExtraProperties
data class NotificationSettings(
    val enabled: Boolean = true,
    val boardCompletion: Boolean = true,
    val bedtimeReminders: Boolean = true,
    val browserPush: Boolean = false
)

@IgnoreExtraProperties
data class KairosSettings(
    val profile: ProfileSettings = ProfileSettings(),
    val accessibility: AccessibilitySettings = AccessibilitySettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val notifications: NotificationSettings = NotificationSettings()
)

@IgnoreExtraProperties
data class KairosNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val time: Long = 0,
    val read: Boolean = false
)

@IgnoreExtraProperties
data class KairosUserProfile(
    val settings: KairosSettings = KairosSettings(),
    val notifications: List<KairosNotification> = emptyList(),
    val achievements: KairosAchievementsData = KairosAchievementsData(),
    val focusData: KairosFocusData = KairosFocusData(),
    val aiChatHistory: List<ChatMessage> = emptyList()
)

@IgnoreExtraProperties
data class ChatMessage(
    val role: String = "", // user, assistant, system
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class KairosSharedRoutine(
    val id: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorAvatar: String = "",
    val title: String = "", // AI Generated "Power Name"
    val description: String = "",
    val category: String = "other", // deep work, student, etc.
    val boards: List<KairosBoard> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0,
    val tags: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)

data class AchievementDef(
    val id: String,
    val category: String,
    val name: String,
    val desc: String,
    val icon: String,
    val type: String, // 'focus_seconds', 'tasks_completed', 'event'
    val threshold: Long = 0,
    val event: String? = null,
    val limitedAvailability: Boolean = false
)

val KAIROS_ACHIEVEMENTS = listOf(
    // Focus time achievements
    AchievementDef("focus_25m", "focus", "Novice", "Log 25 minutes of focus time", "🔥", "focus_seconds", 25 * 60L),
    AchievementDef("focus_1h", "focus", "Apprentice", "Log 1 hour of focus time", "⚡", "focus_seconds", 3600L),
    AchievementDef("focus_2h", "focus", "Adept", "Log 2 hours of focus time", "🌀", "focus_seconds", 2 * 3600L),
    AchievementDef("focus_5h", "focus", "Specialist", "Log 5 hours of focus time", "🎯", "focus_seconds", 5 * 3600L),
    AchievementDef("focus_10h", "focus", "Expert", "Log 10 hours of focus time", "🛡️", "focus_seconds", 10 * 3600L),
    AchievementDef("focus_20h", "focus", "Veteran", "Log 20 hours of focus time", "🏅", "focus_seconds", 20 * 3600L),
    AchievementDef("focus_50h", "focus", "Master", "Log 50 hours of focus time", "👑", "focus_seconds", 50 * 3600L),
    AchievementDef("focus_100h", "focus", "Grandmaster", "Log 100 hours of focus time", "💎", "focus_seconds", 100 * 3600L),
    AchievementDef("focus_300h", "focus", "Legend", "Log 300 hours of focus time", "⭐", "focus_seconds", 300 * 3600L),
    AchievementDef("focus_500h", "focus", "Mythic", "Log 500 hours of focus time", "🌟", "focus_seconds", 500 * 3600L),
    AchievementDef("focus_1000h", "focus", "DEVELOPER???", "Log 1000 hours of focus time", "🧠", "focus_seconds", 1000 * 3600L),
    
    // Tasks achievements
    AchievementDef("tasks_5", "tasks", "Getting Started", "Complete 5 tasks", "📝", "tasks_completed", 5),
    AchievementDef("tasks_15", "tasks", "Warming Up", "Complete 15 tasks", "📋", "tasks_completed", 15),
    AchievementDef("tasks_30", "tasks", "Task Tackler", "Complete 30 tasks", "✅", "tasks_completed", 30),
    AchievementDef("tasks_50", "tasks", "On A Roll", "Complete 50 tasks", "🎲", "tasks_completed", 50),
    AchievementDef("tasks_100", "tasks", "Centurion of Checkboxes", "Complete 100 tasks", "🏆", "tasks_completed", 100),
    AchievementDef("tasks_200", "tasks", "Double Century", "Complete 200 tasks", "🎖️", "tasks_completed", 200),
    AchievementDef("tasks_500", "tasks", "Half-K Hero", "Complete 500 tasks", "🚀", "tasks_completed", 500),
    AchievementDef("tasks_800", "tasks", "Almost There...", "Complete 800 tasks", "🔟", "tasks_completed", 800),
    AchievementDef("tasks_1000", "tasks", "Kilo-Tasker", "Complete 1,000 tasks", "🗻", "tasks_completed", 1000),
    AchievementDef("tasks_20000", "tasks", "Task Titan", "Complete 20,000 tasks", "🗿", "tasks_completed", 20000),
    AchievementDef("tasks_50000", "tasks", "CHECKLIST MASTER", "Complete 50,000 tasks", "👑", "tasks_completed", 50000),
    
    // Milestones
    AchievementDef("misc_welcome", "misc", "Welcome to Kairos", "Join Kairos", "👋", "event", event = "signup"),
    AchievementDef("misc_og", "misc", "OG", "One of the original Kairos users", "🥇", "event", event = "og", limitedAvailability = true),
    AchievementDef("misc_lofi", "misc", "LOFIIII", "Turn on the Lo-fi ambient sound", "🎧", "event", event = "lofi"),
    AchievementDef("misc_light", "misc", "Come To the Light", "Switch to Light mode", "☀️", "event", event = "light_mode"),
    AchievementDef("misc_busy", "misc", "Real Busy", "Schedule more than 10 tasks on a single day", "📅", "event", event = "busy_day")
)

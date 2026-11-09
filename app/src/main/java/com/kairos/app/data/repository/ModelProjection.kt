package com.kairos.app.data.repository

import com.kairos.app.data.models.*

/** Known editable fields only. Snapshot identity and raw source fields are never serialized. */
fun KairosPlan.sharedFields(): Map<String, Any?> = mapOf(
    "boards" to boards.map { it.fields() },
    "dayInsights" to dayInsights.toMap(),
    "scheduleEvents" to scheduleEvents.map { it.fields() }
)

fun KairosUserProfile.sharedFields(): Map<String, Any?> = mapOf(
    "settings" to settings.fields(),
    "notifications" to notifications.map { it.fields() },
    "achievements" to achievements.fields(),
    "focusData" to focusData.fields(),
    "aiChatHistory" to aiChatHistory.map { it.fields() },
    "onboardingCompleted" to onboardingCompleted
)

/**
 * Applies the existing profile update API to a complete baseline projection, without mutating it.
 * Map values replace their addressed field; dot paths address individual nested fields.
 * The result is an edited projection for [SharedDataMerge], never a replacement document.
 */
fun profilePatch(before: KairosUserProfile, data: Map<String, Any>): Map<String, Any?> {
    var result = before.sharedFields()
    val paths = data.keys.map { key ->
        val path = key.split('.')
        require(path.none { it.isBlank() } && path.first() in result) { "Unsupported profile field: $key" }
        require(path.none { it == "sourceFields" || it == "documentId" }) { "Snapshot metadata is not editable" }
        path
    }
    for (left in paths.indices) {
        for (right in left + 1 until paths.size) {
            val a = paths[left]
            val b = paths[right]
            require(a.take(b.size) != b && b.take(a.size) != a) { "Overlapping profile update paths" }
        }
    }
    data.entries.forEachIndexed { index, entry ->
        result = replaceField(result, paths[index], profileValue(entry.value))
    }
    return result
}

private fun replaceField(source: Map<String, Any?>, path: List<String>, value: Any?): Map<String, Any?> {
    val key = path.first()
    val result = source.toMutableMap()
    if (path.size == 1) {
        result[key] = value
    } else {
        val nested = source[key]
        require(nested is Map<*, *>) { "Profile update path does not address a map: ${path.joinToString(".")}" }
        result[key] = replaceField(stringFields(nested), path.drop(1), value)
    }
    return result
}

/** Explicit conversion keeps Firestore's POJO serializer out of the shared write boundary. */
private fun profileValue(value: Any?): Any? = when (value) {
    null, is String, is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> value
    is KairosSettings -> value.fields()
    is ProfileSettings -> value.fields()
    is AccessibilitySettings -> value.fields()
    is AppearanceSettings -> value.fields()
    is NotificationSettings -> value.fields()
    is KairosAchievementsData -> value.fields()
    is KairosFocusData -> value.fields()
    is KairosNotification -> value.fields()
    is ChatMessage -> value.fields()
    is List<*> -> value.map(::profileValue)
    is Map<*, *> -> stringFields(value).mapValues { profileValue(it.value) }
    else -> throw IllegalArgumentException("Unsupported profile update value")
}

private fun stringFields(value: Map<*, *>): Map<String, Any?> {
    require(value.keys.all { it is String }) { "Profile maps require string keys" }
    return value.entries.associate { (key, item) -> key as String to item }
}

private fun KairosTask.fields(): Map<String, Any?> = mapOf(
    "id" to id, "title" to title, "completed" to completed, "date" to date, "archived" to archived,
    "taskDescription" to taskDescription, "notes" to notes, "priority" to priority,
    "estimatedMinutes" to estimatedMinutes,
    "checklist" to checklist?.map { mapOf("id" to it.id, "title" to it.title, "completed" to it.completed) },
    "startTime" to startTime, "endTime" to endTime, "dueDate" to dueDate, "dueTime" to dueTime,
    "reminderMinutes" to reminderMinutes, "scheduleLocked" to scheduleLocked,
    "schedulingPreference" to schedulingPreference, "parentTaskID" to parentTaskID
)

private fun KairosSection.fields(): Map<String, Any?> = mapOf(
    "id" to id, "title" to title, "tasks" to tasks.map { it.fields() }, "archived" to archived
)

private fun KairosBoard.fields(): Map<String, Any?> = mapOf(
    "id" to id, "title" to title, "sections" to sections.map { it.fields() }, "archived" to archived
)

private fun KairosScheduleEvent.fields(): Map<String, Any?> = mapOf(
    "id" to id, "title" to title, "category" to category, "day" to day, "start" to start, "end" to end
)

private fun KairosAchievementsData.fields(): Map<String, Any?> = mapOf(
    "unlocked" to unlocked.toMap(), "countedTaskIds" to countedTaskIds.toList(),
    "lifetimeTasksCompleted" to lifetimeTasksCompleted, "goals" to goals.toList()
)

private fun KairosFocusData.fields(): Map<String, Any?> = mapOf(
    "totalSeconds" to totalSeconds, "longestSessionSeconds" to longestSessionSeconds,
    "dailyFocusLog" to dailyFocusLog.toMap(), "dailyTasksLog" to dailyTasksLog.toMap()
)

private fun ProfileSettings.fields(): Map<String, Any?> = mapOf(
    "displayName" to displayName, "avatarURL" to avatarURL, "birthday" to birthday,
    "timezone" to timezone, "country" to country
)

private fun AccessibilitySettings.fields(): Map<String, Any?> = mapOf(
    "density" to density, "timeFormat" to timeFormat, "reduceMotion" to reduceMotion, "language" to language
)

private fun AppearanceSettings.fields(): Map<String, Any?> = mapOf(
    "mode" to mode, "theme" to theme, "textColor" to textColor, "font" to font,
    "background" to background, "customBackground" to customBackground, "cursor" to cursor,
    "ambientSound" to ambientSound, "ambientVolume" to ambientVolume,
    "customAmbientYoutubeUrl" to customAmbientYoutubeUrl, "confetti" to confetti,
    "timerType" to timerType, "timerTheme" to timerTheme, "timerBorder" to timerBorder
)

private fun NotificationSettings.fields(): Map<String, Any?> = mapOf(
    "enabled" to enabled, "boardCompletion" to boardCompletion,
    "bedtimeReminders" to bedtimeReminders, "browserPush" to browserPush
)

private fun KairosSettings.fields(): Map<String, Any?> = mapOf(
    "profile" to profile.fields(), "accessibility" to accessibility.fields(),
    "appearance" to appearance.fields(), "notifications" to notifications.fields()
)

private fun KairosNotification.fields(): Map<String, Any?> = mapOf(
    "id" to id, "title" to title, "message" to message, "time" to time, "read" to read
)

private fun ChatMessage.fields(): Map<String, Any?> = mapOf(
    "role" to role, "content" to content, "timestamp" to timestamp
)

package com.kairos.app.utils

import com.kairos.app.data.models.KairosPlan
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

internal data class PlannedTaskReminder(val taskId: String, val title: String, val atMillis: Long) {
    fun fingerprint(ownerUid: String): String = reminderFingerprint(listOf(ownerUid, taskId, title, atMillis.toString()))
}

internal fun reminderFingerprint(parts: List<String>): String {
    // Length prefixes keep different combinations of IDs/titles unambiguous.
    val value = parts.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal object TaskReminderPlanner {
    fun plan(plan: KairosPlan?, timezone: String, nowMillis: Long): List<PlannedTaskReminder> {
        val zone = try { ZoneId.of(timezone) } catch (_: DateTimeException) { ZoneOffset.UTC }
        val tasks = plan?.boards.orEmpty().filterNot { it.archived }
            .flatMap { it.sections }.filterNot { it.archived }.flatMap { it.tasks }
        // Ambiguous duplicate IDs cannot safely identify an individual alarm.
        val uniqueIds = tasks.groupingBy { it.id }.eachCount().filterValues { it == 1 }.keys
        return tasks.mapNotNull { task ->
            val minutes = task.reminderMinutes ?: return@mapNotNull null
            if (task.id.isBlank() || task.id !in uniqueIds || task.completed || task.archived ||
                task.recurrence?.enabled == true || minutes <= 0) return@mapNotNull null
            val date = task.date ?: return@mapNotNull null
            val time = task.startTime ?: return@mapNotNull null
            if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ||
                !time.matches(Regex("\\d{2}:\\d{2}"))) return@mapNotNull null
            val at = try {
                // java.time shifts a DST gap forward and chooses the earlier offset in an overlap.
                LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone)
                    .toInstant().toEpochMilli() - minutes.toLong() * 60_000L
            } catch (_: DateTimeException) { return@mapNotNull null }
            if (at <= nowMillis) null else PlannedTaskReminder(task.id, task.title, at)
        }.sortedBy { it.taskId }
    }
}

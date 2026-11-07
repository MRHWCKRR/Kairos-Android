package com.kairos.app.utils

import com.kairos.app.data.models.KairosBoard
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.models.KairosRecurrenceMarker
import com.kairos.app.data.models.KairosSection
import com.kairos.app.data.models.KairosTask
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TaskReminderPlannerTest {
    private val task = KairosTask(id = "task", title = "Prepare", date = "2030-06-02", startTime = "10:00", reminderMinutes = 15)
    private fun plan(vararg tasks: KairosTask, boardArchived: Boolean = false, sectionArchived: Boolean = false) =
        KairosPlan(boards = listOf(KairosBoard(archived = boardArchived, sections = listOf(KairosSection(archived = sectionArchived, tasks = tasks.toList())))))
    private fun reminders(vararg tasks: KairosTask, timezone: String = "UTC", now: Long = 0L) =
        TaskReminderPlanner.plan(plan(*tasks), timezone, now)

    @Test fun usesScheduledStartAndIgnoresIndependentDeadline() {
        val result = reminders(task.copy(dueDate = "2020-01-01", dueTime = "01:00", endTime = "11:00")).single()
        assertEquals(Instant.parse("2030-06-02T09:45:00Z").toEpochMilli(), result.atMillis)
        assertTrue(reminders(task.copy(date = null, dueDate = "2030-06-02", dueTime = "10:00")).isEmpty())
    }

    @Test fun skipsCompletedArchivedRecurringAndAmbiguousIds() {
        assertTrue(reminders(task.copy(completed = true), task.copy(id = "archived", archived = true),
            task.copy(id = "recurring", recurrence = KairosRecurrenceMarker(enabled = true))).isEmpty())
        assertTrue(reminders(task, task.copy(title = "Duplicate")).isEmpty())
        assertTrue(TaskReminderPlanner.plan(plan(task, boardArchived = true), "UTC", 0L).isEmpty())
        assertTrue(TaskReminderPlanner.plan(plan(task, sectionArchived = true), "UTC", 0L).isEmpty())
        assertEquals(1, reminders(task.copy(recurrence = KairosRecurrenceMarker(enabled = false))).size)
    }

    @Test fun skipsMalformedMissingAndPastSchedules() {
        listOf(task.copy(date = "2030-02-30"), task.copy(date = "2030-6-02"), task.copy(startTime = "24:00"),
            task.copy(startTime = "10:00:00"), task.copy(startTime = null), task.copy(reminderMinutes = 0),
            task.copy(reminderMinutes = -1), task.copy(reminderMinutes = null)).forEach { assertTrue(reminders(it).isEmpty()) }
        val at = reminders(task).single().atMillis
        assertTrue(reminders(task, now = at).isEmpty())
        assertTrue(reminders(task, now = at + 1L).isEmpty())
        assertEquals(1, reminders(task, now = at - 1L).size)
    }

    @Test fun invalidTimezoneFallsBackToUtc() {
        assertEquals(reminders(task), reminders(task, timezone = "bad/timezone"))
        assertEquals(Instant.parse("2030-06-02T13:45:00Z").toEpochMilli(),
            reminders(task, timezone = "America/New_York").single().atMillis)
    }

    @Test fun daylightSavingGapShiftsForwardAndOverlapUsesEarlierOffset() {
        val gap = task.copy(date = "2030-03-10", startTime = "02:30")
        assertEquals(Instant.parse("2030-03-10T07:15:00Z").toEpochMilli(), reminders(gap, timezone = "America/New_York").single().atMillis)
        val overlap = task.copy(date = "2030-11-03", startTime = "01:30")
        assertEquals(Instant.parse("2030-11-03T05:15:00Z").toEpochMilli(), reminders(overlap, timezone = "America/New_York").single().atMillis)
    }

    @Test fun fingerprintIncludesOwnerTaskTitleAndTimeWithoutDelimiterCollisions() {
        val reminder = reminders(task).single()
        assertNotEquals(reminder.fingerprint("a"), reminder.fingerprint("b"))
        assertNotEquals(reminder.fingerprint("a"), reminder.copy(title = "Renamed").fingerprint("a"))
        assertNotEquals(reminderFingerprint(listOf("a", "bc")), reminderFingerprint(listOf("ab", "c")))
    }
}

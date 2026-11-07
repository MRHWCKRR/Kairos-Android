package com.kairos.app.data.repository

import com.kairos.app.data.models.*
import org.junit.Assert.*
import org.junit.Test

class TaskEditContractTest {
    private val task = KairosTask(id = "t", title = "Read")
    private fun plan(value: KairosTask = task) = KairosPlan(boards = listOf(KairosBoard(id = "b", sections = listOf(KairosSection(id = "s", tasks = listOf(value))))))
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: SharedDataConflict) {}
    }
    private fun guard(before: KairosTask = task, after: KairosTask, fresh: KairosTask = before) {
        val base = plan(before).sharedFields()
        val edited = plan(after).sharedFields()
        val raw = rawPlan(before)
        val remote = rawPlan(fresh)
        TaskEditContract.validateMutation(base, edited, raw, remote, SharedDataMerge.merge(base, edited, remote, raw))
    }
    @Suppress("UNCHECKED_CAST")
    private fun rawPlan(value: KairosTask): Map<String, Any?> {
        val projected = plan(value).sharedFields()
        val boards = projected["boards"] as List<Map<String, Any?>>
        val sections = boards.single()["sections"] as List<Map<String, Any?>>
        val tasks = sections.single()["tasks"] as List<Map<String, Any?>>
        return projected + ("boards" to listOf(boards.single() + ("sections" to listOf(sections.single() + ("tasks" to listOf(tasks.single() +
            mapOf("recurrence" to mapOf("enabled" to (value.recurrence?.enabled == true), "future" to "preserve"))))))))
    }
    @Test fun changesOnlySelectedTaskAndPreservesUntouchedAbsentChecklist() {
        val before = plan().copy(sourceFields = mapOf("future" to true))
        val result = TaskEditContract.editedPlan(before, "t", task.copy(title = "Edited", checklist = emptyList()))
        assertEquals(task.copy(title = "Edited"), result.boards.single().sections.single().tasks.single())
        assertEquals(before.sourceFields, result.sourceFields)
    }
    @Test fun unchangedUnknownOptionsAndMalformedLegacyDatesCanBeSaved() {
        val before = task.copy(priority = "urgent", schedulingPreference = "night", date = "legacy", estimatedMinutes = 0)
        TaskEditContract.validateTask(before, before.copy(title = "Edited"))
        rejected { TaskEditContract.validateTask(task, task.copy(priority = "urgent")) }
        rejected { TaskEditContract.validateTask(task, task.copy(schedulingPreference = "night")) }
    }
    @Test fun validatesRangesNotesAndStrictSchedule() {
        val bad = listOf(task.copy(title = "  "), task.copy(notes = "x".repeat(3001)), task.copy(notes = "😀".repeat(1501)),
            task.copy(estimatedMinutes = 0), task.copy(estimatedMinutes = 1441), task.copy(reminderMinutes = 0),
            task.copy(reminderMinutes = 10081), task.copy(date = "2026-02-29"), task.copy(date = "2026-2-01"),
            task.copy(dueDate = "2026-04-31"), task.copy(dueTime = "24:00"), task.copy(startTime = "09:00"),
            task.copy(startTime = "10:00", endTime = "09:00"), task.copy(startTime = "10:00", endTime = "10:00"))
        bad.forEach { after -> rejected { TaskEditContract.validateTask(task, after) } }
        TaskEditContract.validateTask(task, task.copy(estimatedMinutes = 1440, reminderMinutes = 10080,
            notes = "😀".repeat(1500), date = "2028-02-29", startTime = "09:00", endTime = "10:00", dueTime = "23:59"))
    }
    @Test fun validatesChecklistIdentityAndLimits() {
        val valid = KairosChecklistItem("id", "Step")
        val invalid = listOf(listOf(valid.copy(id = " ")), listOf(valid.copy(id = "x".repeat(121))),
            listOf(valid.copy(title = " ")), listOf(valid.copy(title = "x".repeat(181))), listOf(valid, valid),
            (0..100).map { valid.copy(id = "$it") })
        invalid.forEach { entries -> rejected { TaskEditContract.validateTask(task, task.copy(checklist = entries)) } }
    }
    @Test fun staleLocksRequireOriginalExplicitUnlock() {
        rejected { guard(after = task.copy(date = "2026-11-08"), fresh = task.copy(scheduleLocked = true)) }
        rejected { guard(after = task.copy(date = "2026-11-08", scheduleLocked = false), fresh = task.copy(scheduleLocked = true)) }
        val locked = task.copy(scheduleLocked = true)
        guard(locked, locked.copy(scheduleLocked = false, date = "2026-11-08"))
        guard(after = task.copy(title = "Edited"), fresh = task.copy(scheduleLocked = true))
    }
    @Test fun remoteRecurrenceBlocksScheduleAndCompletionButAllowsNotes() {
        val recurring = task.copy(recurrence = KairosRecurrenceMarker(true))
        rejected { guard(after = task.copy(date = "2026-11-08"), fresh = recurring) }
        rejected { guard(after = task.copy(checklist = listOf(KairosChecklistItem("i", "Step", true))), fresh = recurring) }
        guard(after = task.copy(notes = "My notes"), fresh = recurring)
    }
    @Test fun remoteTaskAndParentsCannotBecomeArchivedOrMove() {
        rejected { guard(after = task.copy(notes = "Draft"), fresh = task.copy(archived = true)) }
        val base = plan().sharedFields()
        val edited = plan(task.copy(notes = "Draft")).sharedFields()
        val board = plan().boards.single()
        for (fresh in listOf(plan().copy(boards = listOf(board.copy(archived = true))),
            plan().copy(boards = listOf(board.copy(sections = listOf(board.sections.single().copy(archived = true))))),
            plan().copy(boards = listOf(board.copy(sections = listOf(board.sections.single().copy(id = "moved"))))))) {
            rejected { TaskEditContract.validateMutation(base, edited, base, fresh.sharedFields(), fresh.sharedFields()) }
        }
    }
    @Test fun concurrentChecklistAdditionsCannotExceedFinalLimit() {
        val before = task.copy(checklist = (0 until 99).map { KairosChecklistItem("i$it", "Step") })
        val after = before.copy(checklist = before.checklist!! + KairosChecklistItem("mine", "Mine"))
        val remote = before.copy(checklist = before.checklist!! + KairosChecklistItem("theirs", "Theirs"))
        rejected { guard(before, after, remote) }
    }
    @Test fun recurrenceIsReadOnlyAndOmittedFromProjection() {
        val recurrence = KairosRecurrenceMarker(true)
        assertFalse(plan(task.copy(recurrence = recurrence)).sharedFields().toString().contains("recurrence"))
        rejected { TaskEditContract.validateTask(task, task.copy(recurrence = recurrence)) }
    }
}

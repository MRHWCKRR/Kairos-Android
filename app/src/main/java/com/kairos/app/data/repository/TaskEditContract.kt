package com.kairos.app.data.repository

import com.kairos.app.data.models.*
import java.time.LocalDate

/** Rules shared by the editor and the transaction boundary, including retained draft replay. */
object TaskEditContract {
    private val scheduleFields = setOf("date", "startTime", "endTime")

    fun validateTask(before: KairosTask, after: KairosTask) {
        require(before.id == after.id) { "Task identity cannot change." }
        require(before.parentTaskID == after.parentTaskID) { "Reparenting is not supported in task details." }
        require(before.recurrence == after.recurrence) { "Use the repeat editor to change recurrence." }
        validateChanged(taskFields(before), taskFields(after))
        val scheduled = before.date != after.date || before.startTime != after.startTime || before.endTime != after.endTime
        require(!scheduled || before.recurrence?.enabled != true) { "Use the repeat editor to change a recurring schedule." }
        require(!scheduled || before.scheduleLocked != true || after.scheduleLocked == false) { "Unlock the schedule before changing its date or time." }
        require(before.recurrence?.enabled != true || !completionChanged(taskFields(before), taskFields(after))) {
            "Use the repeat editor to complete recurring checklist items."
        }
    }

    fun editedPlan(before: KairosPlan, taskId: String, edited: KairosTask): KairosPlan {
        val locations = before.boards.flatMap { board -> board.sections.flatMap { section ->
            section.tasks.filter { it.id == taskId }.map { Triple(board, section, it) }
        } }
        require(locations.size == 1) { "This task is unavailable or has an ambiguous identity." }
        val (board, section, task) = locations.single()
        require(before.boards.count { it.id == board.id } == 1 && board.sections.count { it.id == section.id } == 1 &&
            !board.archived && !section.archived && !task.archived) { "This task moved or became unavailable. Reopen its details." }
        validateTask(task, edited)
        // An untouched absent checklist remains absent; the editor presents it as an empty list.
        val replacement = if (task.checklist == null && edited.checklist?.isEmpty() == true) edited.copy(checklist = null) else edited
        return before.copy(boards = before.boards.map { b ->
            if (b.id != board.id) b else b.copy(sections = b.sections.map { s ->
                if (s.id != section.id) s else s.copy(tasks = s.tasks.map { if (it.id == taskId) replacement else it })
            })
        })
    }

    fun validateMutation(
        base: Map<String, Any?>,
        edited: Map<String, Any?>,
        rawBaseline: Map<String, Any?>,
        remote: Map<String, Any?>,
        merged: Map<String, Any?>
    ) {
        val old = locations(base)
        val desired = locations(edited)
        val original = locations(rawBaseline)
        val current = locations(remote)
        val result = locations(merged)
        for ((id, previous) in old) {
            val next = desired[id]
            if (next == previous) continue
            val fresh = current[id]
            // A retained delete may be replayed after the server committed it but its acknowledgement was lost.
            if (next == null && fresh == null && result[id] == null) continue
            if (fresh == null) conflict("This task was removed. Reopen its details.")
            if (next != null && (previous.board != next.board || previous.section != next.section)) {
                conflict("Task details cannot move a task between sections.")
            }
            if (fresh.board != previous.board || fresh.section != previous.section) conflict("This task moved. Reopen its details.")
            val baseline = original[id] ?: previous
            val finalLocation = result[id]
            val newlyArchived = (!baseline.archived && fresh.archived) ||
                (!baseline.boardArchived && fresh.boardArchived) || (!baseline.sectionArchived && fresh.sectionArchived)
            if (newlyArchived) {
                // Only an already-applied, explicitly intended archive can bypass fresh availability.
                // Pending details edits still fail when another device archives the task or a parent.
                val archiveMatchesIntent = next != null && finalLocation != null &&
                    next.archived == fresh.archived && finalLocation.archived == fresh.archived &&
                    next.boardArchived == fresh.boardArchived && finalLocation.boardArchived == fresh.boardArchived &&
                    next.sectionArchived == fresh.sectionArchived && finalLocation.sectionArchived == fresh.sectionArchived
                val taskChangesAlreadyApplied = next != null && (previous.task.keys + next.task.keys).all { key ->
                    previous.task[key] == next.task[key] || next.task[key] == fresh.task[key]
                }
                if (!archiveMatchesIntent || !taskChangesAlreadyApplied) {
                    conflict("This task or its section was archived. Reopen its details.")
                }
            }
            if (next == null) continue
            validateChanged(previous.task, next.task)
            val finalTask = result[id]?.task ?: conflict("This task became unavailable.")
            validateChanged(fresh.task, finalTask)
            val scheduled = scheduleFields.any { previous.task[it] != next.task[it] }
            val recurring = (fresh.task["recurrence"] as? Map<*, *>)?.get("enabled") == true
            val checklistChanged = previous.task["checklist"] != next.task["checklist"]
            if (recurring && (scheduled || completionChanged(previous.task, next.task) ||
                    (checklistChanged && maps(finalTask["checklist"]).any { it["completed"] == true }))) {
                conflict("This task is recurring. Use the repeat editor for schedule or checklist completion changes.")
            }
            val explicitUnlock = baseline.task["scheduleLocked"] == true && previous.task["scheduleLocked"] == true &&
                next.task["scheduleLocked"] == false && result[id]?.task?.get("scheduleLocked") == false
            if (scheduled && fresh.task["scheduleLocked"] == true && !explicitUnlock) {
                conflict("This schedule was locked. Reopen its details before changing it.")
            }
        }
        for ((id, next) in desired) if (id !in old) validateChanged(emptyMap(), next.task)
    }

    private fun validateChanged(before: Map<String, Any?>, after: Map<String, Any?>) {
        fun changed(key: String) = before[key] != after[key]
        if (changed("title")) require((after["title"] as? String)?.isNotBlank() == true) { "Enter a task title." }
        if (changed("notes")) require(after["notes"] == null || (after["notes"] as? String)?.length?.let { it <= 3000 } == true) { "Notes support up to 3000 characters." }
        fun bounded(key: String, range: IntRange, message: String) {
            if (!changed(key) || after[key] == null) return
            val n = after[key] as? Number
            require(n != null && n.toDouble() == n.toInt().toDouble() && n.toInt() in range) { message }
        }
        bounded("estimatedMinutes", 1..1440, "Enter an estimate from 1 to 1440 minutes.")
        bounded("reminderMinutes", 1..10080, "Enter reminder minutes from 1 to 10080.")
        for ((key, options) in mapOf("priority" to setOf("low", "medium", "high"), "schedulingPreference" to setOf("morning", "afternoon", "evening"))) {
            if (changed(key)) require(after[key] == null || (after[key] is String && options.contains(after[key] as String))) { "Choose a supported $key value." }
        }
        for (key in listOf("date", "dueDate")) if (changed(key) && after[key] != null) {
            val value = after[key] as? String
            require(value != null && value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { LocalDate.parse(value) }.isSuccess) { "Enter a valid date in YYYY-MM-DD format." }
        }
        for (key in listOf("startTime", "endTime", "dueTime")) if (changed(key) && after[key] != null) {
            require((after[key] as? String)?.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) == true) { "Enter a valid time in HH:mm format." }
        }
        if (changed("startTime") || changed("endTime")) {
            val start = after["startTime"] as? String
            val end = after["endTime"] as? String
            require((start == null && end == null) || (start != null && end != null && start < end &&
                start.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) && end.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")))) { "Choose both scheduled times, with end after start." }
        }
        if (before["checklist"] != after["checklist"]) {
            val list = after["checklist"] as? List<*> ?: emptyList<Any?>()
            require(after["checklist"] == null || after["checklist"] is List<*>) { "Invalid checklist." }
            require(list.size <= 100) { "A checklist supports up to 100 items." }
            val ids = mutableSetOf<String>()
            list.forEach { item ->
                val entry = item as? Map<*, *>
                val id = entry?.get("id") as? String
                val title = entry?.get("title") as? String
                require(id != null && id.isNotBlank() && id.length <= 120 && ids.add(id)) { "Checklist items need unique IDs of up to 120 characters." }
                require(title != null && title.trim().isNotEmpty() && title.length <= 180) { "Checklist titles must contain 1–180 characters." }
                require(entry?.get("completed") is Boolean) { "Invalid checklist completion value." }
            }
        }
    }

    private data class Location(
        val board: String, val section: String, val archived: Boolean,
        val boardArchived: Boolean, val sectionArchived: Boolean, val task: Map<String, Any?>
    )
    private fun locations(document: Map<String, Any?>): Map<String, Location> {
        val result = mutableMapOf<String, Location>()
        maps(document["boards"]).forEach { board -> maps(board["sections"]).forEach { section -> maps(section["tasks"]).forEach { task ->
            val id = task["id"] as? String ?: conflict("Task identity is missing.")
            if (result.containsKey(id)) conflict("Task identity is ambiguous.")
            result[id] = Location(board["id"] as? String ?: "", section["id"] as? String ?: "", task["archived"] == true,
                board["archived"] == true, section["archived"] == true, task)
        } } }
        return result
    }
    @Suppress("UNCHECKED_CAST")
    private fun maps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
    private fun completionChanged(before: Map<String, Any?>, after: Map<String, Any?>): Boolean {
        val old = maps(before["checklist"]).associate { it["id"] to (it["completed"] == true) }
        val next = maps(after["checklist"]).associate { it["id"] to (it["completed"] == true) }
        return next.any { (id, completed) -> completed != (old[id] ?: false) } || old.any { (id, completed) -> completed && id !in next }
    }
    private fun taskFields(task: KairosTask): Map<String, Any?> = locations(KairosPlan(boards = listOf(KairosBoard(id = "b", sections = listOf(KairosSection(id = "s", tasks = listOf(task)))))).sharedFields()).getValue(task.id).task
    private fun conflict(message: String): Nothing = throw SharedDataConflict(message)
}

package com.kairos.app.ui.screens.tasks

import androidx.compose.runtime.saveable.listSaver
import com.kairos.app.data.models.KairosChecklistItem
import com.kairos.app.data.models.KairosTask

/** Saved as primitives so drafts, including checklist identities, survive activity recreation. */
internal data class TaskEditorDraft(
    val title: String,
    val description: String,
    val notes: String,
    val priority: String,
    val estimateEnabled: Boolean,
    val estimate: String,
    val reminderEnabled: Boolean,
    val reminder: String,
    val locked: Boolean,
    val preference: String,
    val date: String?,
    val start: String?,
    val end: String?,
    val deadline: String?,
    val dueTime: String?,
    val checklist: List<KairosChecklistItem>
) {
    constructor(task: KairosTask) : this(
        task.title, task.taskDescription.orEmpty(), task.notes ?: task.taskDescription.orEmpty(),
        task.priority.orEmpty(), task.estimatedMinutes != null, task.estimatedMinutes?.toString().orEmpty(),
        task.reminderMinutes != null, (task.reminderMinutes ?: 15).toString(), task.scheduleLocked == true,
        task.schedulingPreference.orEmpty(), task.date, task.startTime, task.endTime, task.dueDate,
        task.dueTime, task.checklist.orEmpty()
    )

    fun edited(original: KairosTask): KairosTask = original.copy(
        title = if (title == original.title) original.title else title.trim(),
        taskDescription = if (description == original.taskDescription.orEmpty()) original.taskDescription else description,
        notes = if (notes == (original.notes ?: original.taskDescription.orEmpty())) original.notes else notes,
        priority = if (priority == original.priority.orEmpty()) original.priority else priority.ifEmpty { null },
        estimatedMinutes = if (estimateEnabled) estimate.toIntOrNull() else null,
        reminderMinutes = if (reminderEnabled) reminder.toIntOrNull() else null,
        scheduleLocked = if (locked == (original.scheduleLocked == true)) original.scheduleLocked else locked,
        schedulingPreference = if (preference == original.schedulingPreference.orEmpty()) original.schedulingPreference else preference.ifEmpty { null },
        date = date, startTime = start, endTime = end, dueDate = deadline, dueTime = dueTime,
        checklist = if (checklist == original.checklist.orEmpty()) original.checklist else checklist
    )

    fun inputError(original: KairosTask): String? = when {
        title.isBlank() -> "Enter a task title."
        estimateEnabled && (estimate != original.estimatedMinutes?.toString()) && estimate.toIntOrNull() !in 1..1440 -> "Enter an estimate from 1 to 1440 minutes."
        reminderEnabled && (reminder != original.reminderMinutes?.toString()) && reminder.toIntOrNull() !in 1..10080 -> "Enter reminder minutes from 1 to 10080, or turn the reminder off."
        else -> null
    }

    companion object {
        val Saver = listSaver<TaskEditorDraft, Any>(save = { draft ->
            listOf(draft.title, draft.description, draft.notes, draft.priority,
                draft.estimateEnabled, draft.estimate, draft.reminderEnabled, draft.reminder,
                draft.locked, draft.preference, draft.date ?: "", draft.start ?: "", draft.end ?: "",
                draft.deadline ?: "", draft.dueTime ?: "", draft.date != null, draft.start != null,
                draft.end != null, draft.deadline != null, draft.dueTime != null) +
                draft.checklist.flatMap { listOf(it.id, it.title, it.completed) }
        }, restore = { values ->
            fun nullable(index: Int, flag: Int) = if (values[flag] as Boolean) values[index] as String else null
            TaskEditorDraft(values[0] as String, values[1] as String, values[2] as String,
                values[3] as String, values[4] as Boolean, values[5] as String,
                values[6] as Boolean, values[7] as String, values[8] as Boolean,
                values[9] as String, nullable(10, 15), nullable(11, 16), nullable(12, 17),
                nullable(13, 18), nullable(14, 19), values.drop(20).chunked(3).map {
                    KairosChecklistItem(it[0] as String, it[1] as String, it[2] as Boolean)
                })
        })
    }
}

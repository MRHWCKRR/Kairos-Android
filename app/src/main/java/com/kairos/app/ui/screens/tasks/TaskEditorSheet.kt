package com.kairos.app.ui.screens.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.data.models.KairosChecklistItem
import com.kairos.app.data.models.KairosTask
import com.kairos.app.ui.components.KairosCard
import com.kairos.app.ui.components.KairosTextButton
import com.kairos.app.ui.icons.KairosIcons
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** The caller holds the original snapshot and account scope stable until dismissal. */
@Composable
fun TaskEditorSheet(
    original: KairosTask,
    draftKey: String,
    isSaving: Boolean,
    error: String?,
    available: Boolean,
    onSave: (KairosTask) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(draftKey, stateSaver = TaskEditorDraft.Saver) { mutableStateOf(TaskEditorDraft(original)) }
    var showDiscard by rememberSaveable(draftKey) { mutableStateOf(false) }
    var validationError by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    val initial = remember(original, draftKey) { TaskEditorDraft(original) }
    val dismiss = {
        if (!isSaving) {
            if (draft != initial) showDiscard = true else onDismiss()
        }
    }
    val update: (TaskEditorDraft) -> Unit = { draft = it; validationError = null }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = false, dismissOnClickOutside = false
    )) {
        BackHandler { dismiss() }
        Surface(Modifier.fillMaxSize().then(editorTag("sheet")), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KairosTextButton(dismiss, enabled = !isSaving, modifier = editorTag("cancel")) { Text("Cancel") }
                    Text("Task details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    KairosTextButton(onClick = {
                        val inputError = draft.inputError(original)
                        if (inputError != null) validationError = inputError else {
                            val edited = draft.edited(original)
                            try {
                                com.kairos.app.data.repository.TaskEditContract.validateTask(original, edited)
                                validationError = null
                                onSave(edited)
                            } catch (invalid: IllegalArgumentException) {
                                validationError = invalid.message ?: "Review the task details before saving."
                            }
                        }
                    }, enabled = !isSaving && available && draft.title.isNotBlank(), modifier = editorTag("save")) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Save")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Keep failures visible even when the field that failed is below the fold.
                if (!available) EditorMessage("This task moved or became unavailable. Your draft is still here; reopen its current details before saving.")
                (validationError ?: error)?.let { EditorMessage(it, isError = true) }
                if (isSaving) Text("Saving…", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).then(editorTag("scroll")).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    EditorCard {
                        EditorField(draft.title, { update(draft.copy(title = it)) }, "Task title", "title", !isSaving,
                            minLines = 1, textStyle = MaterialTheme.typography.titleLarge)
                        EditorCaption("Edit the task without leaving your checklist.")
                        EditorField(draft.description, { update(draft.copy(description = it)) }, "Description", "description", !isSaving, minLines = 2)
                    }
                    EditorCard {
                        EditorHeading("Notes", KairosIcons.symbol("text.alignleft"))
                        EditorField(draft.notes, { update(draft.copy(notes = it)) }, "Notes", "notes", !isSaving, minLines = 5,
                            supportingText = "${draft.notes.length}/3000", isError = draft.notes.length > 3000)
                    }
                    EditorCard {
                        EditorChoice("Priority", draft.priority, listOf("" to "None", "low" to "Low", "medium" to "Medium", "high" to "High"), "priority", !isSaving) { update(draft.copy(priority = it)) }
                        EditorToggle("Estimated time", draft.estimateEnabled, "estimated-toggle", !isSaving) { update(draft.copy(estimateEnabled = it)) }
                        if (draft.estimateEnabled) EditorField(draft.estimate, { update(draft.copy(estimate = it)) }, "Estimated minutes", "estimate", !isSaving,
                            numeric = true, supportingText = "Minutes (1–1440)")
                        EditorCaption("Notes support up to 3000 characters.")
                    }
                    EditorCard {
                        EditorHeading("Planning & reminders", KairosIcons.symbol("calendar.badge.clock"))
                        EditorToggle("Task reminder", draft.reminderEnabled, "reminder-toggle", !isSaving) { update(draft.copy(reminderEnabled = it)) }
                        if (draft.reminderEnabled) {
                            EditorField(draft.reminder, { update(draft.copy(reminder = it)) }, "Reminder minutes before scheduled start", "reminder", !isSaving,
                                numeric = true, supportingText = "Minutes before (1–10080)")
                            EditorCaption("Reminders use the scheduled date and start time. Allow notifications in Android and Kairos Settings for alerts.")
                        }
                        EditorChoice("Preferred time", draft.preference, listOf("" to "Any time", "morning" to "Morning", "afternoon" to "Afternoon", "evening" to "Evening"), "preference", !isSaving) { update(draft.copy(preference = it)) }
                        EditorToggle("Lock schedule", draft.locked, "lock", !isSaving) { update(draft.copy(locked = it)) }
                        EditorCaption("The lock keeps this placement fixed during AI planning and touch rescheduling. Unlock it to edit the scheduled date or time. Changing a preferred time does not move this task.")
                    }
                    EditorCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EditorHeading("Checklist", KairosIcons.Checklist, Modifier.weight(1f))
                            Text("${draft.checklist.count { it.completed }}/${draft.checklist.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        draft.checklist.forEachIndexed { index, item ->
                            key(item.id, index) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(item.completed, { completed -> update(draft.copy(checklist = draft.checklist.toMutableList().also { it[index] = item.copy(completed = completed) })) },
                                        enabled = !isSaving && original.recurrence?.enabled != true, modifier = editorTag("checklist-completed-${item.id}").semantics { contentDescription = "Complete checklist item ${index + 1}" })
                                    EditorField(item.title, { title -> update(draft.copy(checklist = draft.checklist.toMutableList().also { it[index] = item.copy(title = title) })) },
                                        "Checklist item", "checklist-title-${item.id}", !isSaving, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { update(draft.copy(checklist = draft.checklist.filterIndexed { position, _ -> position != index })) },
                                        enabled = !isSaving, modifier = editorTag("checklist-delete-${item.id}")) {
                                        Icon(KairosIcons.Delete, "Remove checklist item ${index + 1}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        KairosTextButton(onClick = { update(draft.copy(checklist = draft.checklist + KairosChecklistItem(id = UUID.randomUUID().toString()))) },
                            enabled = !isSaving && draft.checklist.size < 100, modifier = editorTag("checklist-add")) {
                            Icon(KairosIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add checklist item")
                        }
                        if (original.recurrence?.enabled == true) EditorCaption("Checklist completion is managed for each recurring occurrence.")
                    }
                    EditorCard {
                        EditorHeading("Schedule", KairosIcons.CalendarMonth)
                        val scheduleEnabled = !isSaving && !draft.locked && original.recurrence?.enabled != true
                        EditorToggle("Scheduled date", draft.date != null, "scheduled-date-toggle", scheduleEnabled) { update(draft.copy(date = if (it) LocalDate.now().toString() else null)) }
                        draft.date?.let { value -> EditorDate("Date", value, "scheduled-date", scheduleEnabled) { update(draft.copy(date = it)) } }
                        EditorToggle("Scheduled time", draft.start != null || draft.end != null, "scheduled-time-toggle", scheduleEnabled) { enabled ->
                            update(draft.copy(start = if (enabled) "09:00" else null, end = if (enabled) "10:00" else null))
                        }
                        if (draft.start != null || draft.end != null) {
                            EditorTime("Start", draft.start.orEmpty(), "start-time", scheduleEnabled) { update(draft.copy(start = it)) }
                            EditorTime("End", draft.end.orEmpty(), "end-time", scheduleEnabled) { update(draft.copy(end = it)) }
                        }
                        if (draft.locked) EditorCaption("Unlock the schedule to change its date or time.")
                        if (original.recurrence?.enabled == true) EditorCaption("Scheduled dates and times are managed by the task’s repeat rule.")
                        EditorToggle("Deadline", draft.deadline != null, "deadline-toggle", !isSaving) { update(draft.copy(deadline = if (it) LocalDate.now().toString() else null)) }
                        draft.deadline?.let { value -> EditorDate("Due date", value, "due-date", !isSaving) { update(draft.copy(deadline = it)) } }
                        EditorToggle("Due time", draft.dueTime != null, "due-time-toggle", !isSaving) { update(draft.copy(dueTime = if (it) "17:00" else null)) }
                        draft.dueTime?.let { value -> EditorTime("Time", value, "due-time", !isSaving) { update(draft.copy(dueTime = it)) } }
                        EditorCaption("Scheduled time appears in Schedule on mobile and web. Your deadline is separate.")
                    }
                }
            }
        }
        if (showDiscard) AlertDialog(
            onDismissRequest = { if (!isSaving) showDiscard = false },
            title = { Text("Discard changes?") }, text = { Text("Your unsaved changes will be lost.") },
            confirmButton = { KairosTextButton(onClick = onDismiss, enabled = !isSaving, modifier = editorTag("discard-confirm")) { Text("Discard changes") } },
            dismissButton = { KairosTextButton(onClick = { showDiscard = false }, enabled = !isSaving, modifier = editorTag("keep-editing")) { Text("Keep editing") } }
        )
    }
}

private fun editorTag(name: String) = Modifier.testTag("task-editor-$name")

@Composable private fun EditorCard(content: @Composable ColumnScope.() -> Unit) {
    KairosCard(contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun EditorHeading(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable private fun EditorCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun EditorMessage(message: String, isError: Boolean = false) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).then(editorTag(if (isError) "error" else "unavailable")).semantics { liveRegion = LiveRegionMode.Polite },
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

@Composable private fun EditorField(
    value: String, onChange: (String) -> Unit, label: String, tag: String, enabled: Boolean,
    modifier: Modifier = Modifier, minLines: Int = 1, numeric: Boolean = false,
    supportingText: String? = null, isError: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth().then(editorTag(tag)), enabled = enabled,
        label = { Text(label) }, minLines = minLines, singleLine = numeric,
        textStyle = textStyle, isError = isError, shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        supportingText = supportingText?.let { text -> { Text(text) } },
        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = MaterialTheme.colorScheme.secondary))
}

@Composable private fun EditorToggle(label: String, checked: Boolean, tag: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked, onChange, enabled = enabled, modifier = editorTag(tag).semantics { contentDescription = label })
    }
}

@Composable private fun EditorChoice(label: String, value: String, choices: List<Pair<String, String>>, tag: String, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            KairosTextButton(onClick = { expanded = true }, enabled = enabled, modifier = editorTag(tag)) {
                Text(choices.firstOrNull { it.first == value }?.second ?: "Saved: $value")
                Icon(KairosIcons.ArrowDropDown, null, Modifier.padding(start = 6.dp).size(16.dp))
            }
            DropdownMenu(expanded && enabled, { expanded = false }) {
                choices.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onChange(key); expanded = false }) }
                if (choices.none { it.first == value }) DropdownMenuItem(text = { Text("Saved: $value") }, onClick = { expanded = false })
            }
        }
    }
}

// Date keys are calendar dates, never instants. Native picker components avoid UTC offset shifts.
@Composable private fun EditorDate(label: String, value: String, tag: String, enabled: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { LocalDate.parse(value) }.getOrNull()
    PickerRow(label, value, tag, enabled) {
        val seed = parsed ?: LocalDate.now()
        DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, seed.year, seed.monthValue - 1, seed.dayOfMonth).show()
    }
}

@Composable private fun EditorTime(label: String, value: String, tag: String, enabled: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { LocalTime.parse(value) }.getOrNull()
    PickerRow(label, value.ifEmpty { "Not set" }, tag, enabled) {
        val seed = parsed ?: LocalTime.of(9, 0)
        TimePickerDialog(context, { _, hour, minute -> onChange(LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))) }, seed.hour, seed.minute, DateFormat.is24HourFormat(context)).show()
    }
}

@Composable private fun PickerRow(label: String, value: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        KairosTextButton(onClick, enabled = enabled, modifier = editorTag(tag)) { Text(value) }
    }
}

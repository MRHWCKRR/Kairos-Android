package com.kairos.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kairos.app.data.repository.PendingEdit
import com.kairos.app.ui.theme.KairosMetrics

/** Shared by onboarding and the app shell; callbacks own persistence decisions. */
@Composable
fun RetainedEditsFeedback(
    retainedEdits: List<PendingEdit>,
    retentionError: String?,
    errorMessage: String?,
    isSaving: Boolean,
    canRetrySave: Boolean,
    onRetrySave: () -> Unit,
    onDiscardAll: () -> Unit,
    onRetryEdit: (String) -> Unit,
    onDiscardEdit: (String) -> Unit
) {
    var reviewing by remember { mutableStateOf(false) }
    LaunchedEffect(retainedEdits.isEmpty()) {
        if (retainedEdits.isEmpty()) reviewing = false
    }
    Column(Modifier.fillMaxWidth()) {
        if (retainedEdits.isNotEmpty()) {
            Snackbar(
                modifier = Modifier.padding(KairosMetrics.itemSpacing)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                action = {
                    KairosTextButton(onClick = { reviewing = true }, enabled = !isSaving) { Text("Review") }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (retainedEdits.size == 1) "1 retained edit" else "${retainedEdits.size} retained edits")
                    if (isSaving) SavingEditsProgress()
                }
            }
        } else if (isSaving) {
            Snackbar(Modifier.padding(KairosMetrics.itemSpacing)) { SavingEditsProgress() }
        }
        if (retentionError != null || errorMessage != null) {
            Snackbar(
                modifier = Modifier.padding(KairosMetrics.itemSpacing)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    // Retry journal records individually after review. A corrupt journal
                    // can only be cleared by an explicit discard.
                    Row {
                        if (retentionError == null && retainedEdits.isEmpty() && canRetrySave) {
                            KairosTextButton(onClick = onRetrySave, enabled = !isSaving) { Text("Retry") }
                        }
                        if (retentionError != null || retainedEdits.isEmpty()) {
                            KairosTextButton(onClick = onDiscardAll, enabled = !isSaving) {
                                Text(if (retentionError != null) "Discard all" else "Discard")
                            }
                        }
                    }
                }
            ) {
                Text(if (retentionError != null) {
                    "Retained edits could not be read. Discard all to clear saved edits on this device."
                } else errorMessage.orEmpty())
            }
        }
    }
    if (reviewing && retainedEdits.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { reviewing = false },
            modifier = Modifier.testTag("retained-edits-review"),
            title = { Text("Retained edits") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(KairosMetrics.itemSpacing)
                ) {
                    items(retainedEdits, key = { it.id }) { edit ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(edit.reviewLabel(), style = MaterialTheme.typography.titleSmall)
                            remember(edit) { edit.plannerDescription() }?.let { description ->
                                Text(
                                    description, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                KairosTextButton(
                                    onClick = { onRetryEdit(edit.id) },
                                    enabled = !isSaving && retentionError == null
                                ) { Text("Retry") }
                                KairosTextButton(onClick = { onDiscardEdit(edit.id) }, enabled = !isSaving) {
                                    Text("Discard", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { KairosTextButton(onClick = { reviewing = false }) { Text("Done") } }
        )
    }
}

private fun PendingEdit.reviewLabel(): String = when {
    kind == "plan" -> "Planner edit"
    kind == "ai" -> "AI workspace edit"
    base["aiChatHistory"] != edited["aiChatHistory"] -> "AI chat edit"
    else -> "Settings and progress edit"
}

/** Only display authored titles; identifiers are used to match snapshots, never as labels. */
private fun PendingEdit.plannerDescription(): String? {
    if (kind != "plan") return null
    fun children(value: Any?): List<Map<*, *>> =
        (value as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
    fun indexed(values: List<Map<*, *>>): Map<String, Map<*, *>> =
        values.mapNotNull { value ->
            (value["id"] as? String)?.takeIf { it.isNotBlank() }?.let { it to value }
        }.toMap()
    fun title(value: Map<*, *>?): String? =
        (value?.get("title") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    fun changed(before: Map<String, Map<*, *>>, after: Map<String, Map<*, *>>): List<String> =
        (before.keys + after.keys).filter { before[it] != after[it] }
    val beforeBoards = indexed(children(base["boards"]))
    val afterBoards = indexed(children(edited["boards"]))
    fun tasks(boards: Map<String, Map<*, *>>): Map<String, Map<*, *>> = indexed(
        boards.values.flatMap { board ->
            children(board["sections"]).flatMap { section -> children(section["tasks"]) }
        }
    )
    val beforeTasks = tasks(beforeBoards)
    val afterTasks = tasks(afterBoards)
    val changedTasks = changed(beforeTasks, afterTasks)
    if (changedTasks.size == 1) {
        val id = changedTasks.single()
        title(afterTasks[id] ?: beforeTasks[id])?.let { return it }
    }
    val changedBoards = changed(beforeBoards, afterBoards)
    return changedBoards.singleOrNull()?.let { id -> title(afterBoards[id] ?: beforeBoards[id]) }
}

@Composable
private fun SavingEditsProgress() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text("Saving.")
    }
}

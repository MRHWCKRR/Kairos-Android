package com.kairos.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairos.app.data.models.KairosPlan

@Composable
fun PlannerSearchDialog(plan: KairosPlan?, onDismiss: () -> Unit, onOpenTask: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(plan, query) {
        plan?.boards?.filter { !it.archived }?.flatMap { board ->
            board.sections.filter { !it.archived }.flatMap { section ->
                section.tasks.filter { !it.archived && query.isNotBlank() && it.title.contains(query.trim(), ignoreCase = true) }
                    .map { Triple(it, board.title, section.title) }
            }
        } ?: emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search planner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KairosTextField(query, { query = it }, "Search tasks", singleLine = true)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (query.isNotBlank() && results.isEmpty()) item { Text("No matching tasks", Modifier.padding(vertical = 20.dp)) }
                    items(results) { (task, board, section) ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }.padding(vertical = 12.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                            Text("$board · $section", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text("Close") } }
    )
}

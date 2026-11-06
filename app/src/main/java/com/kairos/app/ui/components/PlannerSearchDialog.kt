package com.kairos.app.ui.components

import com.kairos.app.ui.icons.KairosIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kairos.app.data.models.KairosPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerSearchDialog(
    plan: KairosPlan?,
    onDismiss: () -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    timezone: String,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    // Sheet dialogs create their own composition; retain the app's accessibility scale.
    val inheritedDensity = LocalDensity.current
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(PlannerSearchStatus.All) }
    var selectedBoard by remember { mutableStateOf<PlannerSearchTarget?>(null) }
    var selectedTask by remember { mutableStateOf<PlannerSearchTarget?>(null) }
    val zone = remember(timezone) { PlannerSearch.zone(timezone) }
    var today by remember(zone) { mutableStateOf(LocalDate.now(zone)) }
    LaunchedEffect(zone) {
        while (true) { today = LocalDate.now(zone); delay(60_000) }
    }
    // Keying the producer removes stale rows immediately when the planner changes.
    val index = key(plan, today) {
        produceState<List<PlannerSearchRow>?>(null, plan, today) {
            value = plan?.let { withContext(Dispatchers.Default) { PlannerSearch.index(it, today) } }
        }.value
    }
    val results = key(index, query, status) {
        produceState<PlannerSearchResults?>(null, index, query, status) {
            if (index != null) {
                delay(180)
                value = withContext(Dispatchers.Default) { PlannerSearch.search(index, query, status) }
            }
        }.value
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val open: (PlannerSearchRow) -> Unit = { row ->
        keyboard?.hide()
        if (row.isBoard) selectedBoard = row.target else selectedTask = row.target
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null
    ) {
        CompositionLocalProvider(LocalDensity provides inheritedDensity) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding().testTag("planner-search-sheet")) {
                SearchSheetHeader("Search", onDismiss)
                val focus = remember { FocusRequester() }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(PlannerSearch.QUERY_LIMIT) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(focus)
                        .semantics { contentDescription = "Search planner" },
                    placeholder = { Text("Tasks, boards, assignments", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(KairosIcons.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(KairosIcons.Cancel, "Clear search")
                        }
                    }
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val filterWidth = maxOf(maxWidth - 32.dp, (82f * fontScale * PlannerSearchStatus.entries.size).dp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.width(filterWidth)) {
                            PlannerSearchStatus.entries.forEachIndexed { position, option ->
                                SegmentedButton(
                                    selected = status == option, onClick = { status = option },
                                    shape = SegmentedButtonDefaults.itemShape(position, PlannerSearchStatus.entries.size),
                                    icon = {},
                                    label = { Text(option.label, maxLines = 1, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
                when {
                    plan == null -> SearchMessage(
                        if (isLoading) "Loading planner" else "Search unavailable",
                        errorMessage ?: "Search will be available when your planner has loaded.", isLoading
                    )
                    results == null -> SearchMessage("Searching your planner", loading = true)
                    else -> LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).nestedScroll(remember(keyboard) {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    if (available.y != 0f) keyboard?.hide()
                                    return Offset.Zero
                                }
                            }
                        }),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                if (results.total > results.rows.size) "Showing ${results.rows.size} of ${results.total} matches. Refine your search to see more."
                                else "${results.total} ${if (results.total == 1) "result" else "results"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (results.rows.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(KairosIcons.Search, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No results", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                Text(if (query.isBlank()) "No planner items match this filter." else "Try a different search or filter.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        items(results.rows, key = { it.target.key }) { row -> SearchResultRow(row) { open(row) } }
                    }
                }
            }
        }
    }
    selectedBoard?.let { target ->
        val boardRow = index?.singleOrNull { it.target == target }
        val board = plan?.boards?.singleOrNull { it.id == target.boardId && !it.archived }
        ModalBottomSheet(
            onDismissRequest = { selectedBoard = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null
        ) {
            CompositionLocalProvider(LocalDensity provides inheritedDensity) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).testTag("planner-search-board")) {
                    SearchSheetHeader(boardRow?.title ?: "Board") { selectedBoard = null }
                    if (boardRow == null || board == null) SearchMessage("This board is no longer available.")
                    else LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val sections = board.sections.filter { section -> !section.archived && board.sections.count { it.id == section.id } == 1 }
                        if (sections.isEmpty()) item { Text("No sections yet") }
                        sections.forEach { section ->
                            item { Text(section.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                            val tasks = index.orEmpty().filter { it.target.boardId == target.boardId && it.target.sectionId == section.id }
                            if (tasks.isEmpty()) item { Text("No tasks", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(tasks, key = { it.target.key }) { row -> SearchResultRow(row) { open(row) } }
                        }
                    }
                }
            }
        }
    }
    selectedTask?.let { target ->
        // Resolve against the current index, so deleted, archived and ambiguous targets cannot be edited.
        val row = index?.singleOrNull { it.target == target }
        ModalBottomSheet(onDismissRequest = { selectedTask = null }, dragHandle = null) {
            CompositionLocalProvider(LocalDensity provides inheritedDensity) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).testTag("planner-search-task")) {
                    SearchSheetHeader("Task details") { selectedTask = null }
                    if (row == null) SearchMessage("This task is no longer available.")
                    else Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(row.title, style = MaterialTheme.typography.headlineSmall)
                        Text(row.path, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SearchResultDate(row)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconToggleButton(checked = row.completed, onCheckedChange = { completed -> onToggleTask(requireNotNull(target.taskId), completed) }) {
                                Icon(if (row.completed) KairosIcons.CheckCircle else KairosIcons.RadioButtonUnchecked,
                                    if (row.completed) "Mark unfinished" else "Mark completed", tint = MaterialTheme.colorScheme.secondary)
                            }
                            Text(row.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSheetHeader(title: String, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        KairosTextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { Text("Done") }
    }
}

@Composable
private fun SearchMessage(title: String, description: String? = null, loading: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SearchResultRow(row: PlannerSearchRow, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (row.isBoard) KairosIcons.Layers else if (row.completed) KairosIcons.CheckCircle else KairosIcons.RadioButtonUnchecked,
                null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${row.path} · ${row.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SearchResultDate(row)
            }
            Icon(KairosIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchResultDate(row: PlannerSearchRow) {
    row.date?.let { date ->
        Text("Scheduled $date${if (row.overdue) " · Overdue" else ""}", style = MaterialTheme.typography.bodySmall,
            color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

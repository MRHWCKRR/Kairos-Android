package com.kairos.app.ui.screens.tasks

import com.kairos.app.ui.components.KairosTextButton
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosBoard
import com.kairos.app.data.models.KairosSection
import com.kairos.app.data.models.KairosTask
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.components.KairosCard
import com.kairos.app.ui.components.KairosOverflowMenu
import com.kairos.app.ui.components.KairosTaskRow
import com.kairos.app.ui.components.KairosInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: MainViewModel = viewModel(), requestedTaskId: String? = null, onTaskRequestConsumed: () -> Unit = {}) {
    val plan by viewModel.plan.collectAsState()
    val boards = plan?.boards?.filter { !it.archived } ?: emptyList()

    var showAddBoardDialog by remember { mutableStateOf(false) }
    var boardToRename by remember { mutableStateOf<KairosBoard?>(null) }
    var sectionToRename by remember { mutableStateOf<KairosSection?>(null) }
    var taskToRename by remember { mutableStateOf<KairosTask?>(null) }

    var boardIdForNewSection by remember { mutableStateOf<String?>(null) }
    var sectionIdForNewTask by remember { mutableStateOf<String?>(null) }

    var showCompleted by remember { mutableStateOf(true) }
    var showArchive by remember { mutableStateOf(false) }
    LaunchedEffect(requestedTaskId, plan) {
        if (requestedTaskId != null && plan != null) {
            taskToRename = boards.flatMap { it.sections.filter { section -> !section.archived } }.flatMap { it.tasks }.firstOrNull { it.id == requestedTaskId && !it.archived }
            onTaskRequestConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBoardDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(58.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Board")
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tasks", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    KairosTextButton(onClick = { showArchive = true }) { Text("Archive", color = MaterialTheme.colorScheme.secondary) }
                }
                Text("Everything you need to get done, organized into focused checklists.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp))
            }
            item { TasksSummary(boards) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Checklists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    KairosTextButton(onClick = { showCompleted = !showCompleted }) {
                        Text(if (showCompleted) "Hide completed" else "Show completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (boards.isEmpty()) item {
                KairosCard {
                    Icon(Icons.Default.Checklist, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Your checklist is empty", style = MaterialTheme.typography.titleMedium)
                    Text("Use the + button to create your first board.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
                items(boards) { board ->
                    BoardCard(
                        board = board,
                        onRename = { boardToRename = board },
                        onArchive = { viewModel.archiveBoard(board.id) },
                        onAddSection = { boardIdForNewSection = board.id },
                        onSectionRename = { sectionToRename = it },
                        onSectionArchive = { viewModel.archiveSection(it.id) },
                        onAddTask = { sectionIdForNewTask = it.id },
                        onTaskToggle = { taskId, completed -> viewModel.toggleTask(taskId, completed) },
                        onTaskRename = { taskToRename = it },
                        onTaskArchive = { viewModel.archiveTask(it.id) },
                        showCompleted = showCompleted
                    )
                }
        }
    }
    if (showArchive) ModalBottomSheet(onDismissRequest = { showArchive = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            item { ArchiveSection(plan, viewModel, initiallyExpanded = true) }
        }
    }

    // Dialogs...
    if (showAddBoardDialog) {
        InputDialog(
            title = "New Board",
            onDismiss = { showAddBoardDialog = false },
            onConfirm = { title ->
                viewModel.addBoard(title)
                showAddBoardDialog = false
            }
        )
    }

    boardToRename?.let { board ->
        InputDialog(
            title = "Rename Board",
            initialValue = board.title,
            onDismiss = { boardToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameBoard(board.id, newTitle)
                boardToRename = null
            }
        )
    }

    boardIdForNewSection?.let { boardId ->
        InputDialog(
            title = "New Section",
            onDismiss = { boardIdForNewSection = null },
            onConfirm = { title ->
                viewModel.addSection(boardId, title)
                boardIdForNewSection = null
            }
        )
    }

    sectionToRename?.let { section ->
        InputDialog(
            title = "Rename Section",
            initialValue = section.title,
            onDismiss = { sectionToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameSection(section.id, newTitle)
                sectionToRename = null
            }
        )
    }

    sectionIdForNewTask?.let { sectionId ->
        InputDialog(
            title = "New Task",
            onDismiss = { sectionIdForNewTask = null },
            onConfirm = { title ->
                viewModel.addTask(sectionId, title)
                sectionIdForNewTask = null
            }
        )
    }

    taskToRename?.let { task ->
        InputDialog(
            title = "Rename Task",
            initialValue = task.title,
            onDismiss = { taskToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameTask(task.id, newTitle)
                taskToRename = null
            }
        )
    }

}

@Composable
fun ArchiveSection(plan: com.kairos.app.data.models.KairosPlan?, viewModel: MainViewModel, initiallyExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    val archivedBoards = plan?.boards?.filter { it.archived } ?: emptyList()
    val archivedSections = mutableListOf<Pair<KairosBoard, KairosSection>>()
    val archivedTasks = mutableListOf<Triple<KairosBoard, KairosSection, KairosTask>>()

    plan?.boards?.forEach { board ->
        board.sections.forEach { section ->
            if (section.archived && !board.archived) {
                archivedSections.add(board to section)
            }
            section.tasks.forEach { task ->
                if (task.archived && !section.archived && !board.archived) {
                    archivedTasks.add(Triple(board, section, task))
                }
            }
        }
    }

    val totalCount = archivedBoards.size + archivedSections.size + archivedTasks.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Archive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (totalCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = totalCount.toString(), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        if (expanded) {
            if (totalCount == 0) {
                Text(
                    text = "Nothing archived yet. Archived items show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    if (archivedBoards.isNotEmpty()) {
                        Text(
                            text = "Archived Boards",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        archivedBoards.forEach { board ->
                            ArchiveItemRow(
                                title = board.title,
                                onRestore = { viewModel.restoreBoard(board.id) },
                                onDelete = { viewModel.deleteBoardForever(board.id) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (archivedSections.isNotEmpty()) {
                        Text(
                            text = "Archived Sections",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        archivedSections.forEach { (board, section) ->
                            ArchiveItemRow(
                                title = section.title,
                                subtitle = "— ${board.title}",
                                onRestore = { viewModel.restoreSection(section.id) },
                                onDelete = { viewModel.deleteSectionForever(board.id, section.id) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (archivedTasks.isNotEmpty()) {
                        Text(
                            text = "Archived Tasks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        archivedTasks.forEach { (board, section, task) ->
                            ArchiveItemRow(
                                title = task.title,
                                subtitle = "— ${board.title} / ${section.title}",
                                onRestore = { viewModel.restoreTask(task.id) },
                                onDelete = { viewModel.deleteTaskForever(section.id, task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveItemRow(
    title: String,
    subtitle: String = "",
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Row {
            KairosTextButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Restore", fontSize = 12.sp)
            }
            KairosTextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Delete Forever", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun BoardCard(
    board: KairosBoard,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onAddSection: () -> Unit,
    onSectionRename: (KairosSection) -> Unit,
    onSectionArchive: (KairosSection) -> Unit,
    onAddTask: (KairosSection) -> Unit,
    onTaskToggle: (String, Boolean) -> Unit,
    onTaskRename: (KairosTask) -> Unit,
    onTaskArchive: (KairosTask) -> Unit,
    showCompleted: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            val boardTasks = board.sections.filter { !it.archived }.flatMap { it.tasks.filter { task -> !task.archived } }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Checklist, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                Text(
                    text = board.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${boardTasks.count { it.completed }} of ${boardTasks.size} complete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Board Options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("Archive") }, onClick = { showMenu = false; onArchive() })
                    }
                }
            }

            if (boardTasks.isNotEmpty()) LinearProgressIndicator(
                progress = { boardTasks.count { it.completed }.toFloat() / boardTasks.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            board.sections.filter { !it.archived }.forEach { section ->
                SectionBlock(
                    section = section,
                    onRename = { onSectionRename(section) },
                    onArchive = { onSectionArchive(section) },
                    onAddTask = { onAddTask(section) },
                    onTaskToggle = onTaskToggle,
                    onTaskRename = onTaskRename,
                    onTaskArchive = onTaskArchive,
                    showCompleted = showCompleted
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            KairosTextButton(onClick = onAddSection) {
                Text("+ Add Section", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun SectionBlock(
    section: KairosSection,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onAddTask: () -> Unit,
    onTaskToggle: (String, Boolean) -> Unit,
    onTaskRename: (KairosTask) -> Unit,
    onTaskArchive: (KairosTask) -> Unit,
    showCompleted: Boolean = true
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
    Column(Modifier.padding(15.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Section Options", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { showMenu = false; onArchive() })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        section.tasks.filter { !it.archived && (showCompleted || !it.completed) }.sortedBy { it.completed }.forEach { task ->
            TaskItem(
                task = task,
                onToggle = { onTaskToggle(task.id, it) },
                onRename = { onTaskRename(task) },
                onArchive = { onTaskArchive(task) }
            )
        }

        KairosTextButton(onClick = onAddTask, contentPadding = PaddingValues(0.dp)) {
            Text("+ Add Task", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}
}

@Composable
fun TaskItem(
    task: KairosTask,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit
) {
    KairosTaskRow(
        title = task.title,
        completed = task.completed,
        onToggle = onToggle,
        onOpen = onRename,
        trailing = {
            KairosOverflowMenu("Options for " + task.title) { dismiss ->
                DropdownMenuItem(text = { Text("Rename task") }, onClick = { dismiss(); onRename() })
                DropdownMenuItem(text = { Text("Archive task") }, onClick = { dismiss(); onArchive() })
            }
        }
    )
}

@Composable
fun InputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    KairosInputDialog(title, initialValue, onDismiss, onConfirm)
}


@Composable
fun TasksSummary(boards: List<KairosBoard>) {
    val tasks = boards.filter { !it.archived }.flatMap { it.sections.filter { section -> !section.archived } }.flatMap { it.tasks.filter { task -> !task.archived } }
    val done = tasks.count { it.completed }
    val progress = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    KairosCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your checklist", style = MaterialTheme.typography.titleMedium)
                Text(if (tasks.isEmpty()) "Start with one small win." else "Keep the momentum going.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer, strokeWidth = 5.dp)
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf((tasks.size - done) to "Remaining", done to "Completed", tasks.size to "Total").forEach { (value, label) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

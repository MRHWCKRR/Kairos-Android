package com.kairos.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosBoard
import com.kairos.app.data.models.KairosSection
import com.kairos.app.data.models.KairosTask
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.theme.BgCard
import com.kairos.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: MainViewModel = viewModel()) {
    val plan by viewModel.plan.collectAsState()
    val boards = plan?.boards?.filter { !it.archived } ?: emptyList()

    var showAddBoardDialog by remember { mutableStateOf(false) }
    var boardToRename by remember { mutableStateOf<KairosBoard?>(null) }
    var sectionToRename by remember { mutableStateOf<KairosSection?>(null) }
    var taskToRename by remember { mutableStateOf<KairosTask?>(null) }
    
    var boardIdForNewSection by remember { mutableStateOf<String?>(null) }
    var sectionIdForNewTask by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBoardDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Board")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (boards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "No boards yet — create one to get started.", color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp)
            ) {
                item {
                    Text(
                        text = "Routines & Tasks",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Manage your master list. AI plans will populate here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
                    )
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
                        onTaskArchive = { viewModel.archiveTask(it.id) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Dialogs
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
    onTaskArchive: (KairosTask) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = board.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Board Options", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("Archive") }, onClick = { showMenu = false; onArchive() })
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            board.sections.filter { !it.archived }.forEach { section ->
                SectionBlock(
                    section = section,
                    onRename = { onSectionRename(section) },
                    onArchive = { onSectionArchive(section) },
                    onAddTask = { onAddTask(section) },
                    onTaskToggle = onTaskToggle,
                    onTaskRename = onTaskRename,
                    onTaskArchive = onTaskArchive
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            TextButton(onClick = onAddSection) {
                Text("+ Add Section", color = MaterialTheme.colorScheme.primary)
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
    onTaskArchive: (KairosTask) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Section Options", tint = TextMuted)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { showMenu = false; onArchive() })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        section.tasks.filter { !it.archived }.forEach { task ->
            TaskItem(
                task = task,
                onToggle = { onTaskToggle(task.id, it) },
                onRename = { onTaskRename(task) },
                onArchive = { onTaskArchive(task) }
            )
        }
        
        TextButton(onClick = onAddTask, contentPadding = PaddingValues(0.dp)) {
            Text("+ Add Task", color = TextMuted, fontSize = 14.sp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.completed,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = Color.White.copy(alpha = 0.6f)
            )
        )
        
        Text(
            text = task.title,
            modifier = Modifier.weight(1f).clickable { onRename() },
            color = if (task.completed) TextMuted else Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        
        IconButton(onClick = onArchive, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Archive Task", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

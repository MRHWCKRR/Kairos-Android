package com.kairos.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.*
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.utils.ConfettiCannon

@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel()) {
    val plan by viewModel.plan.collectAsState()
    val profile by viewModel.profile.collectAsState()
    
    var showConfetti by remember { mutableStateOf(false) }
    
    val activeSection = remember(plan) {
        plan?.boards?.filter { !it.archived }
            ?.flatMap { it.sections }
            ?.firstOrNull { section -> 
                !section.archived && section.tasks.any { !it.archived && !it.completed } 
            }
    }

    // Trigger confetti if Focus Mode was active and now is null (all completed)
    var wasFocusActive by remember { mutableStateOf(false) }
    LaunchedEffect(activeSection) {
        if (activeSection == null && wasFocusActive && profile.settings.appearance.confetti) {
            showConfetti = true
        }
        wasFocusActive = activeSection != null
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp)
            ) {
                item {
                    FocusTimerWidget(viewModel)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    MiniGoalsWidget(profile)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    RoutineStatsCard(plan = plan)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                if (activeSection != null) {
                    item {
                        Text(
                            text = "Focus Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    item {
                        FocusModeCard(
                            section = activeSection,
                            onTaskToggle = { taskId, completed ->
                                viewModel.toggleTask(taskId, completed)
                            }
                        )
                    }
                } else {
                    item {
                        AllCaughtUpView()
                    }
                }
            }
            
            ConfettiCannon(
                trigger = showConfetti,
                onFinished = { showConfetti = false }
            )
        }
    }
}

@Composable
fun FocusTimerWidget(viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Focus Timer", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = formatHMS(viewModel.focusSecondsActive),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = if (viewModel.focusTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!viewModel.focusTimerRunning) {
                    Button(
                        onClick = { viewModel.startFocusTimer() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Start")
                    }
                } else {
                    Button(
                        onClick = { viewModel.pauseFocusTimer() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Pause")
                    }
                }
                
                OutlinedButton(
                    onClick = { viewModel.stopAndLogFocus() },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Stop & Log")
                }
            }
        }
    }
}

@Composable
fun MiniGoalsWidget(profile: KairosUserProfile) {
    val goalIds = profile.achievements.goals.filterNotNull().take(3)
    
    if (goalIds.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Active Goals", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            goalIds.forEach { id ->
                val def = KAIROS_ACHIEVEMENTS.find { it.id == id }
                if (def != null) {
                    val unlocked = profile.achievements.unlocked.containsKey(id)
                    val progress = when (def.type) {
                        "focus_seconds" -> profile.focusData.totalSeconds.toFloat() / def.threshold
                        "tasks_completed" -> profile.achievements.lifetimeTasksCompleted.toFloat() / def.threshold
                        else -> 0f
                    }
                    val pct = if (unlocked) 100 else (progress * 100).toInt().coerceIn(0, 100)

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "${def.icon} ${def.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "$pct%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineStatsCard(plan: KairosPlan?) {
    val stats = remember(plan) {
        var total = 0
        var completed = 0
        plan?.boards?.filter { !it.archived }?.forEach { board ->
            board.sections.filter { !it.archived }.forEach { section ->
                section.tasks.filter { !it.archived }.forEach { task ->
                    total++
                    if (task.completed) completed++
                }
            }
        }
        val pct = if (total == 0) 0 else (completed * 100) / total
        Triple(pct, completed, total)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Routine Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stats.first}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${stats.second} / ${stats.third} tasks completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { stats.first / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun FocusModeCard(
    section: KairosSection,
    onTaskToggle: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            section.tasks.filter { !it.archived }.forEach { task ->
                RoutineTaskItem(
                    task = task,
                    onToggle = { onTaskToggle(task.id, it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RoutineTaskItem(
    task: KairosTask,
    onToggle: (Boolean) -> Unit
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
                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = task.title,
            color = if (task.completed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

@Composable
fun AllCaughtUpView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✨",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You're all caught up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Take a break or plan your next routine.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedButton(
            onClick = { /* Nav to tasks or something */ },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(50.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Board")
        }
    }
}

private fun formatHMS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

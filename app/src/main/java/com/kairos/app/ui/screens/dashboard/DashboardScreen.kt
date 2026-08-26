package com.kairos.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.kairos.app.ui.components.PremiumButton
import com.kairos.app.ui.components.PremiumCard
import com.kairos.app.ui.components.PremiumSecondaryButton
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = profile.settings.profile.displayName.ifBlank { "Achiever" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(36.dp))
            }

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
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
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
            
            // Final spacing to clear the floating dock
            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
        
        ConfettiCannon(
            trigger = showConfetti,
            onFinished = { showConfetti = false }
        )
    }
}

@Composable
fun FocusTimerWidget(viewModel: MainViewModel) {
    val isRunning = viewModel.focusTimerRunning
    
    PremiumCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isRunning) "FOCUS ACTIVE" else "TIMER READY",
                style = MaterialTheme.typography.labelLarge,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = formatHMS(viewModel.focusSecondsActive),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp),
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isRunning) {
                    PremiumButton(
                        onClick = { viewModel.startFocusTimer() },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Focus", fontWeight = FontWeight.Bold)
                    }
                } else {
                    PremiumSecondaryButton(
                        onClick = { viewModel.pauseFocusTimer() },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Pause, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pause", fontWeight = FontWeight.Bold)
                    }
                }
                
                PremiumSecondaryButton(
                    onClick = { viewModel.stopAndLogFocus() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun MiniGoalsWidget(profile: KairosUserProfile) {
    val goalIds = profile.achievements.goals.filterNotNull().take(3)
    
    if (goalIds.isEmpty()) return

    PremiumCard {
        Column {
            Text(
                text = "Active Goals", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(20.dp))
            
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

                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "${def.icon} ${def.name}", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "$pct%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
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

    PremiumCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Routine Stats",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stats.first}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${stats.second} of ${stats.third} tasks completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
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
    PremiumCard {
        Column {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            section.tasks.filter { !it.archived }.forEach { task ->
                RoutineTaskItem(
                    task = task,
                    onToggle = { onTaskToggle(task.id, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun RoutineTaskItem(
    task: KairosTask,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { onToggle(!task.completed) },
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
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
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = task.title,
                color = if (task.completed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}

@Composable
fun AllCaughtUpView() {
    PremiumCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "✨",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "You're all caught up!",
                style = MaterialTheme.typography.titleLarge,
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
            
            PremiumButton(
                onClick = { /* Nav to tasks */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Board", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatHMS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}

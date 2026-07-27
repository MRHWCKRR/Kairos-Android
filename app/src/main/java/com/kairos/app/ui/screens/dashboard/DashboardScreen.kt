package com.kairos.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.models.KairosSection
import com.kairos.app.data.models.KairosTask
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.theme.BgCard
import com.kairos.app.ui.theme.TextMuted

@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel()) {
    val plan by viewModel.plan.collectAsState()
    
    val activeSection = remember(plan) {
        plan?.boards?.filter { !it.archived }
            ?.flatMap { it.sections }
            ?.firstOrNull { section -> 
                !section.archived && section.tasks.any { !it.archived && !it.completed } 
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp)
        ) {
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
        colors = CardDefaults.cardColors(containerColor = BgCard)
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
                    color = Color.White
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
                color = TextMuted
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { stats.first / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.1f),
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
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
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
                uncheckedColor = Color.White.copy(alpha = 0.6f)
            )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = task.title,
            color = if (task.completed) TextMuted else Color.White,
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
            text = "🎉",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You're all caught up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Take a break or plan your next routine.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

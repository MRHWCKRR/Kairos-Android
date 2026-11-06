package com.kairos.app.ui.screens.dashboard

import com.kairos.app.ui.components.KairosTextButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.*
import com.kairos.app.ui.components.*
import com.kairos.app.ui.navigation.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel(), onNavigate: (String) -> Unit = {}) {
    val plan by viewModel.plan.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val zone = remember(profile.settings.profile.timezone) {
        runCatching { ZoneId.of(profile.settings.profile.timezone) }.getOrDefault(ZoneId.systemDefault())
    }
    val now by produceState(ZonedDateTime.now(zone), zone) {
        while (true) { value = ZonedDateTime.now(zone); delay(60_000) }
    }
    HomeContent(
        plan, profile, now, viewModel.focusSecondsActive, viewModel.focusTimerRunning,
        onStart = { viewModel.startFocusTimer() }, onPause = { viewModel.pauseFocusTimer() },
        onStop = { viewModel.stopAndLogFocus() }, onNavigate = onNavigate,
        onToggleTask = { id, completed -> viewModel.toggleTask(id, completed) }
    )
}

/** The live Home layout is also used for previews and screenshot verification. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    plan: KairosPlan?, profile: KairosUserProfile, now: ZonedDateTime,
    focusSeconds: Long, focusRunning: Boolean,
    onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit,
    onNavigate: (String) -> Unit, onToggleTask: (String, Boolean) -> Unit
) {
    val tasks = remember(plan) { plan?.boards?.filter { !it.archived }?.flatMap { board ->
        board.sections.filter { !it.archived }.flatMap { section -> section.tasks.filter { !it.archived } }
    }.orEmpty() }
    val completed = tasks.count { it.completed }
    val today = now.toLocalDate()
    val todayTasks = tasks.filter { !it.completed && it.date == today.toString() }
    val overdue = tasks.filter { task -> !task.completed && task.date?.let { runCatching { LocalDate.parse(it).isBefore(today) }.getOrDefault(false) } == true }
    val unscheduled = tasks.count { !it.completed && it.date.isNullOrBlank() }
    val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Welcome back" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(greeting.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text(profile.settings.profile.displayName.ifBlank { "Welcome to Kairos" },
                    style = MaterialTheme.typography.displaySmall, letterSpacing = (-1.2).sp)
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            KairosCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    KairosTextButton(onClick = { onNavigate("plan") }) { Text("Open schedule", color = MaterialTheme.colorScheme.secondary) }
                }
                Text("${todayTasks.size} planned · ${overdue.size} overdue · $unscheduled unscheduled",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val visible = (overdue.take(2) + todayTasks).take(3)
                if (visible.isEmpty()) {
                    Text("Nothing scheduled today", style = MaterialTheme.typography.titleSmall)
                    Text(if (unscheduled > 0) "You have $unscheduled tasks ready to plan." else "Make room for what matters to you.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                } else visible.forEach { task ->
                    KairosTaskRow(task.title, task.completed, { onToggleTask(task.id, it) }, { onNavigate("tasks") })
                }
            }
        }
        item { FocusSessionCard(focusSeconds, focusRunning, onStart, onPause, onStop) }
        item {
            KairosCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your momentum", style = MaterialTheme.typography.titleLarge)
                        Text("Small wins compound.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onNavigate("achievements") }) { Icon(Icons.Default.NorthEast, "Open goals", tint = MaterialTheme.colorScheme.secondary) }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeMetric(if (tasks.isEmpty()) "0%" else "${completed * 100 / tasks.size}%", "Tasks complete", Modifier.weight(1f))
                    HomeMetric(completed.toString(), "Completed", Modifier.weight(1f))
                    HomeMetric(profile.achievements.unlocked.size.toString(), "Unlocked", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(progress = { if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size },
                    modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Workspace", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Triple("Boards", Icons.Default.Checklist, "tasks"), Triple("Calendar", Icons.Default.CalendarMonth, "plan"), Triple("Goals", Icons.Default.EmojiEvents, "achievements")).forEach { (label, icon, route) ->
                        OutlinedCard(onClick = { onNavigate(route) }, modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FocusSessionCard(seconds: Long, running: Boolean, onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit) {
    KairosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Timer, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("FOCUS SESSION", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        BoxWithConstraints {
            val compact = maxWidth < 310.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            val timer: @Composable () -> Unit = {
                Column {
                    Text(formatHMS(seconds), style = MaterialTheme.typography.displayLarge, letterSpacing = (-1.5).sp)
                    Text(if (running) "Stay in the zone." else "Ready when you are.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val controls: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledIconButton(onClick = if (running) onPause else onStart, modifier = Modifier.size(50.dp), shape = CircleShape) {
                        Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, if (running) "Pause focus" else "Start focus")
                    }
                    if (running || seconds > 0) OutlinedIconButton(onClick = onStop, modifier = Modifier.size(48.dp), shape = CircleShape) { Icon(Icons.Default.Stop, "Stop and log focus") }
                }
            }
            if (compact) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { timer(); controls() }
            else Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { timer() }; controls() }
        }
    }
}

fun formatHMS(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}

package com.kairos.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.models.KairosTask
import com.kairos.app.data.models.KairosScheduleEvent
import com.kairos.app.ui.components.KairosCard
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.screens.schedule.ScheduleScreen
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

private enum class ScheduleRange(val label: String, val days: Int) {
    DAY("Day", 1), THREE_DAYS("3 days", 3), WEEK("Week", 7), MONTH("Month", 42)
}

private fun scheduledTasks(plan: KairosPlan?, date: LocalDate): List<KairosTask> =
    plan?.boards?.filterNot { it.archived }?.flatMap { it.sections }
        ?.filterNot { it.archived }?.flatMap { it.tasks }
        ?.filter { it.date == date.toString() && !it.archived }.orEmpty()

private fun scheduledEvents(plan: KairosPlan?, date: LocalDate): List<KairosScheduleEvent> {
    val day = date.dayOfWeek.value % 7
    val previous = (day + 6) % 7
    return plan?.scheduleEvents.orEmpty().mapNotNull { event ->
        val start = event.start.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val end = event.end.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        when {
            event.day == day -> if (end <= start) event.copy(end = "24:00") else event
            event.day == previous && end < start && end > 0 -> event.copy(start = "00:00")
            else -> null
        }
    }.sortedBy { it.start }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(mainViewModel: MainViewModel, viewModel: CalendarViewModel = viewModel()) {
    val plan by mainViewModel.plan.collectAsState()
    var selectedKey by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedKey)
    var range by rememberSaveable { mutableStateOf(ScheduleRange.DAY) }
    var agenda by rememberSaveable { mutableStateOf(true) }
    var showCompleted by rememberSaveable { mutableStateOf(true) }
    var showEvents by rememberSaveable { mutableStateOf(true) }
    var filters by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }
    val start = when (range) {
        ScheduleRange.MONTH -> YearMonth.from(selected).atDay(1).let { it.minusDays((it.dayOfWeek.value % 7).toLong()) }
        ScheduleRange.WEEK -> selected.minusDays((selected.dayOfWeek.value % 7).toLong())
        else -> selected
    }
    val dates = List(range.days) { start.plusDays(it.toLong()) }
    fun navigate(direction: Int) {
        selectedKey = (if (range == ScheduleRange.MONTH) selected.plusMonths(direction.toLong())
            else selected.plusDays((direction * range.days).toLong())).toString()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedule", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { agenda = !agenda }) {
                Icon(if (agenda) Icons.Default.CalendarMonth else Icons.AutoMirrored.Filled.List,
                    if (agenda) "Show fixed-event timeline" else "Show agenda", tint = MaterialTheme.colorScheme.secondary)
            }
            Box {
                IconButton(onClick = { filters = true }) { Icon(Icons.Default.FilterList, "Schedule filters", tint = MaterialTheme.colorScheme.secondary) }
                DropdownMenu(expanded = filters, onDismissRequest = { filters = false }) {
                    DropdownMenuItem(text = { Text("Completed tasks") }, onClick = { showCompleted = !showCompleted }, trailingIcon = { Checkbox(showCompleted, { showCompleted = it }) })
                    DropdownMenuItem(text = { Text("Fixed events") }, onClick = { showEvents = !showEvents }, trailingIcon = { Checkbox(showEvents, { showEvents = it }) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { datePicker = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(selected.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selectedKey = LocalDate.now().toString() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Today") }
            IconButton(onClick = { navigate(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${range.label.lowercase()}") }
            IconButton(onClick = { navigate(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${range.label.lowercase()}") }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            ScheduleRange.entries.forEachIndexed { index, item ->
                SegmentedButton(selected = range == item, onClick = { range = item }, shape = SegmentedButtonDefaults.itemShape(index, ScheduleRange.entries.size), icon = {},
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.surface, activeContentColor = MaterialTheme.colorScheme.secondary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant, inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(item.label, maxLines = 1)
                }
            }
        }
        if (mainViewModel.isInitializing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (plan == null && mainViewModel.errorMessage != null) {
            Text(mainViewModel.errorMessage ?: "Schedule unavailable", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        } else if (range != ScheduleRange.MONTH && !agenda) {
            ScheduleScreen(viewModel = mainViewModel, dates = dates, showEvents = showEvents, onDayDetails = { detailDate = it })
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (range == ScheduleRange.MONTH) {
                    CalendarGrid(YearMonth.from(selected), plan, selected, showCompleted, showEvents) { selectedKey = it.toString() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.format(DateTimeFormatter.ofPattern("MMM d")), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { range = ScheduleRange.DAY }) { Text("Open day") }
                    }
                }
                (if (range == ScheduleRange.MONTH) listOf(selected) else dates).forEach { date ->
                    if (range != ScheduleRange.MONTH) Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DayAgenda(date, plan, showCompleted, showEvents, mainViewModel) { detailDate = date }
                }
                plan?.dayInsights?.get(selected.toString())?.takeIf { it.isNotBlank() }?.let { insight ->
                    KairosCard { Text("Daily insight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Text(insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(onClick = { detailDate = selected }, modifier = Modifier.align(Alignment.Start)) { Text("Day details & insight") }
            }
        }
    }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { selectedKey = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; datePicker = false }) { Text("Done") }
        }, dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
    detailDate?.let { date ->
        ModalBottomSheet(onDismissRequest = { detailDate = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DayDetailContent(date, plan, mainViewModel, viewModel) }
        }
    }
}

@Composable
private fun DayAgenda(date: LocalDate, plan: KairosPlan?, showCompleted: Boolean, showEvents: Boolean, mainViewModel: MainViewModel, onDetails: () -> Unit) {
    val tasks = scheduledTasks(plan, date).filter { showCompleted || !it.completed }
    val events = if (showEvents) scheduledEvents(plan, date) else emptyList()
    KairosCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        if (tasks.isEmpty() && events.isEmpty()) Text("Nothing scheduled for this day.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tasks.forEach { task ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(task.completed, { mainViewModel.toggleTask(task.id, it) })
                Text(task.title, Modifier.weight(1f).clickable(onClick = onDetails).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        events.forEach { event ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(event.title, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
                Text("${event.start}–${event.end}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CalendarGrid(currentMonth: YearMonth, plan: KairosPlan?, selectedDate: LocalDate = LocalDate.now(), showCompleted: Boolean = true, showEvents: Boolean = true, onDateClick: (LocalDate) -> Unit) {
    val first = currentMonth.atDay(1).let { it.minusDays((it.dayOfWeek.value % 7).toLong()) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { day ->
                    val date = first.plusDays((week * 7 + day).toLong())
                    val selected = date == selectedDate
                    val count = scheduledTasks(plan, date).count { showCompleted || !it.completed } + if (showEvents) scheduledEvents(plan, date).size else 0
                    Column(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onDateClick(date) }.heightIn(min = 48.dp).semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))}, $count items" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else if (YearMonth.from(date) != currentMonth) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Medium)
                        Text(if (count > 0) count.toString() else " ", fontSize = 9.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun DayDetailContent(
    date: LocalDate,
    plan: KairosPlan?,
    mainViewModel: MainViewModel,
    viewModel: CalendarViewModel
) {
    val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val tasksForDay = scheduledTasks(plan, date)
    
    val insight = plan?.dayInsights?.get(dateKey)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Tasks Section
        Text(
            text = "Tasks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        
        if (tasksForDay.isEmpty()) {
            Text(
                text = "No tasks scheduled for this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            tasksForDay.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.completed,
                        onCheckedChange = { mainViewModel.toggleTask(task.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = task.title,
                        color = if (task.completed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AI Insight Section
        Text(
            text = "Daily insight",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            if (viewModel.isGeneratingInsight) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.Center))
            } else if (insight != null) {
                Text(text = insight, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = viewModel.insightError ?: "No insight yet.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.generateInsight(date, plan) { result ->
                    mainViewModel.updateDayInsight(dateKey, result)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isGeneratingInsight && tasksForDay.isNotEmpty(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (insight != null) "Regenerate Insight" else "Generate Insight")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

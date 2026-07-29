package com.kairos.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.models.KairosTask
import com.kairos.app.ui.navigation.MainViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    mainViewModel: MainViewModel,
    viewModel: CalendarViewModel = viewModel()
) {
    val plan by mainViewModel.plan.collectAsState()
    val currentMonth = viewModel.currentMonth
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Calendar",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Manage your routines and assignment dates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // Month Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Row {
                    IconButton(onClick = { viewModel.onPreviousMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { viewModel.onNextMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekday Labels
            Row(modifier = Modifier.fillMaxWidth()) {
                val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                days.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            CalendarGrid(
                currentMonth = currentMonth,
                plan = plan,
                onDateClick = {
                    viewModel.selectedDate = it
                    showBottomSheet = true
                }
            )
        }
    }

    if (showBottomSheet && viewModel.selectedDate != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DayDetailContent(
                date = viewModel.selectedDate!!,
                plan = plan,
                mainViewModel = mainViewModel,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    plan: KairosPlan?,
    onDateClick: (LocalDate) -> Unit
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // 0=Sun, 1=Mon...
    val daysInMonth = currentMonth.lengthOfMonth()
    
    val today = LocalDate.now()
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Padding for previous month
        items(firstDayOfWeek) {
            Box(modifier = Modifier.aspectRatio(1f))
        }

        // Days of current month
        items(daysInMonth) { index ->
            val day = index + 1
            val date = currentMonth.atDay(day)
            val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val taskCount = plan?.boards?.flatMap { it.sections }?.flatMap { it.tasks }
                ?.count { it.date == dateKey && !it.archived } ?: 0
            
            val isToday = date == today

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onDateClick(date) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = day.toString(),
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                    if (taskCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
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
    val tasksForDay = plan?.boards?.flatMap { it.sections }?.flatMap { it.tasks }
        ?.filter { it.date == dateKey && !it.archived } ?: emptyList()
    
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
            color = MaterialTheme.colorScheme.primary,
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
            text = "AI Coach Insight",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
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

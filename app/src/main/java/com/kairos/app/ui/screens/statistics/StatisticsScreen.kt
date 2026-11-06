package com.kairos.app.ui.screens.statistics

import com.kairos.app.ui.icons.KairosIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun StatisticsScreen(
    mainViewModel: MainViewModel,
    viewModel: StatisticsViewModel = viewModel()
) {
    val profile by mainViewModel.profile.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Your progress",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text("See how consistently you are showing up.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(18.dp))

            // Range Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatsRange.entries.forEach { range ->
                    val selected = viewModel.statsRange == range
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .clickable { viewModel.statsRange = range },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Focus Time Chart
            ChartCard(
                title = "Focus Time",
                data = viewModel.getChartData(profile.focusData, isFocusTime = true),
                valueFormatter = { formatSecondsToHMS(it.toLong()) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Tasks Completed Chart
            ChartCard(
                title = "Tasks Completed",
                data = viewModel.getChartData(profile.focusData, isFocusTime = false),
                valueFormatter = { "${it.toInt()} tasks" }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Lifetime totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    SummaryCard("Total Focus Logged", formatSecondsToHMS(profile.focusData.totalSeconds))
                    SummaryCard("Longest Session", formatSecondsToHMS(profile.focusData.longestSessionSeconds))
                    SummaryCard("Tasks Completed", "${profile.achievements.lifetimeTasksCompleted}")
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ChartCard(
    title: String,
    data: List<StatisticsViewModel.ChartDataPoint>,
    valueFormatter: (Float) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (title == "Focus Time") "Minutes logged" else "Completed each period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (title == "Focus Time") KairosIcons.Timer else KairosIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(12.dp))
            BarChart(data = data, valueFormatter = valueFormatter)
        }
    }
}

@Composable
fun BarChart(
    data: List<StatisticsViewModel.ChartDataPoint>,
    valueFormatter: (Float) -> String
) {
    val maxVal = remember(data) { data.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f }
    var selectedIndex by remember(data) { mutableStateOf(-1) }

    Column {
        // Tooltip area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selectedIndex != -1) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = valueFormatter(data[selectedIndex].value),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else {
                Text(
                    text = "Tap a bar to see details",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { index, point ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${point.label}: ${valueFormatter(point.value)}" }
                        .clickable { selectedIndex = if (selectedIndex == index) -1 else index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // The Bar Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((point.value / maxVal).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (selectedIndex == index) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = point.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selectedIndex == index) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCard(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(when (label) {
            "Total Focus Logged" -> KairosIcons.Timer
            "Longest Session" -> KairosIcons.LocalFireDepartment
            else -> KairosIcons.CheckCircle
        }, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun formatSecondsToHMS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}


package com.kairos.app.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.kairos.app.ui.theme.BgCard
import com.kairos.app.ui.theme.TextMuted

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
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Statistics",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Range Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
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
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.statsRange = range },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selected) Color.White else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Focus Time Chart
            ChartCard(
                title = "Focus Time",
                data = viewModel.getChartData(profile.focusData, isFocusTime = true),
                valueFormatter = { formatSecondsToHMS(it.toLong()) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tasks Completed Chart
            ChartCard(
                title = "Tasks Completed",
                data = viewModel.getChartData(profile.focusData, isFocusTime = false),
                valueFormatter = { "${it.toInt()} tasks" }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Summary Totals
            Text(text = "Lifetime Totals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            
            SummaryCard(label = "Total Focus Logged", value = formatSecondsToHMS(profile.focusData.totalSeconds))
            Spacer(modifier = Modifier.height(12.dp))
            SummaryCard(label = "Longest Session", value = formatSecondsToHMS(profile.focusData.longestSessionSeconds))
            Spacer(modifier = Modifier.height(12.dp))
            SummaryCard(label = "Total Tasks Completed", value = "${profile.achievements.lifetimeTasksCompleted}")

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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            
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
                    color = TextMuted,
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
                                .fillMaxHeight((point.value / maxVal).coerceAtLeast(0.05f))
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
                        color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label, 
                style = MaterialTheme.typography.bodyMedium, 
                color = TextMuted,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = value, 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatSecondsToHMS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}

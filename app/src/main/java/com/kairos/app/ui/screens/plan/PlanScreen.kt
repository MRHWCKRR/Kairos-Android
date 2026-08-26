package com.kairos.app.ui.screens.plan

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.screens.calendar.CalendarScreen
import com.kairos.app.ui.screens.schedule.ScheduleScreen

enum class PlanTab { CALENDAR, SCHEDULE }

@Composable
fun PlanScreen(mainViewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(PlanTab.CALENDAR) }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- CUSTOM SEGMENTED TOGGLE ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlanToggleItem(
                        label = "Calendar",
                        selected = selectedTab == PlanTab.CALENDAR,
                        onClick = { selectedTab = PlanTab.CALENDAR },
                        modifier = Modifier.weight(1f)
                    )
                    PlanToggleItem(
                        label = "Schedule",
                        selected = selectedTab == PlanTab.SCHEDULE,
                        onClick = { selectedTab = PlanTab.SCHEDULE },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- CONTENT AREA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 100.dp) // Spacing for floating dock
        ) {
            if (selectedTab == PlanTab.CALENDAR) {
                CalendarScreen(mainViewModel = mainViewModel)
            } else {
                ScheduleScreen(viewModel = mainViewModel)
            }
        }
    }
}

@Composable
fun PlanToggleItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (selected) 4.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
    }
}

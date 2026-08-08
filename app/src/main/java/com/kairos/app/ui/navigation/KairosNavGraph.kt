package com.kairos.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class KairosDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : KairosDestination("dashboard", "Dashboard", Icons.Default.Home)
    data object Discovery : KairosDestination("discovery", "Discovery", Icons.Default.Explore)
    data object AiHelper : KairosDestination("ai_helper", "AI Coach", Icons.Default.AutoAwesome)
    data object Tasks : KairosDestination("tasks", "Tasks", Icons.Default.CheckCircle)
    data object Schedule : KairosDestination("schedule", "Schedule", Icons.Default.Schedule)
    data object Calendar : KairosDestination("calendar", "Calendar", Icons.Default.CalendarMonth)
    data object Achievements : KairosDestination("achievements", "Achievements", Icons.Default.EmojiEvents)
    data object Statistics : KairosDestination("statistics", "Statistics", Icons.Default.BarChart)
    data object Settings : KairosDestination("settings", "Settings", Icons.Default.Settings)

    companion object {
        val all = listOf(Dashboard, Discovery, AiHelper, Tasks, Schedule, Calendar, Achievements, Statistics, Settings)
    }
}

package com.kairos.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class KairosDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : KairosDestination("dashboard", "Home", Icons.Default.Home)
    data object AiHelper : KairosDestination("ai_helper", "AI", Icons.Default.ChatBubble)
    data object Tasks : KairosDestination("tasks", "Boards", Icons.Default.Checklist)
    data object Plan : KairosDestination("plan", "Schedule", Icons.Default.CalendarMonth)
    data object Schedule : KairosDestination("schedule", "Schedule", Icons.Default.Schedule)
    data object Calendar : KairosDestination("calendar", "Calendar", Icons.Default.CalendarMonth)
    data object Achievements : KairosDestination("achievements", "Goals", Icons.Default.EmojiEvents)
    data object Statistics : KairosDestination("statistics", "Statistics", Icons.Default.BarChart)
    data object Settings : KairosDestination("settings", "Settings", Icons.Default.Settings)
}

object KairosDestinations {
    val all = listOf(
        KairosDestination.Dashboard,
        KairosDestination.Tasks,
        KairosDestination.AiHelper,
        KairosDestination.Plan,
        KairosDestination.Achievements
    )
    val secondary = listOf(
        KairosDestination.Statistics,
        KairosDestination.Settings
    )
}

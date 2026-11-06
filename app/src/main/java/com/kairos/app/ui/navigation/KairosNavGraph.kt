package com.kairos.app.ui.navigation

import com.kairos.app.ui.icons.KairosIcons

import androidx.compose.ui.graphics.vector.ImageVector

sealed class KairosDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : KairosDestination("dashboard", "Home", KairosIcons.Home)
    data object AiHelper : KairosDestination("ai_helper", "AI", KairosIcons.ChatBubble)
    data object Tasks : KairosDestination("tasks", "Boards", KairosIcons.Boards)
    data object Plan : KairosDestination("plan", "Schedule", KairosIcons.CalendarMonth)
    data object Schedule : KairosDestination("schedule", "Schedule", KairosIcons.Schedule)
    data object Calendar : KairosDestination("calendar", "Calendar", KairosIcons.CalendarMonth)
    data object Achievements : KairosDestination("achievements", "Goals", KairosIcons.EmojiEvents)
    data object Statistics : KairosDestination("statistics", "Statistics", KairosIcons.BarChart)
    data object Settings : KairosDestination("settings", "Settings", KairosIcons.Settings)
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


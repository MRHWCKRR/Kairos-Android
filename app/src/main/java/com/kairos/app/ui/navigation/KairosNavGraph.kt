package com.kairos.app.ui.navigation

sealed class KairosDestination(val route: String, val label: String) {
    data object Dashboard : KairosDestination("dashboard", "Dashboard")
    data object AiHelper : KairosDestination("ai_helper", "AI Helper")
    data object Tasks : KairosDestination("tasks", "Tasks")
    data object Schedule : KairosDestination("schedule", "Schedule")
    data object Calendar : KairosDestination("calendar", "Calendar")
    data object Achievements : KairosDestination("achievements", "Achievements")
    data object Statistics : KairosDestination("statistics", "Statistics")
    data object Settings : KairosDestination("settings", "Settings")

    companion object {
        val all = listOf(Dashboard, AiHelper, Tasks, Schedule, Calendar, Achievements, Statistics, Settings)
    }
}
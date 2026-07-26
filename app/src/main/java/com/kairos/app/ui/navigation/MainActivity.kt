package com.kairos.app.ui.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.kairos.app.ui.screens.achievements.AchievementsScreen
import com.kairos.app.ui.screens.ai.AiHelperScreen
import com.kairos.app.ui.screens.calendar.CalendarScreen
import com.kairos.app.ui.screens.dashboard.DashboardScreen
import com.kairos.app.ui.screens.schedule.ScheduleScreen
import com.kairos.app.ui.screens.settings.SettingsScreen
import com.kairos.app.ui.screens.statistics.StatisticsScreen
import com.kairos.app.ui.screens.tasks.TasksScreen
import com.kairos.app.ui.theme.KairosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KairosTheme {
                KairosApp()
            }
        }
    }
}

@Composable
fun KairosApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { KairosBottomNav(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KairosDestination.Dashboard.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding)
        ) {
            composable(KairosDestination.Dashboard.route) { DashboardScreen() }
            composable(KairosDestination.AiHelper.route) { AiHelperScreen() }
            composable(KairosDestination.Tasks.route) { TasksScreen() }
            composable(KairosDestination.Schedule.route) { ScheduleScreen() }
            composable(KairosDestination.Calendar.route) { CalendarScreen() }
            composable(KairosDestination.Achievements.route) { AchievementsScreen() }
            composable(KairosDestination.Statistics.route) { StatisticsScreen() }
            composable(KairosDestination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
fun KairosBottomNav(navController: androidx.navigation.NavHostController) {
    val icons = mapOf(
        KairosDestination.Dashboard to Icons.Default.Home,
        KairosDestination.AiHelper to Icons.Default.AutoAwesome,
        KairosDestination.Tasks to Icons.Default.CheckCircle,
        KairosDestination.Schedule to Icons.Default.Schedule,
        KairosDestination.Calendar to Icons.Default.CalendarMonth,
        KairosDestination.Achievements to Icons.Default.EmojiEvents,
        KairosDestination.Statistics to Icons.Default.BarChart,
        KairosDestination.Settings to Icons.Default.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 8 tabs is too many for a bottom bar to breathe — this uses a scrollable
    // NavigationBar so nothing gets cramped; swap for a NavigationDrawer
    // (closer to your web sidebar) later if you'd rather match that exactly.
    NavigationBar {
        KairosDestination.all.forEach { dest ->
            NavigationBarItem(
                icon = { Icon(icons[dest]!!, contentDescription = dest.label) },
                label = { Text(dest.label, maxLines = 1) },
                selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
package com.kairos.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kairos.app.ui.screens.login.LoginScreen
import com.kairos.app.ui.theme.KairosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val profile by viewModel.profile.collectAsState()
            
            KairosTheme(appearance = profile.settings.appearance) {
                KairosApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val user by viewModel.user.collectAsState()
    val errorMessage = viewModel.errorMessage

    if (user == null) {
        LoginScreen()
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Kairos", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = { KairosBottomNav(navController) }
        ) { innerPadding ->
            if (errorMessage != null) {
                SyncErrorScreen(
                    errorMessage = errorMessage,
                    modifier = Modifier.padding(innerPadding),
                    onSignOut = { viewModel.signOut() }
                )
            } else {
                NavHost(
                    navController = navController,
                    startDestination = KairosDestination.Dashboard.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(KairosDestination.Dashboard.route) { DashboardScreen(viewModel) }
                    composable(KairosDestination.AiHelper.route) { AiHelperScreen(viewModel) }
                    composable(KairosDestination.Tasks.route) { TasksScreen(viewModel) }
                    composable(KairosDestination.Schedule.route) { ScheduleScreen(viewModel) }
                    composable(KairosDestination.Calendar.route) { CalendarScreen(viewModel) }
                    composable(KairosDestination.Achievements.route) { AchievementsScreen(viewModel) }
                    composable(KairosDestination.Statistics.route) { StatisticsScreen(viewModel) }
                    composable(KairosDestination.Settings.route) { SettingsScreen() }
                }
            }
        }
    }
}

@Composable
fun SyncErrorScreen(
    errorMessage: String,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val url = remember(errorMessage) {
        val startIndex = errorMessage.indexOf("https://")
        if (startIndex != -1) {
            errorMessage.substring(startIndex).split(" ", "\n").firstOrNull()
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Data Sync Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (url != null) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Fix in Browser")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Out")
        }
    }
}

@Composable
fun KairosBottomNav(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        modifier = Modifier.height(64.dp)
    ) {
        KairosDestination.all.forEach { dest ->
            NavigationBarItem(
                icon = { 
                    Icon(
                        imageVector = dest.icon, 
                        contentDescription = dest.label,
                        modifier = Modifier.size(20.dp)
                    ) 
                },
                label = { 
                    Text(
                        text = dest.label, 
                        maxLines = 1, 
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    ) 
                },
                selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                alwaysShowLabel = false,
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

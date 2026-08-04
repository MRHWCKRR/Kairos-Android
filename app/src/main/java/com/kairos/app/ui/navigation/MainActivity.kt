package com.kairos.app.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.kairos.app.data.models.KairosNotification
import com.kairos.app.ui.components.ProfileImage
import com.kairos.app.ui.screens.achievements.AchievementsScreen
import com.kairos.app.ui.screens.ai.AiHelperScreen
import com.kairos.app.ui.screens.calendar.CalendarScreen
import com.kairos.app.ui.screens.dashboard.DashboardScreen
import com.kairos.app.ui.screens.schedule.ScheduleScreen
import com.kairos.app.ui.screens.settings.SettingsScreen
import com.kairos.app.ui.screens.statistics.StatisticsScreen
import com.kairos.app.ui.screens.tasks.TasksScreen
import com.kairos.app.ui.screens.login.LoginScreen
import com.kairos.app.ui.screens.splash.SplashScreen
import com.kairos.app.ui.theme.*
import com.kairos.app.utils.NotificationHelper
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val profile by mainViewModel.profile.collectAsState()
            val context = LocalContext.current
            val notificationHelper = remember { NotificationHelper(context) }

            LaunchedEffect(Unit) {
                mainViewModel.notificationEvents.collectLatest { (title, message) ->
                    notificationHelper.showNotification(title, message)
                }
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    android.util.Log.w("MainActivity", "Notification permission denied")
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // --- AMBIENT AUDIO SYNC ---
            val appearance = profile.settings.appearance
            LaunchedEffect(appearance.ambientSound, appearance.ambientVolume) {
                mainViewModel.syncAmbientAudio(context)
            }
            
            KairosTheme(appearance = profile.settings.appearance) {
                KairosApp(mainViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val user by viewModel.user.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val errorMessage = viewModel.errorMessage
    val haptics = LocalHapticFeedback.current
    
    var showNotifPanel by remember { mutableStateOf(false) }
    val unreadCount = remember(profile.notifications) { 
        profile.notifications.count { !it.read } 
    }

    Crossfade(targetState = viewModel.isInitializing, label = "main_content", animationSpec = tween(700)) { initializing ->
        if (initializing) {
            SplashScreen()
        } else {
            if (user == null) {
                LoginScreen()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // --- THEME BACKGROUND HANDLER ---
                    if (profile.settings.appearance.background != "none") {
                        if (profile.settings.appearance.background == "custom" && !profile.settings.appearance.customBackground.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.settings.appearance.customBackground,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = 0.4f
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (profile.settings.appearance.background == "mesh") {
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), Color.Transparent)
                                            )
                                        } else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                            )
                        }
                    }

                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("Kairos", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                                actions = {
                                    IconButton(onClick = { 
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showNotifPanel = true 
                                        viewModel.markNotificationsRead()
                                    }) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadCount > 0) {
                                                    Badge {
                                                        Text(text = if (unreadCount > 9) "9+" else unreadCount.toString())
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onBackground)
                                        }
                                    }
                                    
                                    var showProfileMenu by remember { mutableStateOf(false) }
                                    val userName = profile.settings.profile.displayName.ifBlank { user?.displayName ?: "User" }
                                    val userPhoto = profile.settings.profile.avatarURL.ifBlank { user?.photoUrl?.toString() ?: "" }

                                    Box {
                                        IconButton(onClick = { 
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showProfileMenu = true 
                                        }) {
                                            ProfileImage(
                                                imageUrl = userPhoto,
                                                userName = userName,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showProfileMenu,
                                            onDismissRequest = { showProfileMenu = false },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp).width(200.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ProfileImage(
                                                    imageUrl = userPhoto,
                                                    userName = userName,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                                    Text(text = userName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(text = user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                            DropdownMenuItem(
                                                text = { Text("Settings", color = MaterialTheme.colorScheme.onSurface) },
                                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showProfileMenu = false
                                                    navController.navigate(KairosDestination.Settings.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { 
                                                    val isDark = profile.settings.appearance.mode == "dark"
                                                    Text(if (isDark) "Switch to Light Mode" else "Switch to Dark Mode", color = MaterialTheme.colorScheme.onSurface) 
                                                },
                                                leadingIcon = { 
                                                    val isDark = profile.settings.appearance.mode == "dark"
                                                    Icon(
                                                        imageVector = if (isDark) Icons.Default.WbSunny else Icons.Outlined.ModeNight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    ) 
                                                },
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showProfileMenu = false
                                                    viewModel.toggleAppearanceMode()
                                                }
                                            )

                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                            DropdownMenuItem(
                                                text = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showProfileMenu = false
                                                    viewModel.signOut()
                                                }
                                            )
                                        }
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
                                onSignOut = { viewModel.signOut() },
                                onDismiss = { viewModel.clearError() }
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
                                composable(KairosDestination.Settings.route) { SettingsScreen(viewModel) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotifPanel) {
        ModalBottomSheet(
            onDismissRequest = { showNotifPanel = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NotificationPanel(
                notifications = profile.notifications,
                onDelete = { viewModel.deleteNotification(it) }
            )
        }
    }
}

@Composable
fun NotificationPanel(
    notifications: List<KairosNotification>,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(24.dp)
        )
        
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No notifications yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn {
                items(notifications) { notif ->
                    NotificationItem(notif = notif, onDelete = { onDelete(notif.id) })
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notif: KairosNotification,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(8.dp).background(if (notif.read) Color.Transparent else MaterialTheme.colorScheme.primary, CircleShape)
        )
        
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(text = notif.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(text = notif.message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = formatTimeAgo(notif.time), 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), 
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
    }
}

fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val mins = diff / 60000
    if (mins < 1) return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hrs = mins / 60
    if (hrs < 24) return "${hrs}h ago"
    val days = hrs / 24
    return "${days}d ago"
}

@Composable
fun SyncErrorScreen(
    errorMessage: String,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
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
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
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
            text = "Data Sync Warning",
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
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Dismiss & Go Back")
        }

        Spacer(modifier = Modifier.height(12.dp))

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
    val haptics = LocalHapticFeedback.current

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        modifier = Modifier.height(64.dp)
    ) {
        KairosDestination.all.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                icon = { 
                    Icon(
                        imageVector = dest.icon, 
                        contentDescription = dest.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ) 
                },
                label = { 
                    Text(
                        text = dest.label, 
                        maxLines = 1, 
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ) 
                },
                selected = selected,
                alwaysShowLabel = false,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    }
}

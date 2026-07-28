package com.kairos.app.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.kairos.app.ui.screens.achievements.AchievementsScreen
import com.kairos.app.ui.screens.ai.AiHelperScreen
import com.kairos.app.ui.screens.calendar.CalendarScreen
import com.kairos.app.ui.screens.dashboard.DashboardScreen
import com.kairos.app.ui.screens.schedule.ScheduleScreen
import com.kairos.app.ui.screens.settings.SettingsScreen
import com.kairos.app.ui.screens.statistics.StatisticsScreen
import com.kairos.app.ui.screens.tasks.TasksScreen
import com.kairos.app.ui.screens.login.LoginScreen
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

            // Handle System Notifications
            LaunchedEffect(Unit) {
                mainViewModel.notificationEvents.collectLatest { (title, message) ->
                    notificationHelper.showNotification(title, message)
                }
            }

            // Permission Request for Android 13+
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
    
    var showNotifPanel by remember { mutableStateOf(false) }
    val unreadCount = remember(profile.notifications) { 
        profile.notifications.count { !it.read } 
    }

    if (user == null) {
        LoginScreen()
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Kairos", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { 
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
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        }
                        
                        var showProfileMenu by remember { mutableStateOf(false) }
                        val userName = profile.settings.profile.displayName.ifBlank { user?.displayName ?: "User" }
                        val userPhoto = profile.settings.profile.avatarURL.ifBlank { user?.photoUrl?.toString() ?: "" }

                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                if (userPhoto.isNotEmpty()) {
                                    AsyncImage(
                                        model = userPhoto,
                                        contentDescription = "Profile",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = userName.take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false },
                                modifier = Modifier.background(BgCard)
                            ) {
                                // Header
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .width(200.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (userPhoto.isNotEmpty()) {
                                        AsyncImage(
                                            model = userPhoto,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = userName.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(text = userName, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                DropdownMenuItem(
                                    text = { Text("Settings", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = TextMuted) },
                                    onClick = {
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
                                        Text(if (isDark) "Switch to Light Mode" else "Switch to Dark Mode", color = Color.White) 
                                    },
                                    leadingIcon = { 
                                        val isDark = profile.settings.appearance.mode == "dark"
                                        Icon(
                                            imageVector = if (isDark) Icons.Default.Info else Icons.Default.Info, // Use Info as placeholder until I verify LightMode/DarkMode
                                            contentDescription = null,
                                            tint = TextMuted
                                        ) 
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        viewModel.toggleAppearanceMode()
                                    }
                                )

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                DropdownMenuItem(
                                    text = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
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
                    composable(KairosDestination.Settings.route) { SettingsScreen(viewModel) }
                }
            }
        }
    }

    if (showNotifPanel) {
        ModalBottomSheet(
            onDismissRequest = { showNotifPanel = false },
            containerColor = BgCard
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(24.dp)
        )
        
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No notifications yet.", color = TextMuted)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (notif.read) Color.Transparent else MaterialTheme.colorScheme.primary, CircleShape)
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(text = notif.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(text = notif.message, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = formatTimeAgo(notif.time), 
                color = TextMuted.copy(alpha = 0.6f), 
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(20.dp))
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

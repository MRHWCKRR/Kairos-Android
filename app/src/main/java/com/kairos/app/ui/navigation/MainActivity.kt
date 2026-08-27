package com.kairos.app.ui.navigation

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.kairos.app.data.models.KairosNotification
import com.kairos.app.ui.components.ProfileImage
import com.kairos.app.ui.screens.discovery.DiscoveryScreen
import com.kairos.app.ui.screens.achievements.AchievementsScreen
import com.kairos.app.ui.screens.ai.AiHelperScreen
import com.kairos.app.ui.screens.calendar.CalendarScreen
import com.kairos.app.ui.screens.dashboard.DashboardScreen
import com.kairos.app.ui.screens.schedule.ScheduleScreen
import com.kairos.app.ui.screens.plan.PlanScreen
import com.kairos.app.ui.screens.settings.SettingsScreen
import com.kairos.app.ui.screens.statistics.StatisticsScreen
import com.kairos.app.ui.screens.tasks.TasksScreen
import com.kairos.app.ui.screens.login.LoginScreen
import com.kairos.app.ui.screens.splash.SplashScreen
import com.kairos.app.ui.theme.*
import com.kairos.app.utils.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var incomingAction by mutableStateOf<String?>(null)
    private var incomingTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // --- 1. ABSOLUTE EDGE-TO-EDGE AUTHORITY ---
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            // FORCE DARK (White Icons) + 0 SCRIM: The definitive fix for Light Mode white bar
            navigationBarStyle = SystemBarStyle.dark(0)
        )
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        handleIntent(intent)
        
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val profile by mainViewModel.profile.collectAsState()
            val appearance = profile.settings.appearance

            // REACTIVE STABILIZATION: Keep the system bars behaving
            LaunchedEffect(appearance.mode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(0)
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            val context = LocalContext.current
            val notificationHelper = remember { NotificationHelper(context) }

            LaunchedEffect(Unit) {
                mainViewModel.notificationEvents.collectLatest { (title, message) ->
                    notificationHelper.showNotification(title, message)
                }
            }

            LaunchedEffect(incomingAction, incomingTaskId) {
                if (incomingAction != null) {
                    mainViewModel.handleWidgetAction(incomingAction, incomingTaskId, context)
                    incomingAction = null
                    incomingTaskId = null
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

            LaunchedEffect(appearance.ambientSound, appearance.ambientVolume) {
                mainViewModel.syncAmbientAudio(context)
            }

            val plan by mainViewModel.plan.collectAsState()
            LaunchedEffect(plan, mainViewModel.focusTimerRunning) {
                mainViewModel.syncWidgets(context.applicationContext)
            }
            
            KairosTheme(appearance = profile.settings.appearance) {
                KairosApp(mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        incomingAction = intent?.getStringExtra("action")
        incomingTaskId = intent?.getStringExtra("task_id")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val user by viewModel.user.collectAsState()
    val profile by viewModel.profile.collectAsState()
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
                // --- SCAFFOLD LIBERATION ---
                // Using a raw Box stack to ensure no hidden backgrounds or insets trigger the OS scrim.
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    // 1. Root Background Stack
                    val isLightTheme = MaterialTheme.colorScheme.background.red > 0.5f
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Grounding layer
                        Box(modifier = Modifier.fillMaxSize().background(BgMain))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = if (isLightTheme) 0.25f else 0.15f),
                                            Color.Transparent
                                        ),
                                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        radius = 1200f
                                    )
                                )
                        )
                        
                        // Bottom Grounding Gradient: Heavily weighted to "prove" contrast to the OS
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = if (isLightTheme) 0.45f else 0.15f)
                                        ),
                                        startY = 500f
                                    )
                                )
                        )
                        
                        if (profile.settings.appearance.background == "custom" && !profile.settings.appearance.customBackground.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.settings.appearance.customBackground,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = 0.2f
                            )
                        }
                    }

                    // 2. Navigation Layer (Edge-to-Edge Content)
                    NavHost(
                        navController = navController,
                        startDestination = KairosDestination.Dashboard.route,
                        modifier = Modifier.fillMaxSize() 
                    ) {
                        composable(KairosDestination.Dashboard.route) { 
                            DashboardScreen(viewModel = viewModel) 
                        }
                        composable(KairosDestination.Discovery.route) { DiscoveryScreen(viewModel) }
                        composable(KairosDestination.AiHelper.route) { AiHelperScreen(viewModel) }
                        composable(KairosDestination.Tasks.route) { TasksScreen(viewModel) }
                        composable(KairosDestination.Plan.route) { PlanScreen(viewModel) }
                        
                        composable(KairosDestination.Achievements.route) { AchievementsScreen(viewModel) }
                        composable(KairosDestination.Statistics.route) { StatisticsScreen(viewModel) }
                        composable(KairosDestination.Settings.route) { SettingsScreen(viewModel) }
                    }

                    // 3. Floating Overlays (Top Bar)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        CenterAlignedTopAppBar(
                            title = { },
                            actions = {
                                IconButton(onClick = { 
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showNotifPanel = true 
                                    viewModel.markNotificationsRead()
                                }) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadCount > 0) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
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
                                            modifier = Modifier.padding(16.dp).width(220.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProfileImage(
                                                imageUrl = userPhoto,
                                                userName = userName,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                                Text(text = userName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(text = user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                        KairosDestinations.secondary.forEach { dest ->
                                            DropdownMenuItem(
                                                text = { Text(dest.label, fontWeight = FontWeight.Medium) },
                                                leadingIcon = { Icon(dest.icon, null, modifier = Modifier.size(20.dp)) },
                                                onClick = {
                                                    showProfileMenu = false
                                                    navController.navigate(dest.route) {
                                                        launchSingleTop = true
                                                    }
                                                }
                                            )
                                        }

                                        DropdownMenuItem(
                                            text = { 
                                                val isDark = profile.settings.appearance.mode == "dark"
                                                Text(if (isDark) "Light Mode" else "Dark Mode", fontWeight = FontWeight.Medium) 
                                            },
                                            leadingIcon = { 
                                                val isDark = profile.settings.appearance.mode == "dark"
                                                Icon(if (isDark) Icons.Default.WbSunny else Icons.Outlined.ModeNight, null, modifier = Modifier.size(20.dp)) 
                                            },
                                            onClick = {
                                                showProfileMenu = false
                                                viewModel.toggleAppearanceMode()
                                            }
                                        )

                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                        DropdownMenuItem(
                                            text = { Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                            onClick = {
                                                showProfileMenu = false
                                                viewModel.signOut()
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    }

                    // 4. Floating Overlays (Bottom Dock)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        KairosBottomNav(navController) 
                    }

                    // 5. Global Modals
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

                    viewModel.shareSuccessMessage?.let { message ->
                        AlertDialog(
                            onDismissRequest = { viewModel.clearShareSuccess() },
                            title = { Text("🚀 Board Published!", fontWeight = FontWeight.Bold) },
                            text = { Text(message) },
                            confirmButton = {
                                Button(onClick = { viewModel.clearShareSuccess() }) {
                                    Text("Awesome")
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    viewModel.errorMessage?.let { msg ->
                        Box(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = 4.dp,
                                modifier = Modifier.padding(16.dp).clickable { viewModel.clearError() }
                            ) {
                                Text(
                                    text = msg,
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
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
    val isLightTheme = MaterialTheme.colorScheme.background.red > 0.5f

    Surface(
        color = if (isLightTheme) GlassSurfaceLight else GlassSurface,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, if (isLightTheme) GlassBorderLight else GlassBorder.copy(alpha = 0.1f)),
        // REMOVING SHADOW IN LIGHT MODE: Shadows on semitransparent components trigger the OS scrim.
        tonalElevation = if (isLightTheme) 0.dp else 12.dp,
        shadowElevation = if (isLightTheme) 0.dp else 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KairosDestinations.all.forEach { dest ->
                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                // Only restore state for non-Dashboard destinations to prevent stickiness
                                restoreState = dest.route != KairosDestination.Dashboard.route
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            modifier = Modifier.size(28.dp),
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

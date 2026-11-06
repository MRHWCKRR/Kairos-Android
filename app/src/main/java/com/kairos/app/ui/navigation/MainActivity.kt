package com.kairos.app.ui.navigation

import com.kairos.app.ui.icons.KairosIcons

import com.kairos.app.ui.components.KairosOutlinedButton
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.kairos.app.ui.components.KairosTabDock
import com.kairos.app.ui.components.PlannerSearchDialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.kairos.app.data.models.KairosNotification
import com.kairos.app.ui.components.KairosScaffold
import com.kairos.app.ui.components.KairosErrorFeedback
import com.kairos.app.ui.components.ProfileImage
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
import com.kairos.app.ui.screens.onboarding.OnboardingScreen
import com.kairos.app.ui.screens.splash.SplashScreen
import com.kairos.app.ui.theme.*
import com.kairos.app.utils.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var incomingAction by mutableStateOf<String?>(null)
    private var incomingTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw edge to edge; Compose reserves system-bar space in the shell.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
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
            val systemDark = isSystemInDarkTheme()
            val darkSystemIcons = appearance.mode == "light" || (appearance.mode == "system" && !systemDark)

            // Keep icon contrast aligned with the selected appearance.
            LaunchedEffect(darkSystemIcons) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = darkSystemIcons
                controller.isAppearanceLightNavigationBars = darkSystemIcons
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
    val plan by viewModel.plan.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: KairosDestination.Dashboard.route
    var showNotifications by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navigate: (String) -> Unit = { destination ->
        navController.navigate(destination) { launchSingleTop = true }
    }

    when {
        viewModel.isInitializing -> SplashScreen()
        user == null -> LoginScreen()
        !profile.onboardingCompleted -> OnboardingScreen(viewModel)
        else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (profile.settings.appearance.background in listOf("minimal", "gradient")) {
                Box(Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.03f),
                            MaterialTheme.colorScheme.primary.copy(alpha = if (profile.settings.appearance.background == "gradient") 0.10f else 0.04f),
                            Color.Transparent)
                    )
                ))
            }
            if (profile.settings.appearance.background == "custom" && !profile.settings.appearance.customBackground.isNullOrBlank()) {
                AsyncImage(profile.settings.appearance.customBackground, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.15f)
            }
            KairosScaffold(
                topBar = {
                    if (route == KairosDestination.Dashboard.route) {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showNotifications = true; viewModel.markNotificationsRead() }) {
                                BadgedBox(badge = {
                                    val unread = profile.notifications.count { !it.read }
                                    if (unread > 0) Badge { Text(if (unread > 9) "9+" else unread.toString()) }
                                }) { Icon(KairosIcons.Notifications, "Notifications") }
                            }
                            KairosAccountMenu(
                                name = profile.settings.profile.displayName.ifBlank { user?.displayName ?: "Profile" },
                                photo = profile.settings.profile.avatarURL.ifBlank { user?.photoUrl?.toString() ?: "" },
                                onNavigate = navigate,
                                onSignOut = { viewModel.signOut() }
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!keyboardOpen) KairosBottomNav(navController, onSearch = { showSearch = true }, reduceMotion = profile.settings.accessibility.reduceMotion)
                },
                snackbar = { viewModel.errorMessage?.let { KairosErrorFeedback(it, onDismiss = { viewModel.clearError() }) } }
            ) { padding ->
                NavHost(
                    navController, startDestination = KairosDestination.Dashboard.route,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    enterTransition = { fadeIn(tween(if (profile.settings.accessibility.reduceMotion) 0 else 220)) },
                    exitTransition = { fadeOut(tween(if (profile.settings.accessibility.reduceMotion) 0 else 220)) },
                    popEnterTransition = { fadeIn(tween(if (profile.settings.accessibility.reduceMotion) 0 else 220)) },
                    popExitTransition = { fadeOut(tween(if (profile.settings.accessibility.reduceMotion) 0 else 220)) }
                ) {
                    composable(KairosDestination.Dashboard.route) { DashboardScreen(viewModel, onNavigate = navigate) }
                    composable(KairosDestination.Tasks.route) {
                        TasksScreen(viewModel)
                    }
                    composable(KairosDestination.AiHelper.route) { AiHelperScreen(viewModel) }
                    composable(KairosDestination.Plan.route) { PlanScreen(viewModel) }
                    composable(KairosDestination.Achievements.route) { AchievementsScreen(viewModel) }
                    composable(KairosDestination.Statistics.route) { StatisticsScreen(viewModel) }
                    composable(KairosDestination.Settings.route) { SettingsScreen(viewModel, onNavigate = navigate) }
                }
            }
            if (showNotifications) ModalBottomSheet(
                onDismissRequest = { showNotifications = false }, containerColor = MaterialTheme.colorScheme.surface
            ) { NotificationPanel(profile.notifications, onDelete = { viewModel.deleteNotification(it) }) }
            if (showSearch) key(user?.uid) {
                PlannerSearchDialog(
                    plan = plan,
                    onDismiss = { showSearch = false },
                    onToggleTask = { id, completed -> viewModel.toggleTask(id, completed) },
                    timezone = profile.settings.profile.timezone,
                    isLoading = viewModel.isInitializing,
                    errorMessage = if (plan == null) viewModel.errorMessage else null
                )
            }
        }
    }
}

@Composable
private fun KairosAccountMenu(name: String, photo: String, onNavigate: (String) -> Unit, onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = "Open profile" }) {
            ProfileImage(imageUrl = photo, userName = name, modifier = Modifier.size(36.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            Text(name, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
            DropdownMenuItem(text = { Text("Profile & settings") }, onClick = { expanded = false; onNavigate("settings") }, leadingIcon = { Icon(KairosIcons.Settings, null) })
            DropdownMenuItem(text = { Text("Statistics") }, onClick = { expanded = false; onNavigate("statistics") }, leadingIcon = { Icon(KairosIcons.BarChart, null) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Sign out", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onSignOut() })
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
            Icon(KairosIcons.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
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
            imageVector = KairosIcons.Info,
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

        KairosOutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Out")
        }
    }
}

@Composable
fun KairosBottomNav(navController: androidx.navigation.NavHostController, onSearch: () -> Unit = {}, reduceMotion: Boolean = false) {
    val entry by navController.currentBackStackEntryAsState()
    KairosTabDock(entry?.destination?.route ?: "dashboard", onSelect = { destination ->
        navController.navigate(destination) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            // A secondary screen can be saved under the start destination.
            // Home must open the dashboard rather than replay that saved stack.
            restoreState = destination != KairosDestination.Dashboard.route
        }
    }, onSearch = onSearch, reduceMotion = reduceMotion)
}


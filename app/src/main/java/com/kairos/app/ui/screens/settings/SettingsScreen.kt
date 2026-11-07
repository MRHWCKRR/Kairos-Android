package com.kairos.app.ui.screens.settings

import com.kairos.app.ui.icons.KairosIcons

import com.kairos.app.ui.components.KairosOutlinedButton
import com.kairos.app.ui.components.KairosTextButton
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kairos.app.data.local.PreferenceManager
import com.kairos.app.data.models.*
import com.kairos.app.ui.components.ProfileImage
import com.kairos.app.ui.navigation.MainViewModel
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel = viewModel(),
    onNavigate: (String) -> Unit = {}
) {
    val profile by mainViewModel.profile.collectAsState()
    val user by mainViewModel.user.collectAsState()
    var section by rememberSaveable(user?.uid) { mutableStateOf<String?>(null) }
    var editingProfile by rememberSaveable(user?.uid) { mutableStateOf(false) }
    BackHandler(enabled = editingProfile || section != null) {
        if (editingProfile) editingProfile = false else section = null
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (editingProfile) {
            PersonalEditBunker(profile, mainViewModel) { editingProfile = false }
        } else if (section != null) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { section = null }) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back to Settings")
                    }
                    Text(section.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)) {
                    when (section) {
                        "Profile" -> PersonalTabView(profile) { editingProfile = true }
                        "Accessibility" -> AccessibilityTabContent(profile.settings, mainViewModel)
                        "Notifications" -> NotificationSettingsContent(profile.settings, mainViewModel)
                        "Security" -> SecurityTabContent(user, mainViewModel)
                        "Appearance" -> AppearanceTabContent(profile.settings, mainViewModel)
                        "AI Coach" -> AiTabSafe(mainViewModel)
                        "Privacy & Data" -> PrivacyTabContent(profile, mainViewModel)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp))
                SettingsGroup {
                    Row(Modifier.fillMaxWidth().clickable { section = "Profile" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileImage(imageUrl = profile.settings.profile.avatarURL, userName = profile.settings.profile.displayName, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.settings.profile.displayName.ifBlank { "Your profile" }, fontWeight = FontWeight.SemiBold)
                            Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                SettingsGroup {
                    SettingsNavigationRow("Accessibility", KairosIcons.Accessibility) { section = "Accessibility" }
                    SettingsNavigationRow("Notifications", KairosIcons.symbol("bell.badge")) { section = "Notifications" }
                    SettingsNavigationRow("Appearance", KairosIcons.Contrast, divider = false) { section = "Appearance" }
                }
                SettingsGroupLabel("Progress")
                SettingsGroup {
                    SettingsNavigationRow("Goals & Achievements", KairosIcons.EmojiEvents) { onNavigate("achievements") }
                    SettingsNavigationRow("Statistics", KairosIcons.symbol("chart.bar.xaxis"), divider = false) { onNavigate("statistics") }
                }
                SettingsGroupLabel("Account")
                SettingsGroup {
                    SettingsNavigationRow("Security", KairosIcons.Lock) { section = "Security" }
                    SettingsNavigationRow("AI Coach", KairosIcons.AutoAwesome) { section = "AI Coach" }
                    SettingsNavigationRow("Privacy & Data", KairosIcons.Shield, divider = false) { section = "Privacy & Data" }
                }
                Spacer(Modifier.height(24.dp))
                SettingsGroup {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(KairosIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Kairos", fontWeight = FontWeight.SemiBold)
                            Text("Your time, intentionally spent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun SettingsNavigationRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, divider: Boolean = true, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            Icon(KairosIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun NotificationSettingsContent(settings: KairosSettings, viewModel: MainViewModel) {
    val notifications = settings.notifications
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Stay in the loop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose the updates that help you keep your day on track.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup {
            NotificationToggle("Notifications", notifications.enabled) { viewModel.updateSettings(settings.copy(notifications = notifications.copy(enabled = it)), settings) }
            NotificationToggle("Board completion", notifications.boardCompletion) { viewModel.updateSettings(settings.copy(notifications = notifications.copy(boardCompletion = it)), settings) }
            NotificationToggle("Bedtime reminders", notifications.bedtimeReminders) { viewModel.updateSettings(settings.copy(notifications = notifications.copy(bedtimeReminders = it)), settings) }
        }
    }
}

@Composable
private fun NotificationToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PersonalTabView(profile: KairosUserProfile, onEditRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Profile Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp)) {
                ProfileImage(
                    imageUrl = profile.settings.profile.avatarURL,
                    userName = profile.settings.profile.displayName,
                    modifier = Modifier.size(80.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = profile.settings.profile.displayName.ifBlank { "User" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(text = "Routine Manager", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info Grid
        ProfileInfoRow(label = "Birthday", value = profile.settings.profile.birthday.ifBlank { "Not set" })
        Spacer(modifier = Modifier.height(16.dp))
        ProfileInfoRow(label = "Timezone", value = profile.settings.profile.timezone)

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onEditRequest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(KairosIcons.Edit, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit Profile Details")
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(text = value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalEditBunker(
    profile: KairosUserProfile,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val draftSettings = remember { profile.settings }
    var nameDraft by remember { mutableStateOf(profile.settings.profile.displayName) }
    var birthdayDraft by remember { mutableStateOf(profile.settings.profile.birthday) }
    var timezoneDraft by remember { mutableStateOf(profile.settings.profile.timezone) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimezoneMenu by remember { mutableStateOf(false) }

    val commonTimezones = remember {
        listOf(
            "UTC", "GMT", "America/New_York", "America/Los_Angeles", "America/Chicago",
            "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Tokyo", "Asia/Shanghai",
            "Asia/Singapore", "Australia/Sydney", "Australia/Melbourne", "Australia/Brisbane",
            "Pacific/Auckland"
        ).sorted()
    }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(context, it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) { Icon(KairosIcons.Close, "Cancel profile editing", tint = MaterialTheme.colorScheme.onBackground) }
            Text(text = "Edit Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            KairosTextButton(onClick = {
                viewModel.updateSettings(draftSettings.copy(
                    profile = draftSettings.profile.copy(
                        displayName = nameDraft,
                        birthday = birthdayDraft,
                        timezone = timezoneDraft
                    )
                ), draftSettings)
                onDismiss()
            }) {
                Text("Save", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            // Avatar Section with Picker
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(100.dp)) {
                        ProfileImage(
                            imageUrl = profile.settings.profile.avatarURL,
                            userName = nameDraft,
                            modifier = Modifier.size(100.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )

                        if (viewModel.isUploadingAvatar) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row {
                        KairosOutlinedButton(
                            onClick = { launcher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp),
                            enabled = !viewModel.isUploadingAvatar,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (viewModel.isUploadingAvatar) "Processing..." else "Upload")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        KairosOutlinedButton(
                            onClick = { viewModel.removeAvatar() },
                            shape = RoundedCornerShape(8.dp),
                            enabled = !viewModel.isUploadingAvatar && profile.settings.profile.avatarURL.isNotEmpty(),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            BunkerInputSafe(label = "Display Name", value = nameDraft, onValueChange = { nameDraft = it })

            Spacer(modifier = Modifier.height(24.dp))

            // Birthday with DatePicker
            Column {
                Text(text = "Birthday", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = birthdayDraft.ifBlank { "Select Date" },
                        color = if (birthdayDraft.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timezone with Dropdown
            Column {
                Text(text = "Timezone", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .clickable { showTimezoneMenu = true }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = timezoneDraft, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Icon(KairosIcons.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    DropdownMenu(
                        expanded = showTimezoneMenu,
                        onDismissRequest = { showTimezoneMenu = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surface)
                    ) {
                        commonTimezones.forEach { zone ->
                            DropdownMenuItem(
                                text = { Text(zone) },
                                onClick = {
                                    timezoneDraft = zone
                                    showTimezoneMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (viewModel.settingsError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = viewModel.settingsError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (birthdayDraft.isNotBlank()) {
                try {
                    LocalDate.parse(birthdayDraft, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .atStartOfDay(ZoneId.of("UTC"))
                        .toInstant()
                        .toEpochMilli()
                } catch (e: Exception) { null }
            } else null
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                KairosTextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        birthdayDraft = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                KairosTextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun SecurityTabContent(user: com.google.firebase.auth.FirebaseUser?, viewModel: MainViewModel) {
    var newEmail by remember { mutableStateOf(user?.email ?: "") }
    var emailPassword by remember { mutableStateOf("") }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val lastSignIn = remember(user) {
        user?.metadata?.lastSignInTimestamp?.let {
            SimpleDateFormat("M/d/yyyy, h:mm:ss a", Locale.getDefault()).format(Date(it))
        } ?: "Unknown"
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Privacy & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // Change Email
        SecurityCard(title = "Change Email") {
            BunkerInputSafe(label = "New Email", value = newEmail, onValueChange = { newEmail = it })
            Spacer(modifier = Modifier.height(16.dp))
            BunkerInputSafe(label = "Confirm with Password", value = emailPassword, onValueChange = { emailPassword = it }, isPassword = true)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.updateEmail(emailPassword, newEmail) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Update Email")
            }
        }

        // Change Password
        SecurityCard(title = "Change Password") {
            BunkerInputSafe(label = "Current password", value = currentPassword, onValueChange = { currentPassword = it }, isPassword = true)
            Spacer(modifier = Modifier.height(16.dp))
            BunkerInputSafe(label = "New password", value = newPassword, onValueChange = { newPassword = it }, isPassword = true)
            Spacer(modifier = Modifier.height(16.dp))
            BunkerInputSafe(label = "Confirm new password", value = confirmPassword, onValueChange = { confirmPassword = it }, isPassword = true)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (newPassword == confirmPassword) {
                        viewModel.updatePassword(currentPassword, newPassword)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Update Password")
            }
        }

        // Session
        SecurityCard(title = "Session") {
            Text(text = "Last sign-in: $lastSignIn", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "A full \"manage all devices\" view needs a backend we don't have yet — this is on the roadmap.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        if (viewModel.securityError != null) {
            Text(text = viewModel.securityError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        if (viewModel.securitySuccess != null) {
            Text(text = viewModel.securitySuccess!!, color = Color(0xFF4ADE80), fontSize = 12.sp)
        }
    }
}

@Composable
fun SecurityCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun AccessibilityTabContent(settings: KairosSettings, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Accessibility", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Column {
            Text(text = "Layout Density", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                listOf("compact", "default", "spacious").forEach { density ->
                    BunkerOptionButton(density, settings.accessibility.density == density, { viewModel.updateSettings(settings.copy(accessibility = settings.accessibility.copy(density = density)), settings) }, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppearanceTabContent(settings: KairosSettings, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // Mode
        Column {
            Text(text = "Theme Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                BunkerOptionButton("dark", settings.appearance.mode == "dark", { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = "dark")), settings) }, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                BunkerOptionButton("light", settings.appearance.mode == "light", { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = "light")), settings) }, Modifier.weight(1f))
            }
        }

        // Font
        Column {
            Text(text = "Font Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                listOf("sans", "round", "mono").forEach { f ->
                    BunkerOptionButton(f, settings.appearance.font == f, { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(font = f)), settings) }, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        // Theme Accent
        Column {
            Text(text = "Theme Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "default" to Color(red = 0.49f, green = 0.27f, blue = 0.96f),
                    "blue" to Color(0xFF3373F2),
                    "green" to Color(0xFF2E9E66),
                    "orange" to Color(0xFFF26B29),
                    "pink" to Color(0xFFE64094),
                    "red" to Color(0xFFEB3D47)
                ).forEach { (id, color) ->
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(color).border(2.dp, if (settings.appearance.theme == id) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape).clickable { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(theme = id)), settings) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }

        // Background Style
        Column {
            Text(text = "Background Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                listOf("solid", "minimal", "gradient", "custom").forEach { b ->
                    BunkerOptionButton(b, settings.appearance.background == b, { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(background = b)), settings) }, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            if (settings.appearance.background == "custom") {
                Spacer(modifier = Modifier.height(16.dp))
                BunkerInputSafe(
                    label = "Custom Background URL",
                    value = settings.appearance.customBackground ?: "",
                    onValueChange = { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(customBackground = it)), settings) }
                )
            }
        }

        // Ambient Sound
        Column {
            Text(text = "Ambient Sound", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
                listOf("none", "lofi", "rain", "coffee").forEach { s ->
                    BunkerOptionButton(s, settings.appearance.ambientSound == s, { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(ambientSound = s)), settings) }, Modifier.width(80.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(KairosIcons.VolumeUp, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Slider(
                    value = settings.appearance.ambientVolume.toFloat(),
                    onValueChange = { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(ambientVolume = it.toInt())), settings) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Confetti
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Celebratory Confetti", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(text = "Show confetti when completing routines.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Switch(
                checked = settings.appearance.confetti,
                onCheckedChange = { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(confetti = it)), settings) }
            )
        }
    }
}

@Composable
fun AiTabSafe(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "AI Coach", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        SecurityCard(title = "Inbuilt Intelligence") {
            Text(
                text = "Kairos features inbuilt AI coaching and routine generation powered by our community relay. No API keys or setup required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your coaching history is synced across all your devices and the Kairos web app automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun PrivacyTabContent(profile: KairosUserProfile, viewModel: MainViewModel) {
    val plan by viewModel.plan.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Privacy & Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // Recycle Bin
        SecurityCard(title = "Recycle Bin") {
            RecycleBinContent(plan, viewModel)
        }

        // Legal
        SecurityCard(title = "Legal") {
            KairosTextButton(onClick = { }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text("Terms of Service", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
            KairosTextButton(onClick = { }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text("Privacy Policy", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }

        // Danger Zone
        SecurityCard(title = "Danger Zone") {
            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete My Account", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This action is permanent and will delete all your boards, statistics, and profile data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = { Text("Are you absolutely sure? This cannot be undone.") },
            confirmButton = {
                KairosTextButton(onClick = { viewModel.deleteAccount(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                KairosTextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecycleBinContent(plan: KairosPlan?, viewModel: MainViewModel) {
    val archivedBoards = plan?.boards?.filter { it.archived } ?: emptyList()
    val archivedSections = mutableListOf<Pair<KairosBoard, KairosSection>>()
    val archivedTasks = mutableListOf<Triple<KairosBoard, KairosSection, KairosTask>>()

    plan?.boards?.forEach { board ->
        board.sections.forEach { section ->
            if (section.archived && !board.archived) archivedSections.add(board to section)
            section.tasks.forEach { task ->
                if (task.archived && !section.archived && !board.archived) {
                    archivedTasks.add(Triple(board, section, task))
                }
            }
        }
    }

    val totalCount = archivedBoards.size + archivedSections.size + archivedTasks.size

    if (totalCount == 0) {
        Text("Your recycle bin is empty.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            archivedBoards.forEach { board ->
                RecycleItemRow(board.title, "Board", { viewModel.restoreBoard(board.id) }, { viewModel.deleteBoardForever(board.id) })
            }
            archivedSections.forEach { (board, section) ->
                RecycleItemRow(section.title, "Section (${board.title})", { viewModel.restoreSection(section.id) }, { viewModel.deleteSectionForever(board.id, section.id) })
            }
            archivedTasks.forEach { (board, section, task) ->
                RecycleItemRow(task.title, "Task (${section.title})", { viewModel.restoreTask(task.id) }, { viewModel.deleteTaskForever(section.id, task.id) })
            }
        }
    }
}

@Composable
fun RecycleItemRow(title: String, type: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(text = type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Row {
            IconButton(onClick = onRestore) { Icon(KairosIcons.Restore, null, tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDelete) { Icon(KairosIcons.DeleteForever, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
fun BunkerInputSafe(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    placeholder: String = ""
) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text)
            )
            if (value.isEmpty()) {
                Text(text = placeholder.ifBlank { "Enter $label..." }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun BunkerOptionButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight().clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text.replaceFirstChar { it.uppercase() }, color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}



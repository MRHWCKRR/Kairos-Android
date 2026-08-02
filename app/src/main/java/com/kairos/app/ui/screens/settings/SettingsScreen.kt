package com.kairos.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kairos.app.data.local.PreferenceManager
import com.kairos.app.data.models.*
import com.kairos.app.ui.components.ProfileImage
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    val user by mainViewModel.user.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isEditingProfile by remember { mutableStateOf(false) }

    // --- ZERO-WINDOW CONTENT SWAP ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isEditingProfile) {
            PersonalEditBunker(
                profile = profile,
                viewModel = mainViewModel,
                onDismiss = { isEditingProfile = false }
            )
        } else {
            SettingsMainView(
                profile = profile,
                user = user,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onEditRequest = { isEditingProfile = true },
                mainViewModel = mainViewModel
            )
        }
    }
}

@Composable
fun SettingsMainView(
    profile: KairosUserProfile,
    user: com.google.firebase.auth.FirebaseUser?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onEditRequest: () -> Unit,
    mainViewModel: MainViewModel
) {
    val tabs = listOf("Personal", "Accessibility", "Security", "Appearance", "AI Engine")
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Basic Tab Row - Scrollable if too many tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                BunkerTabItem(
                    text = title,
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.width(100.dp) // Fixed width for scrollable tabs
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            when (selectedTab) {
                0 -> PersonalTabView(profile, onEditRequest)
                1 -> AccessibilityTabContent(profile.settings, mainViewModel)
                2 -> SecurityTabContent(user)
                3 -> AppearanceTabContent(profile.settings, mainViewModel)
                4 -> AiTabSafe(mainViewModel)
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
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
            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
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
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onBackground) }
            Text(text = "Edit Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = {
                viewModel.updateSettings(profile.settings.copy(
                    profile = profile.settings.profile.copy(
                        displayName = nameDraft,
                        birthday = birthdayDraft,
                        timezone = timezoneDraft
                    )
                ))
                onDismiss()
            }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                        OutlinedButton(
                            onClick = { launcher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp),
                            enabled = !viewModel.isUploadingAvatar,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (viewModel.isUploadingAvatar) "Processing..." else "Upload")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
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
                            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        birthdayDraft = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun SecurityTabContent(user: com.google.firebase.auth.FirebaseUser?) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Privacy & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Account", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(text = "Email", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(text = user?.email ?: "Unknown", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(text = "Signed in with", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                val provider = user?.providerData?.getOrNull(1)?.providerId ?: "password"
                val providerName = when {
                    provider.contains("google") -> "Google"
                    provider.contains("github") -> "GitHub"
                    else -> "Email"
                }
                Text(text = providerName, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
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
                    BunkerOptionButton(density, settings.accessibility.density == density, { viewModel.updateSettings(settings.copy(accessibility = settings.accessibility.copy(density = density))) }, Modifier.weight(1f))
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
        Column {
            Text(text = "Theme Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                BunkerOptionButton("dark", settings.appearance.mode == "dark", { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = "dark"))) }, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                BunkerOptionButton("light", settings.appearance.mode == "light", { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = "light"))) }, Modifier.weight(1f))
            }
        }
        Column {
            Text(text = "Accent Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                listOf(
                    "default" to Color(0xFFA855F7),
                    "fairyfloss" to Color(0xFFFF8FC9),
                    "poseidon" to Color(0xFF38BDF8),
                    "peacefulplains" to Color(0xFF4ADE80)
                ).forEach { (id, color) ->
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(color).border(2.dp, if (settings.appearance.theme == id) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape).clickable { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(theme = id))) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
fun AiTabSafe(viewModel: MainViewModel) {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    var keyDraft by remember { mutableStateOf(preferenceManager.getGeminiKey() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "AI Helper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        BunkerInputSafe(label = "Gemini API Key", value = keyDraft, onValueChange = { keyDraft = it })
        Button(
            onClick = { 
                preferenceManager.saveGeminiKey(keyDraft)
                Toast.makeText(context, "Key Saved!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save API Key")
        }
    }
}

@Composable
fun BunkerTabItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxHeight().clickable { onClick() }.drawBehind {
            if (selected) {
                drawLine(
                    color = Color(0xFFA855F7),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun BunkerInputSafe(label: String, value: String, placeholder: String = "", onValueChange: (String) -> Unit) {
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
                singleLine = true
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
            Text(text = text.replaceFirstChar { it.uppercase() }, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

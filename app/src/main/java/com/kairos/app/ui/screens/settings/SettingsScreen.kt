package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Personal", "Accessibility", "Appearance", "AI Engine")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- HEADER ---
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { 
                        Text(
                            text = title, 
                            fontSize = 11.sp, 
                            maxLines = 1,
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ) 
                    }
                )
            }
        }

        // --- CONTENT AREA ---
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                when (selectedTab) {
                    0 -> PersonalTabResetInteractive(profile, mainViewModel)
                    1 -> AccessibilityTabSafe(profile.settings, mainViewModel)
                    2 -> AppearanceTabSafe(profile.settings, mainViewModel)
                    3 -> AiTabSafe()
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun PersonalTabResetInteractive(profile: KairosUserProfile, viewModel: MainViewModel) {
    var nameDraft by remember(profile.settings.profile.displayName) { mutableStateOf(profile.settings.profile.displayName) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.uploadAvatar(it) }
    }

    Column {
        Text(text = "Profile Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(32.dp))
        
        // Avatar Section with Picker
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(100.dp)) {
                    if (profile.settings.profile.avatarURL.isNotBlank()) {
                        AsyncImage(
                            model = profile.settings.profile.avatarURL,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(50.dp))
                            }
                        }
                    }
                    
                    if (viewModel.isUploadingAvatar) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { launcher.launch("image/*") },
                    shape = RoundedCornerShape(8.dp),
                    enabled = !viewModel.isUploadingAvatar
                ) {
                    Text(if (viewModel.isUploadingAvatar) "Uploading..." else "Change Picture")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        // Display Name Input
        SettingsInputBunker(label = "Display Name", value = nameDraft, onValueChange = { nameDraft = it })
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.updateSettings(profile.settings.copy(
                    profile = profile.settings.profile.copy(
                        displayName = nameDraft
                    )
                ))
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Name Changes")
        }
    }
}

@Composable
fun AccessibilityTabSafe(settings: KairosSettings, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Accessibility", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Column {
            Text(text = "Layout Density", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                listOf("compact", "default", "spacious").forEach { density ->
                    BunkerOptionTile(density, settings.accessibility.density == density, { viewModel.updateSettings(settings.copy(accessibility = settings.accessibility.copy(density = density))) }, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppearanceTabSafe(settings: KairosSettings, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Column {
            Text(text = "Theme Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                BunkerOptionTile("dark", settings.appearance.mode == "dark", { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = "dark"))) }, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                BunkerOptionTile("light", settings.appearance.mode == "light", { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = "light"))) }, Modifier.weight(1f))
            }
        }
        Column {
            Text(text = "Theme Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                listOf(
                    "default" to Color(0xFFA855F7),
                    "fairyfloss" to Color(0xFFFF8FC9),
                    "poseidon" to Color(0xFF38BDF8),
                    "peacefulplains" to Color(0xFF4ADE80)
                ).forEach { (id, color) ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, if (settings.appearance.theme == id) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                            .clickable { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(theme = id))) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
fun AiTabSafe() {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    var keyDraft by remember { mutableStateOf(preferenceManager.getGeminiKey() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = "AI Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        SettingsInputBunker(label = "Gemini API Key", value = keyDraft, onValueChange = { keyDraft = it })
        Button(
            onClick = { 
                preferenceManager.saveGeminiKey(keyDraft)
                android.widget.Toast.makeText(context, "Key Saved!", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save API Key")
        }
    }
}

@Composable
fun SettingsInputBunker(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                Text(text = "Enter $label...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun BunkerOptionTile(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(48.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text.replaceFirstChar { it.uppercase() }, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

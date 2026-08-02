package com.kairos.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import com.kairos.app.ui.components.ProfileImage
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isEditingProfile by remember { mutableStateOf(false) }

    // --- ZERO-WINDOW CONTENT SWAP ---
    // Either shows the main tabs OR the isolated profile editor. 
    // This physically prevents measurement loops between overlapping windows.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isEditingProfile) {
            // THE ISOLATED EDITOR (No ties to main profile Flow during editing)
            PersonalEditBunker(
                initialName = profile.settings.profile.displayName,
                initialAvatar = profile.settings.profile.avatarURL,
                viewModel = mainViewModel,
                onDismiss = { isEditingProfile = false }
            )
        } else {
            // THE MAIN VIEW
            SettingsMainView(
                profile = profile,
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
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onEditRequest: () -> Unit,
    mainViewModel: MainViewModel
) {
    val tabs = listOf("Personal", "Accessibility", "Appearance", "AI Engine")
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                BunkerTabItem(
                    text = title,
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            when (selectedTab) {
                0 -> PersonalTabView(profile, onEditRequest)
                1 -> AccessibilityTabContent(profile.settings, mainViewModel)
                2 -> AppearanceTabContent(profile.settings, mainViewModel)
                3 -> AiTabSafe(mainViewModel)
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun PersonalTabView(profile: KairosUserProfile, onEditRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Profile Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(modifier = Modifier.size(100.dp)) {
            ProfileImage(
                imageUrl = profile.settings.profile.avatarURL,
                modifier = Modifier.size(100.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = profile.settings.profile.displayName.ifBlank { "User" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(text = "Routine Manager", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(32.dp))
        
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
fun PersonalEditBunker(
    initialName: String,
    initialAvatar: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    // --- SNAPSHOT STATE ---
    // By using local state derived from constants, we decouple the editor 
    // from the main profile flow, breaking any recomposition loops.
    var nameDraft by remember { mutableStateOf(initialName) }
    val currentAvatar = remember { initialAvatar } 
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(context, it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Simple Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                viewModel.clearSettingsError()
                onDismiss()
            }) { 
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onBackground) 
            }
            Text(text = "Edit Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = {
                viewModel.updateSettings(viewModel.profile.value.settings.copy(
                    profile = viewModel.profile.value.settings.profile.copy(displayName = nameDraft)
                ))
                onDismiss()
            }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            // Avatar Section
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(100.dp)) {
                        ProfileImage(
                            imageUrl = currentAvatar,
                            modifier = Modifier.size(100.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        
                        if (viewModel.isUploadingAvatar) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { launcher.launch("image/*") },
                        shape = RoundedCornerShape(8.dp),
                        enabled = !viewModel.isUploadingAvatar,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(if (viewModel.isUploadingAvatar) "Processing..." else "Change Picture")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            BunkerInputSafe(label = "Display Name", value = nameDraft, onValueChange = { nameDraft = it })
            
            if (viewModel.settingsError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = viewModel.settingsError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Images are compressed to stay under 800KB for Firestore stability.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
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
fun BunkerInputSafe(label: String, value: String, onValueChange: (String) -> Unit) {
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
                Text(text = "Enter $label...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), fontSize = 15.sp)
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

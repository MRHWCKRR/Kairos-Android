package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import com.kairos.app.ui.navigation.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    var selectedTab by remember { mutableIntStateOf(2) } // Default to Appearance
    val tabs = listOf("Personal", "Accessibility", "Appearance", "AI Engine")

    var showEditSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- FIXED HEADER ---
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selectedTab = index }
                        .drawBehind {
                            if (selectedTab == index) {
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
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // --- CONTENT AREA (LazyColumn for ultimate stability) ---
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            if (selectedTab == 0) { // PERSONAL
                item { 
                    Text(text = "Profile Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(80.dp)) {
                            if (profile.settings.profile.avatarURL.isNotBlank()) {
                                AsyncImage(
                                    model = profile.settings.profile.avatarURL,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(text = profile.settings.profile.displayName.ifBlank { "User" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = "Tap to update your information", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { showEditSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile")
                        }
                    }
                }
            }

            if (selectedTab == 1) { // ACCESSIBILITY
                item { 
                    Text(text = "Accessibility", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(text = "Layout Density", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        listOf("compact", "default", "spacious").forEach { d ->
                            OptionTileBunker(d, profile.settings.accessibility.density == d, { mainViewModel.updateSettings(profile.settings.copy(accessibility = profile.settings.accessibility.copy(density = it))) }, Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }

            if (selectedTab == 2) { // APPEARANCE
                item { 
                    Text(text = "Appearance", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(text = "Theme Mode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        OptionTileBunker("dark", profile.settings.appearance.mode == "dark", { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "dark"))) }, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        OptionTileBunker("light", profile.settings.appearance.mode == "light", { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "light"))) }, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
                item {
                    Text(text = "Accent Color", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        listOf("default", "fairyfloss", "poseidon", "peacefulplains").forEach { theme ->
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(
                                    when(theme){
                                        "fairyfloss" -> Color(0xFFFF8FC9)
                                        "poseidon" -> Color(0xFF38BDF8)
                                        "peacefulplains" -> Color(0xFF4ADE80)
                                        else -> Color(0xFFA855F7)
                                    }
                                ).border(2.dp, if(profile.settings.appearance.theme == theme) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                                .clickable { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(theme = theme))) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            }

            if (selectedTab == 3) { // AI
                item { 
                    Text(text = "AI Helper Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    val context = LocalContext.current
                    val prefs = remember { PreferenceManager(context) }
                    var keyDraft by remember { mutableStateOf(prefs.getGeminiKey() ?: "") }
                    Column {
                        BunkerInputMini(label = "Gemini API Key", value = keyDraft, onValueChange = { keyDraft = it })
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { 
                            prefs.saveGeminiKey(keyDraft)
                            android.widget.Toast.makeText(context, "Key Saved!", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                            Text("Save API Key")
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            PersonalEditSheet(
                profile = profile,
                onSave = { updatedProfile ->
                    mainViewModel.updateSettings(updatedProfile.settings)
                    showEditSheet = false
                }
            )
        }
    }
}

@Composable
fun PersonalEditSheet(profile: KairosUserProfile, onSave: (KairosUserProfile) -> Unit) {
    var nameDraft by remember { mutableStateOf(profile.settings.profile.displayName) }
    var avatarDraft by remember { mutableStateOf(profile.settings.profile.avatarURL) }
    var birthdayDraft by remember { mutableStateOf(profile.settings.profile.birthday) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)
    ) {
        Text(text = "Edit Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(32.dp))

        BunkerInputMini(label = "Display Name", value = nameDraft, onValueChange = { nameDraft = it })
        Spacer(modifier = Modifier.height(24.dp))
        BunkerInputMini(label = "Avatar URL", value = avatarDraft, onValueChange = { avatarDraft = it })
        Spacer(modifier = Modifier.height(24.dp))
        BunkerInputMini(label = "Birthday (YYYY-MM-DD)", value = birthdayDraft, onValueChange = { birthdayDraft = it })

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                onSave(profile.copy(
                    settings = profile.settings.copy(
                        profile = profile.settings.profile.copy(
                            displayName = nameDraft,
                            avatarURL = avatarDraft,
                            birthday = birthdayDraft
                        )
                    )
                ))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save Profile Changes", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BunkerInputMini(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
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
                Text(text = "...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun OptionTileBunker(text: String, selected: Boolean, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight().clickable { onClick(text) },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text.replaceFirstChar { it.uppercase() }, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

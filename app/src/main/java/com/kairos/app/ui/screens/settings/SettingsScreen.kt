package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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

    // DRAFT STATES (At top for stability)
    var draftName by remember { mutableStateOf("") }
    var draftAvatar by remember { mutableStateOf("") }
    
    // Sync drafts once on load
    LaunchedEffect(profile.settings.profile) {
        if (draftName.isEmpty()) draftName = profile.settings.profile.displayName
        if (draftAvatar.isEmpty()) draftAvatar = profile.settings.profile.avatarURL
    }

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
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                BunkerTabItem(
                    text = title,
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- ULTRA FLAT BODY ---
        // Using LazyColumn but with absolute flat items. NO nested columns or weights inside items.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp)
        ) {
            if (selectedTab == 0) { // PERSONAL
                item { Text(text = "Profile Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        if (draftAvatar.isNotBlank()) {
                            AsyncImage(
                                model = draftAvatar,
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
                
                item { Spacer(modifier = Modifier.height(32.dp)) }

                item { BunkerInputSimple(label = "Name", value = draftName, onValueChange = { draftName = it }) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                item { BunkerInputSimple(label = "Photo URL", value = draftAvatar, onValueChange = { draftAvatar = it }) }
                item { Spacer(modifier = Modifier.height(32.dp)) }

                item {
                    Button(
                        onClick = {
                            mainViewModel.updateSettings(profile.settings.copy(
                                profile = profile.settings.profile.copy(
                                    displayName = draftName,
                                    avatarURL = draftAvatar
                                )
                            ))
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Update Profile")
                    }
                }
            }

            if (selectedTab == 1) { // ACCESSIBILITY
                item { Text(text = "Accessibility", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { 
                    BunkerDensityPicker(
                        current = profile.settings.accessibility.density,
                        onSelect = { mainViewModel.updateSettings(profile.settings.copy(accessibility = profile.settings.accessibility.copy(density = it))) }
                    ) 
                }
            }

            if (selectedTab == 2) { // APPEARANCE
                item { Text(text = "Appearance", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item {
                    Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        BunkerOptionSmall("Dark", profile.settings.appearance.mode == "dark", { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "dark"))) }, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        BunkerOptionSmall("Light", profile.settings.appearance.mode == "light", { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "light"))) }, Modifier.weight(1f))
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item {
                    Row {
                        listOf("default", "fairyfloss", "poseidon", "peacefulplains").forEach { t ->
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(
                                    when(t){
                                        "fairyfloss" -> Color(0xFFFF8FC9)
                                        "poseidon" -> Color(0xFF38BDF8)
                                        "peacefulplains" -> Color(0xFF4ADE80)
                                        else -> Color(0xFFA855F7)
                                    }
                                ).border(2.dp, if(profile.settings.appearance.theme == t) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                                .clickable { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(theme = t))) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            }

            if (selectedTab == 3) { // AI
                item { Text(text = "AI Helper Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    val context = LocalContext.current
                    val prefs = remember { PreferenceManager(context) }
                    var keyDraft by remember { mutableStateOf(prefs.getGeminiKey() ?: "") }
                    Column {
                        BunkerInputSimple(label = "API Key", value = keyDraft, onValueChange = { keyDraft = it })
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { prefs.saveGeminiKey(keyDraft) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("Save API Key")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
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
fun BunkerInputSimple(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                Text(text = "Enter $label...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun BunkerDensityPicker(current: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        listOf("compact", "default", "spacious").forEach { d ->
            BunkerOptionSmall(d, current == d, { onSelect(d) }, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun BunkerOptionSmall(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

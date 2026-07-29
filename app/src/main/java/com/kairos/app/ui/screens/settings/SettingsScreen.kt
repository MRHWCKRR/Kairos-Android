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
    var selectedTab by remember { mutableIntStateOf(2) } 
    val tabs = listOf("Personal", "Accessibility", "Appearance", "AI Engine")

    // DRAFT STATES (At top for stability and access)
    var draftName by remember(profile.settings.profile.displayName) { mutableStateOf(profile.settings.profile.displayName) }
    var draftAvatar by remember(profile.settings.profile.avatarURL) { mutableStateOf(profile.settings.profile.avatarURL) }
    var draftBirthday by remember(profile.settings.profile.birthday) { mutableStateOf(profile.settings.profile.birthday) }
    var draftTimezone by remember(profile.settings.profile.timezone) { mutableStateOf(profile.settings.profile.timezone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            if (selectedTab == 0) { // PERSONAL
                item { Text(text = "Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
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

                item { SafeInputFieldFlat(label = "Display Name", value = draftName, onValueChange = { draftName = it }) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                item { SafeInputFieldFlat(label = "Avatar URL", value = draftAvatar, onValueChange = { draftAvatar = it }) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                item { SafeInputFieldFlat(label = "Birthday", value = draftBirthday, onValueChange = { draftBirthday = it }) }
                item { Spacer(modifier = Modifier.height(24.dp)) }

                item {
                    Button(
                        onClick = {
                            mainViewModel.updateSettings(profile.settings.copy(
                                profile = profile.settings.profile.copy(
                                    displayName = draftName,
                                    avatarURL = draftAvatar,
                                    birthday = draftBirthday,
                                    timezone = draftTimezone
                                )
                            ))
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Profile Changes")
                    }
                }
            }

            if (selectedTab == 1) { // ACCESSIBILITY
                item { Text(text = "Accessibility", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                item { Text(text = "Density", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("compact", "default", "spacious").forEach { d ->
                            OptionTileLocked(
                                text = d, 
                                selected = profile.settings.accessibility.density == d,
                                onClick = { mainViewModel.updateSettings(profile.settings.copy(accessibility = profile.settings.accessibility.copy(density = d))) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }

            if (selectedTab == 2) { // APPEARANCE
                item { Text(text = "Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                item { Text(text = "Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OptionTileLocked("dark", profile.settings.appearance.mode == "dark", { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "dark"))) }, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        OptionTileLocked("light", profile.settings.appearance.mode == "light", { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "light"))) }, Modifier.weight(1f))
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                item { Text(text = "Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                item { Spacer(modifier = Modifier.height(12.dp)) }
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
                item { Text(text = "AI Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item {
                    val context = LocalContext.current
                    val prefs = remember { PreferenceManager(context) }
                    var draftKey by remember { mutableStateOf(prefs.getGeminiKey() ?: "") }
                    SafeInputFieldFlat(label = "Gemini API Key", value = draftKey, onValueChange = { draftKey = it })
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { prefs.saveGeminiKey(draftKey) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Save API Key")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun SafeInputFieldFlat(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true
            )
            if (value.isEmpty()) {
                Text(text = "...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun OptionTileLocked(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(48.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text.replaceFirstChar { it.uppercase() }, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }
}

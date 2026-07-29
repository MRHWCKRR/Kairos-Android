package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
            // Personal Tab
            if (selectedTab == 0) {
                item { Text(text = "Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                
                item {
                    var draftAvatar by remember(profile.settings.profile.avatarURL) { mutableStateOf(profile.settings.profile.avatarURL) }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(80.dp)) {
                            if (draftAvatar.isNotBlank()) {
                                AsyncImage(
                                    model = draftAvatar,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
                
                item { 
                    SettingsFieldThemeAware(
                        label = "Display Name", 
                        value = profile.settings.profile.displayName, 
                        onValueChange = { mainViewModel.updateSettings(profile.settings.copy(profile = profile.settings.profile.copy(displayName = it))) }
                    ) 
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                item { 
                    SettingsFieldThemeAware(
                        label = "Profile Picture URL", 
                        value = profile.settings.profile.avatarURL, 
                        onValueChange = { mainViewModel.updateAvatar(it) }
                    ) 
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                item { 
                    SettingsFieldThemeAware(
                        label = "Birthday", 
                        value = profile.settings.profile.birthday, 
                        onValueChange = { mainViewModel.updateSettings(profile.settings.copy(profile = profile.settings.profile.copy(birthday = it))) }
                    ) 
                }
            }

            // Accessibility Tab
            if (selectedTab == 1) {
                item { Text(text = "Accessibility", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                item { Text(text = "Layout Density", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("compact", "default", "spacious").forEach { density ->
                            OptionTileSmall(
                                text = density,
                                selected = profile.settings.accessibility.density == density,
                                onClick = { mainViewModel.updateSettings(profile.settings.copy(accessibility = profile.settings.accessibility.copy(density = density))) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }

            // Appearance Tab
            if (selectedTab == 2) {
                item {
                    Text(
                        text = "Appearance", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Mode", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OptionTileSmall(
                            text = "Dark",
                            selected = profile.settings.appearance.mode == "dark",
                            onClick = { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "dark"))) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OptionTileSmall(
                            text = "Light",
                            selected = profile.settings.appearance.mode == "light",
                            onClick = { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(mode = "light"))) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Theme", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
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
                                    .border(
                                        width = 2.dp,
                                        color = if (profile.settings.appearance.theme == id) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(theme = id))) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            }

            // AI Tab
            if (selectedTab == 3) {
                item { Text(text = "AI Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { Text(text = "Connect your Gemini API key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item {
                    val context = LocalContext.current
                    val prefs = remember { PreferenceManager(context) }
                    var key by remember { mutableStateOf(prefs.getGeminiKey() ?: "") }
                    Column {
                        OutlinedTextField(
                            value = key, 
                            onValueChange = { key = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { 
                            prefs.saveGeminiKey(key) 
                            android.widget.Toast.makeText(context, "Key Saved!", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Save Key")
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun SettingsFieldThemeAware(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
fun OptionTileSmall(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.replaceFirstChar { it.uppercase() }, 
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground, 
                fontSize = 13.sp
            )
        }
    }
}

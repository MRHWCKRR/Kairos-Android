package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.kairos.app.ui.theme.*

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Personal", "Accessibility", "Appearance", "AI Engine")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 24.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(text = title) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                when (selectedTab) {
                    0 -> PersonalSettingsTab(profile.settings, mainViewModel)
                    1 -> AccessibilitySettingsTab(profile.settings, mainViewModel)
                    2 -> AppearanceSettingsTab(profile.settings, mainViewModel)
                    3 -> AiEngineSettingsTab(profile.settings, mainViewModel)
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun PersonalSettingsTab(settings: KairosSettings, viewModel: MainViewModel) {
    Column {
        Text(text = "Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        SettingsTextField(
            label = "Display Name",
            value = settings.profile.displayName,
            onValueChange = { viewModel.updateSettings(settings.copy(profile = settings.profile.copy(displayName = it))) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Profile Picture", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (settings.profile.avatarURL.isNotBlank()) {
                AsyncImage(
                    model = settings.profile.avatarURL,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedTextField(
                value = settings.profile.avatarURL,
                onValueChange = { viewModel.updateAvatar(it) },
                placeholder = { Text("Enter Image URL", color = TextMuted) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = BgCard,
                    focusedContainerColor = BgCard,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsTextField(
            label = "Birthday",
            value = settings.profile.birthday,
            placeholder = "YYYY-MM-DD",
            onValueChange = { viewModel.updateSettings(settings.copy(profile = settings.profile.copy(birthday = it))) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsTextField(
            label = "Timezone",
            value = settings.profile.timezone,
            onValueChange = { viewModel.updateSettings(settings.copy(profile = settings.profile.copy(timezone = it))) }
        )
    }
}

@Composable
fun AccessibilitySettingsTab(settings: KairosSettings, viewModel: MainViewModel) {
    Column {
        Text(text = "Accessibility", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Layout Density", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("compact", "default", "spacious").forEach { density ->
                OptionTile(
                    text = density.replaceFirstChar { it.uppercase() },
                    selected = settings.accessibility.density == density,
                    onClick = { viewModel.updateSettings(settings.copy(accessibility = settings.accessibility.copy(density = density))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Time Format", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("12", "24").forEach { format ->
                OptionTile(
                    text = "$format-hour",
                    selected = settings.accessibility.timeFormat == format,
                    onClick = { viewModel.updateSettings(settings.copy(accessibility = settings.accessibility.copy(timeFormat = format))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AppearanceSettingsTab(settings: KairosSettings, viewModel: MainViewModel) {
    Column {
        Text(text = "Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Mode", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark", "light").forEach { mode ->
                OptionTile(
                    text = mode.replaceFirstChar { it.uppercase() },
                    selected = settings.appearance.mode == mode,
                    onClick = { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(mode = mode))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Theme", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "default" to AccentGlow,
                "fairyfloss" to ThemeFairyFloss,
                "poseidon" to ThemePoseidon,
                "peacefulplains" to ThemePeacefulPlains
            ).forEach { (id, color) ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = 2.dp,
                            color = if (settings.appearance.theme == id) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(theme = id))) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Font", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sans", "round", "mono").forEach { font ->
                OptionTile(
                    text = font.replaceFirstChar { it.uppercase() },
                    selected = settings.appearance.font == font,
                    onClick = { viewModel.updateSettings(settings.copy(appearance = settings.appearance.copy(font = font))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AiEngineSettingsTab(settings: KairosSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    var geminiKey by remember { mutableStateOf(preferenceManager.getGeminiKey() ?: "") }

    Column {
        Text(text = "AI Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Connect your own Gemini API key to power AI features.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = geminiKey,
            onValueChange = { geminiKey = it },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = BgCard,
                focusedContainerColor = BgCard,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                preferenceManager.saveGeminiKey(geminiKey)
                android.widget.Toast.makeText(context, "Key Saved!", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Key")
        }
    }
}

@Composable
fun SettingsTextField(label: String, value: String, placeholder: String = "", onValueChange: (String) -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = placeholder, color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = BgCard,
                focusedContainerColor = BgCard,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            ),
            singleLine = true
        )
    }
}

@Composable
fun OptionTile(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else BgCard,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}

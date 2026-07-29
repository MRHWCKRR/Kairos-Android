package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.theme.*

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    var selectedTab by remember { mutableStateOf(2) } // Default to Appearance for testing
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
            color = Color.White,
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
                    text = { Text(text = title, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            if (selectedTab == 2) { // Step 4: Re-add Appearance
                item {
                    Text(text = "Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(text = "Mode", style = MaterialTheme.typography.labelMedium, color = TextMuted)
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

                    Text(text = "Theme", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "default" to AccentGlow,
                            "fairyfloss" to ThemeFairyFloss,
                            "poseidon" to ThemePoseidon,
                            "peacefulplains" to ThemePeacefulPlains
                        ).forEach { (id, color) ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = 2.dp,
                                        color = if (profile.settings.appearance.theme == id) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { mainViewModel.updateSettings(profile.settings.copy(appearance = profile.settings.appearance.copy(theme = id))) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            } else {
                item {
                    Text(text = "Placeholder for ${tabs[selectedTab]}", color = Color.Gray)
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun OptionTileSmall(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = if (selected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 13.sp)
        }
    }
}

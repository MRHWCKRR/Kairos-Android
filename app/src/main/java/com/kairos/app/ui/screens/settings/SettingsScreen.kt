package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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

        // --- CONTENT AREA (Step 2: Add Name Input) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            if (selectedTab == 0) {
                Text(text = "Profile Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(32.dp))
                
                // Static Avatar Placeholder
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(80.dp).background(Color.Gray.copy(alpha = 0.2f), CircleShape))
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                // Interactive Name Input (Draft state)
                var nameDraft by remember(profile.settings.profile.displayName) { 
                    mutableStateOf(profile.settings.profile.displayName) 
                }
                
                Text(text = "Display Name", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Static Placeholders for the rest
                repeat(2) {
                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                Button(
                    onClick = {
                        mainViewModel.updateSettings(profile.settings.copy(
                            profile = profile.settings.profile.copy(displayName = nameDraft)
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Changes")
                }
            } else {
                Text(text = "Tab: ${tabs[selectedTab]}", color = MaterialTheme.colorScheme.onBackground)
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

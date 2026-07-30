package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
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

        // Simple TabRow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
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
                                    color = Color(0xFFA855F7), // Primary accent
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

        // --- PLACEHOLDER CONTENT ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Body Content for: ${tabs[selectedTab]}",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

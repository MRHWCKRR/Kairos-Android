package com.kairos.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun SettingsScreen(mainViewModel: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Personal", "Accessibility", "Appearance", "AI Engine")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Step 2: Add Header and Tabs back
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

        // Content (Scrollable)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                Text(text = "Body Content for: ${tabs[selectedTab]}", color = Color.Gray)
                Spacer(modifier = Modifier.height(20.dp))
                // Add a simple button to test interaction
                Button(onClick = { /* test */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Test Button")
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

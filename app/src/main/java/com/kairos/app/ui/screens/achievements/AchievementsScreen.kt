package com.kairos.app.ui.screens.achievements

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.*
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.utils.ConfettiCannon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(mainViewModel: MainViewModel = viewModel()) {
    val profile by mainViewModel.profile.collectAsState()
    val scrollState = rememberScrollState()
    var showConfetti by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Achievements",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Goal Selection Section
                Text(
                    text = "Active Goals",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pick 3 goals to track on your dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { index ->
                        GoalSelector(
                            selectedId = profile.achievements.goals.getOrNull(index),
                            onSelect = { mainViewModel.setGoal(index, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Achievement Categories
                AchievementCategoryBlock("Locked In Time", "focus", profile)
                Spacer(modifier = Modifier.height(40.dp))
                AchievementCategoryBlock("Tasks Completed", "tasks", profile)
                Spacer(modifier = Modifier.height(40.dp))
                AchievementCategoryBlock("Milestones", "misc", profile)
                
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            ConfettiCannon(
                trigger = showConfetti,
                onFinished = { showConfetti = false }
            )
        }
    }

    // Achievement Unlock Overlay
    mainViewModel.newlyUnlockedAchievement?.let { def ->
        if (profile.settings.appearance.confetti) {
            LaunchedEffect(def.id) { showConfetti = true }
        }
        AchievementUnlockOverlay(def = def, onDismiss = { mainViewModel.dismissAchievement() })
    }
}

@Composable
fun GoalSelector(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val trackable = KAIROS_ACHIEVEMENTS.filter { it.type != "event" }
    val selected = KAIROS_ACHIEVEMENTS.find { it.id == selectedId }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(80.dp),
            border = BorderStroke(1.dp, if (selected != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selected != null) {
                    Text(text = selected.icon, fontSize = 24.sp)
                    Text(
                        text = selected.name, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 1, 
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(text = "➕", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 16.sp)
                    Text(text = "Select", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); showMenu = false })
            trackable.forEach { def ->
                DropdownMenuItem(
                    text = { Text("${def.icon} ${def.name}") },
                    onClick = { onSelect(def.id); showMenu = false }
                )
            }
        }
    }
}

@Composable
fun AchievementCategoryBlock(
    title: String,
    category: String,
    profile: KairosUserProfile
) {
    val items = KAIROS_ACHIEVEMENTS.filter { it.category == category }
    
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        val chunked = items.chunked(2)
        chunked.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowItems.forEach { def ->
                    BadgeCard(
                        def = def,
                        profile = profile,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size < 2) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BadgeCard(
    def: AchievementDef,
    profile: KairosUserProfile,
    modifier: Modifier = Modifier
) {
    val unlocked = profile.achievements.unlocked.containsKey(def.id)
    val progress = remember(def, profile) {
        when (def.type) {
            "focus_seconds" -> profile.focusData.totalSeconds.toFloat() / def.threshold
            "tasks_completed" -> profile.achievements.lifetimeTasksCompleted.toFloat() / def.threshold
            else -> if (unlocked) 1f else 0f
        }
    }

    Card(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (unlocked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = def.icon, fontSize = 32.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = def.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = def.desc,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!unlocked && def.type != "event") {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun AchievementUnlockOverlay(
    def: AchievementDef,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Awesome!") }
        },
        title = {
            Text(text = "🎉 Achievement Unlocked!", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = def.icon, fontSize = 40.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = def.name, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 20.sp, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = def.desc, 
                    textAlign = TextAlign.Center, 
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    )
}

package com.kairos.app.ui.screens.discovery

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosSharedRoutine
import com.kairos.app.ui.components.ProfileImage
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun DiscoveryScreen(
    mainViewModel: MainViewModel,
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    val allRoutines by discoveryViewModel.routines.collectAsState()
    val user by mainViewModel.user.collectAsState()
    val context = LocalContext.current
    val errorMessage = discoveryViewModel.errorMessage
    val isRefreshing = discoveryViewModel.isRefreshing
    val showOnlyMyRoutines = discoveryViewModel.showOnlyMyRoutines

    val filteredRoutines = remember(allRoutines, showOnlyMyRoutines, user) {
        if (showOnlyMyRoutines) {
            allRoutines.filter { it.creatorId == user?.uid }
        } else {
            allRoutines
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Discovery",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // Feed Toggle
            Row(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(4.dp)
            ) {
                FilterChip(
                    selected = !showOnlyMyRoutines,
                    onClick = { discoveryViewModel.toggleFilter(false) },
                    label = { Text("Global", fontSize = 10.sp) },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                )
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                    selected = showOnlyMyRoutines,
                    onClick = { discoveryViewModel.toggleFilter(true) },
                    label = { Text("My Shared", fontSize = 10.sp) },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                )
            }
        }

        if (errorMessage != null) {
            DiscoveryErrorView(errorMessage) { discoveryViewModel.loadRoutines() }
        } else if (isRefreshing && allRoutines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    MarketplaceHeader()
                }

                if (filteredRoutines.isEmpty() && !isRefreshing) {
                    item {
                        Text(
                            text = if (showOnlyMyRoutines) "You haven't shared any routines yet." else "No shared routines found yet.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(filteredRoutines) { routine ->
                        RoutineMarketplaceCard(
                            routine = routine,
                            isOwner = routine.creatorId == user?.uid,
                            onAdopt = {
                                user?.uid?.let { uid ->
                                    discoveryViewModel.adoptRoutine(routine, uid)
                                    mainViewModel.importRoutine(routine)
                                    Toast.makeText(context, "Routine Adopted!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = { discoveryViewModel.deleteRoutine(routine.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Oops!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Composable
fun MarketplaceHeader() {
    Column {
        Text(
            text = "Top Communities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("Deep Work", "Lofi Lovers", "Students", "Creative Flow").forEach { cat ->
                AssistChip(
                    onClick = { },
                    label = { Text(cat) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
fun RoutineMarketplaceCard(
    routine: KairosSharedRoutine,
    isOwner: Boolean,
    onAdopt: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileImage(
                    imageUrl = routine.creatorAvatar,
                    userName = routine.creatorName,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isOwner) "You" else routine.creatorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isOwner) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.weight(1f))
                
                if (isOwner) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                } else {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4B91), modifier = Modifier.size(16.dp))
                    Text(text = " ${routine.likes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = routine.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = routine.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (!isOwner) {
                Button(
                    onClick = onAdopt,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to My Kairos")
                }
            } else {
                OutlinedButton(
                    onClick = { /* Edit logic would go here */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Description")
                }
            }
        }
    }
}

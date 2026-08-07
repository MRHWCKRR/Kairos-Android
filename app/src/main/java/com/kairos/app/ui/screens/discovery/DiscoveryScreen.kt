package com.kairos.app.ui.screens.discovery

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val searchQuery = discoveryViewModel.searchQuery
    val filterCreatorId = discoveryViewModel.filterCreatorId

    var routineToDelete by remember { mutableStateOf<KairosSharedRoutine?>(null) }
    var routineToEdit by remember { mutableStateOf<KairosSharedRoutine?>(null) }
    var routineToPreview by remember { mutableStateOf<KairosSharedRoutine?>(null) }

    val filteredRoutines = remember(allRoutines, showOnlyMyRoutines, user, searchQuery, filterCreatorId) {
        allRoutines.filter { routine ->
            val matchesSearch = routine.title.contains(searchQuery, ignoreCase = true) || 
                              routine.description.contains(searchQuery, ignoreCase = true) ||
                              routine.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCreator = filterCreatorId == null || routine.creatorId == filterCreatorId
            
            val matchesTab = if (showOnlyMyRoutines) routine.creatorId == user?.uid else true
            
            matchesSearch && matchesCreator && matchesTab
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- HEADER & SEARCH ---
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovery",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                // Refined Toggle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TabToggleItem("Global", !showOnlyMyRoutines) { discoveryViewModel.toggleFilter(false); discoveryViewModel.filterByCreator(null) }
                        TabToggleItem("My Shared", showOnlyMyRoutines) { discoveryViewModel.toggleFilter(true); discoveryViewModel.filterByCreator(null) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { discoveryViewModel.onSearchQueryChange(it) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search community boards...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty() || filterCreatorId != null) {
                        IconButton(onClick = { 
                            discoveryViewModel.onSearchQueryChange("")
                            discoveryViewModel.filterByCreator(null)
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            if (filterCreatorId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { discoveryViewModel.filterByCreator(null) },
                    label = { Text("Filter: Creator Profile") },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) },
                    shape = RoundedCornerShape(8.dp)
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
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                item { MarketplaceHeader() }

                if (filteredRoutines.isEmpty() && !isRefreshing) {
                    item {
                        Text(
                            text = if (showOnlyMyRoutines) "You haven't shared any boards yet." else "No boards match your search.",
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
                                    Toast.makeText(context, "Board Adopted!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = { routineToDelete = routine },
                            onEdit = { routineToEdit = routine },
                            onPreview = { routineToPreview = routine },
                            onCreatorClick = { discoveryViewModel.filterByCreator(routine.creatorId) },
                            onLike = { discoveryViewModel.toggleLike(routine.id, routine.likes) }
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    // --- DIALOGS ---

    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete Shared Board?") },
            text = { Text("This will remove '${routine.title}' from the community marketplace. Your personal boards will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    discoveryViewModel.deleteRoutine(routine.id)
                    routineToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) { Text("Cancel") }
            }
        )
    }

    routineToEdit?.let { routine ->
        var descDraft by remember { mutableStateOf(routine.description) }
        var catDraft by remember { mutableStateOf(routine.category) }
        val categories = listOf("Deep Work", "Student", "Lofi Lovers", "Creative Flow", "Health")

        AlertDialog(
            onDismissRequest = { routineToEdit = null },
            title = { Text("Edit Board Details") },
            text = {
                Column {
                    Text(text = routine.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = descDraft,
                        onValueChange = { descDraft = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Category", style = MaterialTheme.typography.labelSmall)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(catDraft) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat) }, onClick = { catDraft = cat; expanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    discoveryViewModel.updateRoutine(routine.id, descDraft, catDraft)
                    routineToEdit = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { routineToEdit = null }) { Text("Cancel") }
            }
        )
    }

    if (routineToPreview != null) {
        Dialog(
            onDismissRequest = { routineToPreview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoardPreviewContent(
                    routine = routineToPreview!!,
                    onClose = { routineToPreview = null },
                    onAdopt = {
                        user?.uid?.let { uid ->
                            discoveryViewModel.adoptRoutine(routineToPreview!!, uid)
                            mainViewModel.importRoutine(routineToPreview!!)
                            Toast.makeText(context, "Board Adopted!", Toast.LENGTH_SHORT).show()
                            routineToPreview = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TabToggleItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun BoardPreviewContent(routine: KairosSharedRoutine, onClose: () -> Unit, onAdopt: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            Text("Board Preview", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileImage(routine.creatorAvatar, Modifier.size(48.dp), routine.creatorName)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = routine.creatorName, fontWeight = FontWeight.Bold)
                    Text(text = "Routine Creator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = routine.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = routine.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = routine.description, style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(40.dp))
            Text(text = "What's Inside", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            routine.boards.firstOrNull()?.sections?.forEach { section ->
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(text = section.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    section.tasks.forEach { task ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = task.title)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onAdopt,
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Add this Board to my Kairos", fontWeight = FontWeight.Bold)
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
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onCreatorClick: () -> Unit,
    onLike: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPreview() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clickable { onCreatorClick() }) {
                    ProfileImage(routine.creatorAvatar, Modifier.size(32.dp), routine.creatorName)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isOwner) "You" else routine.creatorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isOwner) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onCreatorClick() }
                )
                Spacer(modifier = Modifier.weight(1f))
                
                if (isOwner) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                } else {
                    IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4B91), modifier = Modifier.size(16.dp))
                    }
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isOwner) {
                    Button(
                        onClick = onAdopt,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adopt", fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit", fontSize = 13.sp)
                    }
                }
                
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Preview", fontSize = 13.sp)
                }
            }
        }
    }
}

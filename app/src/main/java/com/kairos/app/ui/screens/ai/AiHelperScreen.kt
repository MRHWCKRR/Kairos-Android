package com.kairos.app.ui.screens.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.ChatMessage
import com.kairos.app.ui.navigation.MainViewModel

@Composable
fun AiHelperScreen(
    mainViewModel: MainViewModel,
    viewModel: AiHelperViewModel = viewModel()
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val plan by mainViewModel.plan.collectAsState()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- REFINED AI COACH HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Coach",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("📋 Create Board from Chat") },
                            onClick = {
                                showMenu = false
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.createPlanFromChat(plan)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("✨ New Chat") },
                            onClick = {
                                showMenu = false
                                viewModel.clearChat()
                            }
                        )
                    }
                }
            }

            // --- CHAT FEED ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (chatMessages.isEmpty()) {
                    item { ChatEmptyState() }
                }

                items(chatMessages) { message ->
                    ChatBubble(message)
                }

                if (viewModel.isLoading && !viewModel.showConfirmationDialog) {
                    item { TypingIndicator() }
                }
            }

            // --- INPUT BAR ---
            ChatInputBar(
                value = viewModel.userInput,
                onValueChange = { viewModel.userInput = it },
                onSend = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.handleSend()
                },
                isLoading = viewModel.isLoading
            )
        }
    }

    // --- LOADING OVERLAY ---
    if (viewModel.isLoading && viewModel.userInput.isEmpty() && chatMessages.isNotEmpty()) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generating your board...", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (viewModel.showConfirmationDialog) {
        AiPlanConfirmationDialog(
            viewModel = viewModel,
            existingBoards = plan?.boards ?: emptyList(),
            onConfirm = { 
                val updatedPlan = viewModel.onConfirm(plan)
                mainViewModel.updatePlan(updatedPlan)
            }
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            tonalElevation = 2.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Text(
            text = if (isUser) "You" else "Coach",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text("Ask your coach anything...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                if (isLoading && value.isNotEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ChatEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your AI Study Coach",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Ask me anything — or tell me about an assignment.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        ) {
            Text(
                text = "...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlanConfirmationDialog(
    viewModel: AiHelperViewModel,
    existingBoards: List<com.kairos.app.data.models.KairosBoard>,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        title = { Text("Configure Your New Board") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Where should we add these tasks?", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = viewModel.targetBoardMode == TargetBoardMode.NEW,
                        onClick = { viewModel.targetBoardMode = TargetBoardMode.NEW }
                    )
                    Text("A new board", modifier = Modifier.clickable { viewModel.targetBoardMode = TargetBoardMode.NEW })
                }
                
                if (viewModel.targetBoardMode == TargetBoardMode.NEW) {
                    OutlinedTextField(
                        value = viewModel.newBoardName,
                        onValueChange = { viewModel.newBoardName = it },
                        placeholder = { Text("Board name (e.g. Math Study)") },
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                        singleLine = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = viewModel.targetBoardMode == TargetBoardMode.EXISTING,
                        onClick = { viewModel.targetBoardMode = TargetBoardMode.EXISTING },
                        enabled = existingBoards.isNotEmpty()
                    )
                    Text(
                        text = "An existing board", 
                        modifier = Modifier.clickable(enabled = existingBoards.isNotEmpty()) { 
                            viewModel.targetBoardMode = TargetBoardMode.EXISTING 
                        },
                        color = if (existingBoards.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Color.Unspecified
                    )
                }
                
                if (viewModel.targetBoardMode == TargetBoardMode.EXISTING) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(start = 32.dp)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = existingBoards.find { it.id == viewModel.selectedExistingBoardId }?.title ?: "Select Board")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            existingBoards.forEach { board ->
                                DropdownMenuItem(
                                    text = { Text(board.title) },
                                    onClick = {
                                        viewModel.selectedExistingBoardId = board.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        if (viewModel.selectedExistingBoardId.isEmpty() && existingBoards.isNotEmpty()) {
                            viewModel.selectedExistingBoardId = existingBoards.first().id
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // --- TASK PREVIEW ---
                Text(
                    text = "BOARD PREVIEW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                viewModel.pendingResponse?.sections?.forEach { section ->
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(text = section.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        section.tasks.forEach { task ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = task.title, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Add to Boards")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text("Cancel")
            }
        }
    )
}

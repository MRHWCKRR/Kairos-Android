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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.ChatMessage
import com.kairos.app.ui.components.PremiumCard
import com.kairos.app.ui.components.KairosStateMessage
import com.kairos.app.ui.components.KairosErrorFeedback
import com.kairos.app.ui.components.KairosPageHeader
import com.kairos.app.ui.components.KairosOverflowMenu
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.utils.MarkdownUtils

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                KairosPageHeader("Kairos AI") {
                    KairosOverflowMenu("Chat actions") { dismiss ->
                        DropdownMenuItem(
                            text = { Text("Create board") },
                            onClick = {
                                dismiss()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.createPlanFromChat(plan)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear chat") },
                            onClick = { dismiss(); viewModel.clearChat() }
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (chatMessages.isEmpty()) {
                    item { ChatEmptyState() }
                }

                items(chatMessages) { message ->
                    ChatBubble(message)
                }

                if (viewModel.isLoading && !viewModel.isGeneratingBoard && !viewModel.showConfirmationDialog) {
                    item { TypingIndicator() }
                }
            }
            viewModel.errorMessage?.let { message ->
                KairosErrorFeedback(message, onDismiss = { viewModel.errorMessage = null })
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
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
    }

    // --- LOADING OVERLAY ---
    if (viewModel.isGeneratingBoard) {
        Dialog(onDismissRequest = {}) {
            PremiumCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Architecting your board...", style = MaterialTheme.typography.titleMedium)
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
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)) else null
        ) {
            Text(
                text = MarkdownUtils.parseMarkdown(message.content),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = if (isUser) "You" else "Kairos AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = if (isUser) 0.dp else 4.dp, end = if (isUser) 4.dp else 0.dp)
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 5,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text("Ask anything...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )
            }
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                ),
                modifier = Modifier.size(48.dp)
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
    KairosStateMessage(
        title = "Kairos AI",
        message = "Ask about your tasks or plan your next assignment."
    )
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


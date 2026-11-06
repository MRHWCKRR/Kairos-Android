package com.kairos.app.ui.screens.ai

import com.kairos.app.ui.components.KairosOutlinedButton
import com.kairos.app.ui.components.KairosTextButton
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.ChatMessage
import com.kairos.app.ui.components.PremiumCard
import com.kairos.app.ui.components.KairosErrorFeedback
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
    val haptics = LocalHapticFeedback.current

    AiConversationContent(
        messages = chatMessages,
        draft = viewModel.userInput,
        isLoading = viewModel.isLoading,
        showThinking = viewModel.isLoading && !viewModel.isGeneratingBoard && !viewModel.showConfirmationDialog,
        error = viewModel.errorMessage,
        onDraftChange = { viewModel.userInput = it },
        onSend = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.handleSend()
        },
        onClear = { viewModel.clearChat() },
        onCreateBoard = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.createPlanFromChat(plan)
        },
        onDismissError = { viewModel.errorMessage = null }
    )

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
                    Text("Preparing plan…", style = MaterialTheme.typography.titleMedium)
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

/** The production conversation surface, independent of authentication and persistence. */
@Composable
fun AiConversationContent(
    messages: List<ChatMessage>,
    draft: String,
    isLoading: Boolean,
    error: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onCreateBoard: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    showThinking: Boolean = isLoading
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, showThinking) {
        val count = messages.size.coerceAtLeast(1) + if (showThinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Text(
                "Kairos AI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
                KairosOverflowMenu("Chat actions") { dismiss ->
                    DropdownMenuItem(
                        text = { Text("Create board") },
                        enabled = messages.isNotEmpty() && !isLoading,
                        onClick = { dismiss(); onCreateBoard() }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear chat") },
                        enabled = messages.isNotEmpty() && !isLoading,
                        onClick = { dismiss(); onClear() }
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (messages.isEmpty()) item { ChatEmptyState() }
            items(messages) { ChatBubble(it) }
            if (showThinking) item { TypingIndicator() }
        }
        error?.let { KairosErrorFeedback(it, onDismiss = onDismissError) }
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            ChatInputBar(draft, onDraftChange, onSend, isLoading)
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (isUser) Spacer(Modifier.width(40.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                if (isUser) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Text(
                        text = MarkdownUtils.parseMarkdown(message.content),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
                    )
                }
            }
            Text(
                text = if (isUser) "You" else "Kairos AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, start = if (isUser) 0.dp else 2.dp, end = if (isUser) 4.dp else 0.dp)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    val canSend = value.isNotBlank() && !isLoading
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 1,
                maxLines = 6,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) Text(
                            "Message Kairos",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        innerTextField()
                    }
                }
            )
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (canSend) 1f else 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "What can I help with?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            "Study, planning, organisation, and ideas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strokeWidth = 1.5.dp
        )
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = MaterialTheme.colorScheme.secondary),
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
                        KairosOutlinedButton(
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
            KairosTextButton(onClick = { viewModel.dismissDialog() }) {
                Text("Cancel")
            }
        }
    )
}


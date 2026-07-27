package com.kairos.app.ui.screens.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kairos.app.ui.theme.BgCard
import com.kairos.app.ui.theme.TextMuted

@Composable
fun AiHelperScreen(
    mainViewModel: MainViewModel,
    viewModel: AiHelperViewModel = viewModel()
) {
    val plan by mainViewModel.plan.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "AI Helper",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            Text(
                text = "Paste your syllabus or assignment guidelines below.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            OutlinedTextField(
                value = viewModel.userInput,
                onValueChange = { viewModel.userInput = it },
                placeholder = { Text("Paste assignment details here...", color = TextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = BgCard,
                    focusedContainerColor = BgCard,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.onGenerate(plan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !viewModel.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Plan", fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlanConfirmationDialog(
    viewModel: AiHelperViewModel,
    existingBoards: List<com.kairos.app.data.models.KairosBoard>,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDialog() },
        title = { Text("Add AI-generated tasks to:") },
        text = {
            Column {
                // Radio Options
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
                        placeholder = { Text("New board name") },
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
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
                        color = if (existingBoards.isEmpty()) TextMuted else Color.Unspecified
                    )
                }
                
                if (viewModel.targetBoardMode == TargetBoardMode.EXISTING) {
                    // Simple Dropdown placeholder
                    Text(
                        text = "Selected: ${existingBoards.find { it.id == viewModel.selectedExistingBoardId }?.title ?: "Choose a board"}",
                        modifier = Modifier.padding(start = 32.dp).clickable { /* TODO: Show dropdown */ },
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Auto-select first if none selected
                    LaunchedEffect(Unit) {
                        if (viewModel.selectedExistingBoardId.isEmpty() && existingBoards.isNotEmpty()) {
                            viewModel.selectedExistingBoardId = existingBoards.first().id
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preview summary
                Text(
                    text = "Summary:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Text(
                    text = "${viewModel.pendingResponse?.sections?.size ?: 0} sections, " +
                           "${viewModel.pendingResponse?.recurringEvents?.size ?: 0} recurring events",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Add Tasks")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDialog() }) {
                Text("Cancel")
            }
        }
    )
}

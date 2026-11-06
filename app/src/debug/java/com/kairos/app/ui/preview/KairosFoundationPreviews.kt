package com.kairos.app.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.dp
import com.kairos.app.data.models.AppearanceSettings
import com.kairos.app.data.models.ChatMessage
import com.kairos.app.ui.components.*
import com.kairos.app.ui.screens.ai.ChatBubble
import com.kairos.app.ui.screens.ai.ChatInputBar
import com.kairos.app.ui.theme.*

// Preview-only fixtures. These never initialise Firebase or write user data.
enum class FoundationState { Populated, Empty, Loading, Error, Editing }
class FoundationStates : PreviewParameterProvider<FoundationState> {
    override val values = FoundationState.entries.asSequence()
}

@Preview(name = "Light phone", widthDp = 390, heightDp = 844, uiMode = 0x10)
@Preview(name = "Dark phone", widthDp = 390, heightDp = 844, uiMode = 0x20)
@Preview(name = "Small phone, large text", widthDp = 320, heightDp = 640, fontScale = 2f, uiMode = 0x10)
@Preview(name = "Landscape", widthDp = 720, heightDp = 360, uiMode = 0x20)
annotation class KairosPhonePreviews

@Composable
private fun ReferenceFrame(title: String, content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    KairosTheme(AppearanceSettings(mode = if (dark) "dark" else "light")) {
        Surface(color = MaterialTheme.colorScheme.background) {
            KairosScaffold(
                topBar = { Text(title, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) },
                bottomBar = {
                    NavigationBar {
                        listOf("Today", "Tasks", "Kairos AI", "Schedule").forEachIndexed { index, item ->
                            NavigationBarItem(selected = index == 0, onClick = {}, icon = { Text((index + 1).toString()) }, label = { Text(item) })
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
            }
        }
    }
}

@Composable
private fun StateContent(state: FoundationState, content: @Composable () -> Unit) {
    when (state) {
        FoundationState.Loading -> KairosStateMessage("Loading", "Getting your workspace ready.", loading = true)
        FoundationState.Empty -> KairosStateMessage("Nothing here yet", "Add your first task when you are ready.")
        FoundationState.Error -> KairosStateMessage("Could not load", "Your changes are still here. Try again.", actionLabel = "Retry")
        else -> content()
    }
}

@KairosPhonePreviews
@Composable
fun DashboardReference(@PreviewParameter(FoundationStates::class) state: FoundationState) {
    ReferenceFrame("Today") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Welcome back", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Alex", style = MaterialTheme.typography.displaySmall)
            StateContent(state) {
                KairosCard {
                    Text("Focus session", style = MaterialTheme.typography.titleMedium)
                    Text("00:25:00", style = MaterialTheme.typography.displayLarge)
                    PremiumButton(onClick = {}, enabled = state != FoundationState.Editing) { Text("Start focus") }
                }
                Text("Today's tasks", style = MaterialTheme.typography.titleMedium)
                KairosTaskRow("Prepare the assignment outline", false, {}, {})
            }
        }
    }
}

@KairosPhonePreviews
@Composable
fun TaskDetailsReference(@PreviewParameter(FoundationStates::class) state: FoundationState) {
    // Visual contract only: rich task persistence arrives in phase 3.
    ReferenceFrame("Task details") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StateContent(state) {
                var title by remember { mutableStateOf("Prepare the assignment outline") }
                var notes by remember { mutableStateOf("Review the brief and collect sources.") }
                KairosTextField(title, { title = it }, "Title", enabled = state == FoundationState.Editing)
                KairosTextField(notes, { notes = it }, "Notes", enabled = state == FoundationState.Editing)
                Text("Checklist", style = MaterialTheme.typography.titleMedium)
                KairosTaskRow("Read the brief", true, {}, {})
                KairosTaskRow("Write the first draft and check every requirement", false, {}, {})
                KairosButton("Save", {}, enabled = state == FoundationState.Editing)
            }
        }
    }
}

@KairosPhonePreviews
@Composable
fun AIReference(@PreviewParameter(FoundationStates::class) state: FoundationState) {
    ReferenceFrame("Kairos AI") {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                KairosPageHeader("Kairos AI") {
                    KairosOverflowMenu("Chat actions") { dismiss ->
                        DropdownMenuItem(text = { Text("New chat") }, onClick = dismiss)
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StateContent(state) {
                    ChatBubble(ChatMessage("user", "Help me plan my assignment."))
                    ChatBubble(ChatMessage("assistant", "When is it due, and what have you finished so far?"))
                }
            }
            var draft by remember { mutableStateOf(if (state == FoundationState.Editing) "It is due next Friday." else "") }
            Box(Modifier.padding(20.dp)) {
                ChatInputBar(draft, { draft = it }, {}, state == FoundationState.Loading)
            }
        }
    }
}

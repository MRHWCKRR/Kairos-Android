package com.kairos.app.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.dp
import com.kairos.app.data.models.*
import com.kairos.app.ui.components.*
import com.kairos.app.ui.screens.ai.AiConversationContent
import com.kairos.app.ui.screens.dashboard.HomeContent
import com.kairos.app.ui.screens.tasks.BoardCard
import com.kairos.app.ui.screens.tasks.TasksSummary
import com.kairos.app.ui.theme.*
import java.time.ZonedDateTime

// Synthetic preview data renders the production UI without Firebase.
enum class FoundationState { Populated, Empty, Loading, Error, Editing }
class FoundationStates : PreviewParameterProvider<FoundationState> {
    override val values = FoundationState.entries.asSequence()
}
@Preview(name = "Light phone", widthDp = 390, heightDp = 844, uiMode = 0x10)
@Preview(name = "Dark phone", widthDp = 390, heightDp = 844, uiMode = 0x20)
@Preview(name = "Small phone, large text", widthDp = 320, heightDp = 640, fontScale = 2f, uiMode = 0x10)
@Preview(name = "Landscape", widthDp = 720, heightDp = 360, uiMode = 0x20)
annotation class KairosPhonePreviews

private val board = KairosBoard("board", "Assignments", listOf(KairosSection("section", "English", listOf(
    KairosTask("one", "Read the assignment brief", completed = true, date = "2026-11-06"),
    KairosTask("two", "Prepare the first draft", date = "2026-11-06")
))))

@Composable
private fun ReferenceFrame(route: String, content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    KairosTheme(AppearanceSettings(mode = if (dark) "dark" else "light")) {
        Surface(color = MaterialTheme.colorScheme.background) {
            KairosScaffold(bottomBar = { KairosTabDock(route, {}, {}) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
            }
        }
    }
}

@KairosPhonePreviews
@Composable
fun DashboardReference(@PreviewParameter(FoundationStates::class) state: FoundationState) {
    ReferenceFrame("dashboard") {
        when (state) {
            FoundationState.Loading -> KairosStateMessage("Loading", "Getting your workspace ready.", loading = true)
            FoundationState.Error -> KairosStateMessage("Could not load", "Retry loading your planner.", actionLabel = "Retry")
            else -> HomeContent(
                KairosPlan(boards = if (state == FoundationState.Empty) emptyList() else listOf(board)),
                KairosUserProfile(settings = KairosSettings(profile = ProfileSettings(displayName = "Alex"))),
                ZonedDateTime.parse("2026-11-06T09:00:00+10:00[Australia/Brisbane]"), 1500L, false, {}, {}, {}, {}, { _, _ -> }
            )
        }
    }
}

@KairosPhonePreviews
@Composable
fun BoardsReference(@PreviewParameter(FoundationStates::class) state: FoundationState) {
    ReferenceFrame("tasks") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Tasks", style = MaterialTheme.typography.headlineLarge)
            when (state) {
                FoundationState.Loading -> KairosStateMessage("Loading", "Getting your planner ready.", loading = true)
                FoundationState.Error -> KairosStateMessage("Could not load", "Retry loading your planner.", actionLabel = "Retry")
                else -> {
                    TasksSummary(if (state == FoundationState.Empty) emptyList() else listOf(board))
                    if (state == FoundationState.Empty) KairosStateMessage("Your checklist is empty", "Use the + button to create your first board.")
                    else BoardCard(board, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
                    if (state == FoundationState.Editing) KairosTextField("Prepare the first draft", {}, "Task name")
                }
            }
        }
    }
}

@KairosPhonePreviews
@Composable
fun AIReference(@PreviewParameter(FoundationStates::class) state: FoundationState) {
    ReferenceFrame("ai_helper") {
        AiConversationContent(
            messages = if (state == FoundationState.Empty) emptyList() else listOf(
                ChatMessage("user", "Help me plan my assignment."), ChatMessage("assistant", "When is it due?")
            ),
            draft = if (state == FoundationState.Editing) "It is due next Friday." else "",
            isLoading = state == FoundationState.Loading,
            error = if (state == FoundationState.Error) "Could not send. Your draft is still here." else null,
            onDraftChange = {}, onSend = {}, onClear = {}, onCreateBoard = {}, onDismissError = {}
        )
    }
}

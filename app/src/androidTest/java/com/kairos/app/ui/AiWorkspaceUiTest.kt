package com.kairos.app.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.kairos.app.data.models.AppearanceSettings
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kairos.app.data.repository.AiActions
import com.kairos.app.data.repository.AiWorkspace
import com.kairos.app.ui.screens.ai.*
import com.kairos.app.ui.theme.KairosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiWorkspaceUiTest {
    @get:Rule val compose = createComposeRule()
    private var user: Map<String, Any?> = mapOf("aiWorkspace" to mapOf("version" to 2, "chats" to listOf(
        mapOf("id" to "one", "title" to "First chat", "messages" to emptyList<Any>()),
        mapOf("id" to "two", "title" to "Second chat", "messages" to emptyList<Any>())
    ), "projects" to emptyList<Any>()))
    private var ui by mutableStateOf(AiWorkspaceUiState(workspace = AiWorkspace.read(user, "owner"), activeChatId = "one", loading = false))
    private val drafts = mutableMapOf<String, String>()
    private var appearance by mutableStateOf("light")
    private var failSave = false
    private var retainedCommand: AiWorkspace.Command? = null
    private var applied = 0
    private var discarded = 0
    private var created = 0

    private fun edit(command: AiWorkspace.Command) {
        if (failSave) { retainedCommand = command; ui = ui.copy(error = "Could not save project.", retainedChanges = true); return }
        user = user + ("aiWorkspace" to AiWorkspace.apply(user, command, "owner"))
        ui = ui.copy(workspace = AiWorkspace.read(user, "owner"), error = null, retainedChanges = false)
    }

    private fun selectChat(id: String) {
        ui.activeChatId?.let { drafts[it] = ui.draft }
        ui = ui.copy(activeChatId = id, activeProjectId = null, showingAllChats = false, draft = drafts[id].orEmpty())
    }

    private fun show() {
        compose.setContent {
            KairosTheme(AppearanceSettings(mode = appearance)) {
                Surface {
                    AiWorkspaceScreen(ui, AiWorkspaceUiActions(
                        newChat = { project ->
                            val id = "new-${++created}"
                            edit(AiWorkspace.Command.CreateChat(id, project))
                            selectChat(id)
                        },
                        selectChat = ::selectChat,
                        selectProject = { ui = ui.copy(activeProjectId = it, showingAllChats = false) },
                        allChats = { ui = ui.copy(activeProjectId = null, showingAllChats = true) },
                        edit = ::edit,
                        draft = { ui = ui.copy(draft = it) },
                        send = {}, retry = { retainedCommand?.let { edit(it); retainedCommand = null } }, reload = {}, createPlan = {},
                        apply = { applied++; ui = ui.copy(proposal = null) },
                        discard = { discarded++; ui = ui.copy(proposal = null) }, undo = {}
                    ))
                }
            }
        }
    }

    private fun capture(name: String, tag: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "visual-parity")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun projectSaveFailureRetainsEditorThenProjectChatAndDraftSwitchingWork() {
        edit(AiWorkspace.Command.Append("one", listOf(
            AiWorkspace.Message("question", "user", "Help me prepare for my biology exam."),
            AiWorkspace.Message("answer", "assistant", "**Start with recall.** Review cell structure, then test yourself.\n\n```text\nRead → recall → check\n```")
        )))
        show()
        compose.onNodeWithText("Help me prepare for my biology exam.").assertExists()
        capture("ai-workspace-conversation-light", "ai-workspace")
        compose.runOnIdle { appearance = "dark" }
        capture("ai-workspace-conversation-dark", "ai-workspace")
        compose.onNodeWithContentDescription("Chats and projects").performClick()
        compose.runOnIdle { appearance = "light" }
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithTag("ai-project-title").performTextInput("Biology")
        compose.onNodeWithTag("ai-project-description").performTextInput("Exam preparation")
        compose.onNodeWithTag("ai-project-context").performTextInput("Use the course syllabus.")
        compose.runOnIdle { failSave = true }
        compose.onNodeWithText("Save").performClick()
        compose.onNode(hasText("Could not save project.") and hasAnyAncestor(hasTestTag("ai-project-editor"))).assertExists()
        compose.onNodeWithTag("ai-project-title").assertTextContains("Biology")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.runOnIdle { failSave = false }
        compose.onNode(hasText("Retry save") and hasAnyAncestor(hasTestTag("ai-project-editor"))).performScrollTo().performClick()
        compose.onNodeWithTag("ai-project-editor").assertDoesNotExist()
        capture("ai-workspace-history-light", "ai-history")
        compose.runOnIdle { appearance = "dark" }
        capture("ai-workspace-history-dark", "ai-history")
        compose.onNodeWithText("Biology").performClick()
        compose.onNodeWithTag("ai-project-home").assertExists()
        compose.onNodeWithText("Exam preparation").assertExists()
        compose.onNodeWithText("Use the course syllabus.").assertExists()
        capture("ai-workspace-project-dark", "ai-workspace")
        compose.runOnIdle { appearance = "light" }
        capture("ai-workspace-project-light", "ai-workspace")
        compose.onNodeWithText("New chat in project").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Keep this project draft")
        compose.onNodeWithContentDescription("Chats and projects").performClick()
        compose.onNodeWithTag("ai-chat-two").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("two", ui.activeChatId); assertEquals("", ui.draft) }
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNode(hasSetTextAction()).performTextInput("Second draft")
        compose.onNodeWithContentDescription("Chats and projects").performClick()
        compose.onNodeWithTag("ai-chat-new-1").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Keep this project draft")))
        compose.runOnIdle { assertEquals(ui.workspace!!.projects.single().id, ui.workspace!!.chats.first { it.id == "new-1" }.projectId) }
    }

    @Test fun projectEditAndConfirmedDeleteKeepItsChats() {
        edit(AiWorkspace.Command.SaveProject("project", "Biology", "", "Original context", true))
        edit(AiWorkspace.Command.MoveChat("one", "project"))
        ui = ui.copy(activeProjectId = "project")
        show()
        compose.onNodeWithText("Edit project").performClick()
        compose.onNodeWithTag("ai-project-context").performTextReplacement("Updated context")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithTag("ai-project-editor").assertDoesNotExist()
        compose.onNodeWithText("Updated context").assertExists()
        compose.onNodeWithText("Edit project").performClick()
        compose.onNodeWithText("Delete project").performScrollTo().performClick()
        compose.onNodeWithText("Chats will be kept in All chats.").assertExists()
        compose.runOnIdle { assertEquals(1, ui.workspace!!.projects.size) }
        compose.onAllNodesWithText("Delete project").onLast().performClick()
        compose.onNodeWithTag("ai-project-editor").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, ui.workspace!!.projects.size)
            assertEquals(2, ui.workspace!!.chats.size)
            assertEquals("", ui.workspace!!.chats.first { it.id == "one" }.projectId)
        }
    }

    @Test fun proposalShowsSavedBeforeAfterAndRequiresExplicitApplyOrDiscard() {
        val preview = AiActions.Proposal("preview", "owner", "one", "plan", "UTC", 1.0, "fingerprint", "Delete task", emptyList(),
            listOf(AiActions.Row("task", "Read chapter", "delete_task", "Read chapter · Monday", "Removed", true)))
        ui = ui.copy(proposal = preview)
        show()
        compose.onNodeWithText("Review 1 proposed change").performClick()
        compose.onNodeWithTag("ai-proposal-review").assertExists()
        compose.onNodeWithText("Before").assertExists()
        compose.onNodeWithText("Read chapter · Monday").assertExists()
        compose.onNodeWithText("After").assertExists()
        compose.onNodeWithText("Removed").assertExists()
        compose.onNodeWithText("This removes the item or selected occurrence from your planner.").assertExists()
        compose.runOnIdle { assertEquals(0, applied) }
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertEquals(preview, ui.proposal) }
        compose.onNodeWithText("Review 1 proposed change").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithTag("ai-proposal-review").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, applied); ui = ui.copy(proposal = preview.copy(id = "second")) }
        compose.onNodeWithText("Review 1 proposed change").performClick()
        compose.onNodeWithText("Discard changes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, discarded); assertEquals(null, ui.proposal) }
    }
}

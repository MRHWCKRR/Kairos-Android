package com.kairos.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kairos.app.data.models.AppearanceSettings
import com.kairos.app.data.repository.PendingEdit
import com.kairos.app.ui.components.RetainedEditsFeedback
import com.kairos.app.ui.theme.KairosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RetainedEditsFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private var edits by mutableStateOf<List<PendingEdit>>(emptyList())
    private var saving by mutableStateOf(false)
    private var appearance by mutableStateOf("light")
    private val retried = mutableListOf<String>()
    private val discarded = mutableListOf<String>()
    private var retryAllCount = 0
    private var discardAllCount = 0

    private fun edit(id: String, kind: String = "plan", chat: Boolean = false) = PendingEdit(
        id = id, userId = "private-account", kind = kind, documentId = "private-document",
        documentExisted = true, base = emptyMap(),
        edited = if (chat) mapOf("aiChatHistory" to listOf("private chat content")) else emptyMap(),
        rawBaseline = emptyMap()
    )

    private fun show(error: String? = null, retentionError: String? = null, retry: Boolean = true) {
        compose.setContent {
            KairosTheme(AppearanceSettings(mode = appearance)) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        RetainedEditsFeedback(
                            retainedEdits = edits, retentionError = retentionError, errorMessage = error,
                            isSaving = saving, canRetrySave = retry,
                            onRetrySave = { retryAllCount++ }, onDiscardAll = { discardAllCount++ },
                            onRetryEdit = { retried += it }, onDiscardEdit = { discarded += it }
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String, tag: String? = null) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "visual-parity")
        check(directory.isDirectory || directory.mkdirs())
        val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
        File(directory, "$name.png").outputStream().use {
            check(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun restoredEditsAreDiscoverableWithoutAnError() {
        edits = listOf(edit("plan-id"), edit("profile-id", "profile"), edit("chat-id", "profile", chat = true))
        show()
        compose.onNodeWithText("3 retained edits").assertIsDisplayed()
        compose.onNodeWithText("Planner edit").assertDoesNotExist()
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText("Planner edit").assertIsDisplayed()
        compose.onNodeWithText("Settings and progress edit").assertIsDisplayed()
        compose.onNodeWithText("AI chat edit").assertIsDisplayed()
        compose.onNodeWithText("private-account").assertDoesNotExist()
        compose.onNodeWithText("private-document").assertDoesNotExist()
        compose.onNodeWithText("private chat content").assertDoesNotExist()
        capture("retained-edits-review-light", "retained-edits-review")
        compose.runOnIdle { appearance = "dark" }
        capture("retained-edits-review-dark", "retained-edits-review")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Retained edits").assertDoesNotExist()
    }

    @Test fun reviewActionsTargetOnlyTheSelectedRecord() {
        edits = listOf(edit("first"), edit("second", "profile"))
        show(error = "Save failed. Your edits are retained.")
        compose.onNodeWithText("Save failed. Your edits are retained.").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        compose.onAllNodesWithText("Retry")[1].performClick()
        compose.onAllNodesWithText("Discard")[0].performClick()
        compose.runOnIdle {
            assertEquals(listOf("second"), retried)
            assertEquals(listOf("first"), discarded)
            assertEquals(0, discardAllCount)
        }
    }

    @Test fun savingDisablesReviewAndRecordActions() {
        edits = listOf(edit("first"))
        show()
        compose.onNodeWithText("Review").performClick()
        compose.runOnIdle { saving = true }
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithText("Discard").assertIsNotEnabled()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Review").assertIsNotEnabled()
        compose.onNodeWithText("Saving.").assertIsDisplayed()
    }

    @Test fun corruptRetentionOffersExplicitDiscardWithoutRetry() {
        show(retentionError = "private/account/document/journal-corrupt")
        compose.onNodeWithText("Retained edits could not be read. Discard all to clear saved edits on this device.").assertIsDisplayed()
        compose.onNodeWithText("private/account/document/journal-corrupt").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        capture("retained-edits-corrupt-light")
        compose.runOnIdle { appearance = "dark" }
        capture("retained-edits-corrupt-dark")
        compose.onNodeWithText("Discard all").performClick()
        compose.runOnIdle { assertEquals(1, discardAllCount) }
    }

    @Test fun corruptRetentionAlsoDisablesRetryOfReadableRecords() {
        edits = listOf(edit("first"))
        show(retentionError = "Unreadable journal")
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithText("Discard").assertIsEnabled()
    }

    @Test fun memoryOnlyFailureSupportsRetryAndDiscard() {
        show(error = "Save failed. Your edits remain in memory.")
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.runOnIdle {
            assertEquals(1, retryAllCount)
            assertEquals(1, discardAllCount)
        }
        compose.runOnIdle { saving = true }
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithText("Discard").assertIsNotEnabled()
    }

    @Test fun removingLastRecordDismissesReviewAndBanner() {
        edits = listOf(edit("first"))
        show()
        compose.onNodeWithText("1 retained edit").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        compose.runOnIdle { edits = emptyList() }
        compose.onNodeWithText("Retained edits").assertDoesNotExist()
        compose.onNodeWithText("Review").assertDoesNotExist()
    }

    private fun plannerSnapshot(boardTitle: String, taskTitle: String, completed: Boolean = false): Map<String, Any?> = mapOf(
        "boards" to listOf(mapOf(
            "id" to "internal-board-id", "title" to boardTitle,
            "sections" to listOf(mapOf("id" to "internal-section-id", "tasks" to listOf(
                mapOf("id" to "internal-task-id", "title" to taskTitle, "completed" to completed)
            )))
        ))
    )

    @Test fun plannerReviewShowsAffectedTaskOrBoardOnlyAfterReview() {
        val taskTitle = "Prepare a detailed assignment outline and collect supporting references for the final draft"
        val before = plannerSnapshot("Assignments", taskTitle)
        edits = listOf(
            edit("task-change").copy(base = before, edited = plannerSnapshot("Assignments", taskTitle, completed = true)),
            edit("board-change").copy(base = before, edited = plannerSnapshot("Coursework", taskTitle))
        )
        show()
        compose.onNodeWithText(taskTitle).assertDoesNotExist()
        compose.onNodeWithText("Coursework").assertDoesNotExist()
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText(taskTitle).assertIsDisplayed()
        compose.onNodeWithText("Coursework").assertIsDisplayed()
        compose.onNodeWithText("internal-task-id").assertDoesNotExist()
        compose.onNodeWithText("internal-board-id").assertDoesNotExist()
        capture("retained-edits-planner-details-light", "retained-edits-review")
        compose.runOnIdle { appearance = "dark" }
        capture("retained-edits-planner-details-dark", "retained-edits-review")
    }

    @Test fun missingPlannerTitlesNeverFallBackToInternalIdentifiers() {
        val before = plannerSnapshot("", "")
        edits = listOf(edit("private-record-id").copy(base = before, edited = plannerSnapshot("", "", completed = true)))
        show()
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText("Planner edit").assertIsDisplayed()
        compose.onNodeWithText("internal-task-id").assertDoesNotExist()
        compose.onNodeWithText("internal-board-id").assertDoesNotExist()
        compose.onNodeWithText("private-record-id").assertDoesNotExist()
    }
    @Test fun emptyIdleStateShowsNoFeedback() {
        show()
        compose.onNodeWithText("Review").assertDoesNotExist()
        compose.onNodeWithText("Saving.").assertDoesNotExist()
        compose.onNodeWithText("Discard").assertDoesNotExist()
    }
}

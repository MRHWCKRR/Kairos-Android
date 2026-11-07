package com.kairos.app.ui

import androidx.compose.material3.Surface
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
            KairosTheme(AppearanceSettings(mode = "light")) {
                Surface {
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

    @Test fun emptyIdleStateShowsNoFeedback() {
        show()
        compose.onNodeWithText("Review").assertDoesNotExist()
        compose.onNodeWithText("Saving.").assertDoesNotExist()
        compose.onNodeWithText("Discard").assertDoesNotExist()
    }
}

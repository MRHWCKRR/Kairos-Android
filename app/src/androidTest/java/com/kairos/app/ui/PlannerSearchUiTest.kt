package com.kairos.app.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.kairos.app.data.models.*
import com.kairos.app.ui.components.PlannerSearchDialog
import com.kairos.app.ui.theme.KairosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlannerSearchUiTest {
    @get:Rule val compose = createComposeRule()
    private var appearance by mutableStateOf("dark")
    private var scale by mutableStateOf(1f)
    private val fixture = KairosPlan(boards = listOf(KairosBoard("b", "Biology", listOf(
        KairosSection("s", "Assignments", listOf(
            KairosTask("past", "Prepare the first draft", date = "2020-01-01"),
            KairosTask("done", "Read the assignment brief", completed = true)
        ))
    ))))

    private fun show(plan: KairosPlan? = fixture, isLoading: Boolean = false, error: String? = null, onToggle: (String, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                KairosTheme(AppearanceSettings(mode = appearance)) {
                    Surface {
                        PlannerSearchDialog(plan, {}, onToggle, "UTC", isLoading, error)
                    }
                }
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String, tag: String = "planner-search-sheet") {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "visual-parity")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun titleHeight(text: String, sheet: String): Float = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(sheet)), useUnmergedTree = true
    ).fetchSemanticsNode().boundsInRoot.height

    private fun assertLargerTitle(text: String, sheet: String, normalHeight: Float) {
        compose.waitForIdle()
        val largeHeight = titleHeight(text, sheet)
        assertTrue("Title must grow at 2x font scale: normal=$normalHeight, large=$largeHeight", largeHeight > normalHeight * 1.5f)
    }

    @Test fun populatedSearchSupportsAppearanceAndLargeText() {
        show()
        awaitText("3 results")
        capture("search-dark")
        compose.runOnIdle { appearance = "light" }
        capture("search-light")
        val normalHeight = titleHeight("Biology", "planner-search-sheet")
        compose.runOnIdle { scale = 2f }
        assertLargerTitle("Biology", "planner-search-sheet", normalHeight)
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search planner").assertIsDisplayed()
        capture("search-large-text")
        val allHeight = compose.onNodeWithText("All", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        val unfinishedHeight = compose.onNodeWithText("Unfinished", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertEquals("Large-text filters must keep the same single-line height", allHeight, unfinishedHeight, 0.5f)
        compose.onNodeWithText("Completed").performScrollTo().assertIsDisplayed()
    }

    @Test fun queryFiltersClearAndNoMatchUseCurrentResults() {
        show()
        awaitText("3 results")
        compose.onNodeWithContentDescription("Search planner").performTextInput("missing")
        awaitText("No results")
        capture("search-no-match")
        compose.onNodeWithContentDescription("Clear search").performClick()
        awaitText("3 results")
        compose.onNodeWithText("Overdue").performClick()
        awaitText("1 result")
        compose.onNodeWithText("Prepare the first draft").assertExists()
        compose.onNodeWithText("Read the assignment brief").assertDoesNotExist()
        capture("search-overdue")
        compose.onNodeWithText("Completed").performClick()
        awaitText("Read the assignment brief")
    }

    @Test fun boardAndTaskSheetsPreserveSearchAndCompletionCallback() {
        var completedId: String? = null
        var completedValue: Boolean? = null
        show(onToggle = { id, value -> completedId = id; completedValue = value })
        awaitText("3 results")
        compose.onNodeWithText("Biology", substring = false).performClick()
        compose.onNodeWithTag("planner-search-board").assertExists()
        capture("search-board", "planner-search-board")
        val boardTitleHeight = titleHeight("Prepare the first draft", "planner-search-board")
        compose.runOnIdle { scale = 2f }
        assertLargerTitle("Prepare the first draft", "planner-search-board", boardTitleHeight)
        capture("search-board-large-text", "planner-search-board")
        compose.runOnIdle { scale = 1f }
        compose.onNode(hasText("Prepare the first draft") and hasAnyAncestor(hasTestTag("planner-search-board"))).performClick()
        compose.onNodeWithText("Task details").assertExists()
        capture("search-task", "planner-search-task")
        val taskTitleHeight = titleHeight("Prepare the first draft", "planner-search-task")
        compose.runOnIdle { scale = 2f }
        assertLargerTitle("Prepare the first draft", "planner-search-task", taskTitleHeight)
        capture("search-task-large-text", "planner-search-task")
        compose.runOnIdle { scale = 1f }
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("planner-search-task"))).performClick()
        compose.runOnIdle { assertEquals("past", completedId); assertEquals(true, completedValue) }
        compose.onNode(hasText("Done") and hasAnyAncestor(hasTestTag("planner-search-task"))).performClick()
        compose.onNodeWithTag("planner-search-board").assertExists()
        compose.onNode(hasText("Done") and hasAnyAncestor(hasTestTag("planner-search-board"))).performClick()
        awaitText("3 results")
        compose.onNodeWithContentDescription("Search planner").assertExists()
    }

    @Test fun unavailablePlannerExplainsRealError() {
        show(plan = null, error = "Planner could not be loaded.")
        compose.onNodeWithText("Search unavailable").assertExists()
        compose.onNodeWithText("Planner could not be loaded.").assertExists()
        capture("search-unavailable")
    }

    @Test fun loadingPlannerShowsLoadingState() {
        show(plan = null, isLoading = true)
        compose.onNodeWithText("Loading planner").assertExists()
        capture("search-loading")
    }
}

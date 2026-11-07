package com.kairos.app.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.kairos.app.data.models.AppearanceSettings
import com.kairos.app.data.models.KairosChecklistItem
import com.kairos.app.data.models.KairosRecurrenceMarker
import com.kairos.app.data.models.KairosTask
import com.kairos.app.ui.screens.tasks.TaskEditorSheet
import com.kairos.app.ui.theme.KairosTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises the real editor without Firebase authentication or a repository. */
class TaskEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private var appearance by mutableStateOf("light")
    private var error by mutableStateOf<String?>(null)
    private var saving by mutableStateOf(false)
    private var narrow by mutableStateOf(false)
    private var captured: KairosTask? = null
    private var dismissals = 0
    private val fixture = KairosTask(
        id = "task-editor-fixture", title = "Prepare the research proposal", completed = true,
        date = "2026-11-12", archived = true, taskDescription = "Existing description",
        notes = "Outline the research question and methods.", priority = "medium", estimatedMinutes = 45,
        checklist = listOf(KairosChecklistItem("question", "Choose a question", true),
            KairosChecklistItem("sources", "Review sources", false)),
        startTime = "09:30", endTime = "10:15", dueDate = "2026-11-15", dueTime = "17:00",
        reminderMinutes = 15, scheduleLocked = false, schedulingPreference = "morning", parentTaskID = "parent"
    )

    private fun show(
        original: KairosTask = fixture,
        restoration: StateRestorationTester? = null,
        onSave: (KairosTask) -> Unit = { captured = it }
    ) {
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            // A 320dp logical viewport also constrains the sheet's separate dialog window.
            val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val factor = if (narrow) screenWidth / 320f else 1f
            CompositionLocalProvider(LocalDensity provides Density(density.density * factor, density.fontScale)) {
                KairosTheme(AppearanceSettings(mode = appearance)) {
                    Surface {
                        TaskEditorSheet(original, "test:${original.id}", saving, error, true, onSave, { dismissals++ })
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun field(name: String) = compose.onNodeWithTag("task-editor-$name")
    private fun edit(name: String, value: String) {
        field(name).performScrollTo().performTextReplacement(value)
        Espresso.closeSoftKeyboard()
    }
    private fun click(name: String) { field(name).performScrollTo().performClick() }
    private fun save() {
        Espresso.closeSoftKeyboard()
        field("save").assertIsDisplayed().performClick()
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "visual-parity")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(field("sheet").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun editingFieldsSavesTypedValuesAndPreservesTaskIdentityAndUnchangedFields() {
        show()
        edit("title", "Submit the research proposal")
        edit("description", "Updated description")
        edit("notes", "Use the revised methods.")
        edit("estimate", "90")
        edit("reminder", "30")
        click("priority")
        compose.onNodeWithText("High", substring = false).performClick()
        click("preference")
        compose.onNodeWithText("Evening", substring = false).performClick()
        edit("checklist-title-sources", "Review primary sources")
        click("checklist-completed-sources")
        save()
        compose.runOnIdle {
            assertEquals(fixture.copy(title = "Submit the research proposal", taskDescription = "Updated description",
                notes = "Use the revised methods.", priority = "high", estimatedMinutes = 90,
                reminderMinutes = 30, schedulingPreference = "evening", checklist = listOf(
                    fixture.checklist!![0], KairosChecklistItem("sources", "Review primary sources", true))), captured)
            assertEquals(0, dismissals)
        }
    }

    @Test fun checklistAddAndDeletePreserveExistingIdentityAndOtherFields() {
        show()
        click("checklist-delete-sources")
        click("checklist-add")
        val added = compose.onNode(SemanticsMatcher("new checklist title") {
            val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
            tag.startsWith("task-editor-checklist-title-") && tag != "task-editor-checklist-title-question"
        })
        added.performScrollTo().performTextReplacement("Write the conclusion")
        save()
        compose.runOnIdle {
            val result = requireNotNull(captured)
            val items = requireNotNull(result.checklist)
            assertEquals(2, items.size)
            assertEquals(fixture.checklist!![0], items[0])
            assertTrue(items[1].id.isNotBlank())
            assertNotEquals("question", items[1].id)
            assertNotEquals("sources", items[1].id)
            assertEquals("Write the conclusion", items[1].title)
            assertFalse(items[1].completed)
            assertEquals(fixture.copy(checklist = items), result)
        }
    }

    @Test fun savingDisablesRepeatedSubmissionAndDismissalWithoutLosingDraft() {
        show()
        edit("title", "Keep the submitted draft")
        compose.runOnIdle { saving = true }
        field("save").assertIsNotEnabled()
        field("cancel").assertIsNotEnabled()
        field("title").assertIsNotEnabled().assertTextContains("Keep the submitted draft")
        compose.runOnIdle { saving = false }
        save()
        compose.runOnIdle { assertEquals(fixture.copy(title = "Keep the submitted draft"), captured) }
    }

    @Test fun titleOnlyEditDoesNotMaterializeAbsentOptionalFields() {
        val original = KairosTask(id = "minimal", title = "Original", parentTaskID = "parent")
        show(original)
        edit("title", "Renamed")
        save()
        compose.runOnIdle { assertEquals(original.copy(title = "Renamed"), captured) }
    }

    @Test fun clearingOptionalTogglesSavesNullRatherThanDefaultValues() {
        show()
        listOf("estimated-toggle", "scheduled-time-toggle", "scheduled-date-toggle", "deadline-toggle",
            "due-time-toggle", "reminder-toggle").forEach(::click)
        save()
        compose.runOnIdle {
            assertEquals(fixture.copy(estimatedMinutes = null, date = null, startTime = null, endTime = null,
                dueDate = null, dueTime = null, reminderMinutes = null), captured)
        }
    }

    @Test fun dirtyCancelKeepsDraftUntilDiscardIsConfirmed() {
        show()
        edit("title", "Unsaved proposal")
        field("cancel").performClick()
        field("keep-editing").assertIsDisplayed().performClick()
        field("title").assertTextContains("Unsaved proposal")
        compose.runOnIdle { assertEquals(0, dismissals) }
        field("cancel").performClick()
        field("discard-confirm").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertNull(captured) }
    }

    @Test fun systemBackRequiresDiscardForDirtyDraft() {
        show()
        edit("title", "Unsaved from back")
        Espresso.pressBack()
        field("keep-editing").assertIsDisplayed().performClick()
        field("title").assertTextContains("Unsaved from back")
        Espresso.pressBack()
        field("discard-confirm").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertNull(captured) }
    }

    @Test fun failedSaveRetainsDraftAndAllowsRetry() {
        var attempts = 0
        show(onSave = { captured = it; attempts++; error = "Could not save. Check your connection and retry." })
        edit("notes", "Retain this after the network fails.")
        save()
        field("error").assertTextContains("Could not save.", substring = true)
        capture("task-editor-save-error")
        field("notes").performScrollTo().assertTextContains("Retain this after the network fails.")
        save()
        compose.runOnIdle { assertEquals(2, attempts); assertEquals("Retain this after the network fails.", captured?.notes) }
    }

    @Test fun restoredDraftSurvivesSavedInstanceStateWithoutMutatingOriginal() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        edit("title", "Restored proposal")
        edit("notes", "Draft after rotation")
        edit("checklist-title-sources", "Draft source item")
        restoration.emulateSavedInstanceStateRestore()
        field("title").performScrollTo().assertTextContains("Restored proposal")
        field("notes").performScrollTo().assertTextContains("Draft after rotation")
        save()
        compose.runOnIdle {
            assertEquals(fixture.copy(title = "Restored proposal", notes = "Draft after rotation",
                checklist = listOf(fixture.checklist!![0], fixture.checklist!![1].copy(title = "Draft source item"))), captured)
            assertEquals("Prepare the research proposal", fixture.title)
        }
    }

    @Test fun lockedScheduleCannotChangeUntilUnlocked() {
        show(fixture.copy(scheduleLocked = true))
        field("scheduled-date-toggle").performScrollTo().assertIsNotEnabled()
        field("scheduled-time-toggle").performScrollTo().assertIsNotEnabled()
        click("lock")
        field("scheduled-date-toggle").performScrollTo().assertIsEnabled()
        field("scheduled-time-toggle").assertIsEnabled()
    }

    @Test fun repeatingTaskDisablesScheduleAndChecklistCompletion() {
        show(fixture.copy(recurrence = KairosRecurrenceMarker(enabled = true)))
        field("checklist-completed-sources").performScrollTo().assertIsNotEnabled()
        field("scheduled-date-toggle").performScrollTo().assertIsNotEnabled()
        field("scheduled-time-toggle").performScrollTo().assertIsNotEnabled()
        capture("task-editor-recurring")
        edit("title", "Updated repeating task")
        save()
        compose.runOnIdle { assertEquals(fixture.copy(title = "Updated repeating task", recurrence = KairosRecurrenceMarker(true)), captured) }
    }

    @Test fun invalidMinutesAndOverlongNotesBlockSave() {
        show()
        edit("estimate", "1441")
        save()
        field("error").assertTextContains("1440", substring = true)
        compose.runOnIdle { assertNull(captured) }
        edit("estimate", "45")
        edit("reminder", "10081")
        save()
        field("error").assertTextContains("10080", substring = true)
        compose.runOnIdle { assertNull(captured) }
        edit("reminder", "15")
        edit("notes", "n".repeat(3001))
        save()
        field("error").assertTextContains("3000", substring = true)
        compose.runOnIdle { assertNull(captured) }
        capture("task-editor-notes-validation")
    }

    @Test fun phoneLightDarkAndNarrowLayoutsKeepHeaderAndLowerFieldsReachable() {
        show(fixture.copy(notes = "Research notes with a longer paragraph. ".repeat(18)))
        field("title").assertIsDisplayed()
        field("save").assertIsDisplayed()
        field("cancel").assertIsDisplayed()
        capture("task-editor-light-top")
        field("lock").performScrollTo().assertIsDisplayed()
        field("save").assertIsDisplayed()
        capture("task-editor-light-planning")
        compose.runOnIdle { appearance = "dark" }
        capture("task-editor-dark-planning")
        field("checklist-title-sources").performScrollTo().assertIsDisplayed()
        capture("task-editor-dark-checklist")
        field("title").performScrollTo()
        capture("task-editor-dark-top")
        compose.runOnIdle { narrow = true }
        field("notes").performScrollTo().performClick()
        field("save").assertIsDisplayed()
        field("cancel").assertIsDisplayed()
        capture("task-editor-narrow-long-notes")
        field("lock").performScrollTo().assertIsDisplayed()
        capture("task-editor-narrow-planning")
    }
}

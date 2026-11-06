package com.kairos.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.kairos.app.ui.navigation.KairosBottomNav
import com.kairos.app.ui.theme.KairosTheme
import org.junit.Rule
import org.junit.Test

/** Real Navigation Compose controller + production tab actions, without Firebase. */
class KairosNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController

    private fun show() {
        compose.setContent {
            KairosTheme {
                val nav = rememberNavController()
                SideEffect { controller = nav }
                Column(Modifier.fillMaxSize()) {
                    NavHost(nav, startDestination = "dashboard", modifier = Modifier.weight(1f)) {
                        composable("dashboard") { Text("Dashboard content", Modifier.testTag("screen-dashboard")) }
                        composable("settings") { Text("Settings content", Modifier.testTag("screen-settings")) }
                        composable("statistics") { Text("Statistics content", Modifier.testTag("screen-statistics")) }
                        composable("tasks") {
                            var edits by rememberSaveable { mutableStateOf(0) }
                            Column(Modifier.testTag("screen-tasks")) {
                                Text("Board edits: $edits")
                                Button(onClick = { edits++ }) { Text("Edit board draft") }
                            }
                        }
                        composable("ai_helper") { Text("AI content", Modifier.testTag("screen-ai")) }
                        composable("plan") { Text("Schedule content", Modifier.testTag("screen-plan")) }
                        composable("achievements") { Text("Goals content", Modifier.testTag("screen-goals")) }
                    }
                    KairosBottomNav(nav)
                }
            }
        }
    }

    private fun settings() {
        compose.runOnIdle { controller.navigate("settings") { launchSingleTop = true } }
        compose.onNodeWithTag("screen-settings").assertIsDisplayed()
    }

    @Test fun homeLeavesSettingsImmediately() {
        show()
        settings()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithTag("screen-dashboard").assertIsDisplayed()
        compose.onNodeWithTag("screen-settings").assertDoesNotExist()
    }

    @Test fun homeDoesNotRestoreSettingsAfterOtherTabs() {
        show()
        for (tab in listOf("Boards", "AI", "Schedule", "Goals")) {
            settings()
            compose.onNodeWithText(tab).performClick()
            compose.onNodeWithText("Home").performClick()
            compose.onNodeWithTag("screen-dashboard").assertIsDisplayed()
            compose.onNodeWithTag("screen-settings").assertDoesNotExist()
        }
    }

    @Test fun returningHomePreservesOtherTabDraftState() {
        show()
        compose.onNodeWithText("Boards").performClick()
        compose.onNodeWithText("Edit board draft").performClick()
        compose.onNodeWithText("Board edits: 1").assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithTag("screen-dashboard").assertIsDisplayed()
        settings()
        compose.onNodeWithText("AI").performClick()
        compose.onNodeWithText("Boards").performClick()
        compose.onNodeWithText("Board edits: 1").assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithTag("screen-dashboard").assertIsDisplayed()
    }
}
